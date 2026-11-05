package com.mochyy.mochymusic.db

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class LegacyDeeplistImportTest {
    private lateinit var context: Application
    private lateinit var room: InternalDatabase
    private lateinit var target: SupportSQLiteDatabase

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("song.db")
        room = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        target = room.openHelper.writableDatabase
    }

    @After fun tearDown() { room.close() }

    @Test fun `real v38 library imports into generated v42 schema once without changing source`() {
        legacy { db ->
            seed(db, "song", "id" to "song1", "title" to "A song", "isDownloaded" to 1, "isCached" to 1)
            seed(db, "playlist", "id" to "list1", "name" to "My list")
            seed(db, "playlist_song_map", "id" to 1, "playlistId" to "list1", "songId" to "song1", "position" to 0)
            seed(db, "event", "id" to 1, "songId" to "song1", "timestamp" to 1000, "playTime" to 30000)
            seed(db, "lyrics", "id" to "song1", "lyrics" to "[00:01]Hello")
        }
        val result = LegacyDeeplistImport.importIfFresh(context, target)
        assertTrue(result is LegacyDeeplistImport.Result.Imported)
        assertEquals(1, count("song"))
        assertEquals(1, count("playlist_song_map"))
        assertEquals(1, count("event"))
        target.query("SELECT title, isDownloaded, localUri, addedByLike FROM song").use {
            assertTrue(it.moveToFirst())
            assertEquals("A song", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertTrue(it.isNull(2))
            assertEquals(0, it.getInt(3))
        }
        target.query("SELECT imported_rows FROM deeplist_legacy_import WHERE id=1").use {
            assertTrue(it.moveToFirst())
            assertEquals((result as LegacyDeeplistImport.Result.Imported).rows, it.getInt(0))
        }
        // No external marker is needed: the receipt survives even if the process exits at commit.
        assertEquals(LegacyDeeplistImport.Result.AlreadyImported, LegacyDeeplistImport.importIfFresh(context, target))
        SQLiteDatabase.openDatabase(context.getDatabasePath("song.db").path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(38, it.version)
            it.rawQuery("SELECT isDownloaded FROM song", null).use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }
        }
    }

    @Test fun `unresolved source relationship rolls back entire target and keeps original`() {
        legacy { db ->
            seed(db, "song", "id" to "song1", "title" to "Keep me")
            seed(db, "playlist_song_map", "id" to 1, "playlistId" to "missing", "songId" to "song1", "position" to 0)
        }
        assertTrue(runCatching { LegacyDeeplistImport.importIfFresh(context, target) }.isFailure)
        assertEquals(0, count("song"))
        assertEquals(0, count("playlist_song_map"))
        target.query("SELECT 1 FROM sqlite_master WHERE name='deeplist_legacy_import'").use { assertFalse(it.moveToFirst()) }
        assertTrue(context.getDatabasePath("song.db").isFile)
    }

    @Test fun `import runs from actual Room onOpen before first library query`() {
        legacy { db -> seed(db, "song", "id" to "first", "title" to "On open") }
        Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    LegacyDeeplistImport.requireImported(context, db)
                }
            }).build().useRoom { opened ->
                opened.openHelper.writableDatabase.query("SELECT title FROM song").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("On open", it.getString(0))
                }
            }
        assertTrue(LegacyDeeplistImport.state.value.completed)
    }

    @Test fun `existing destination is never merged overwritten or duplicated`() {
        legacy { db -> seed(db, "song", "id" to "legacy", "title" to "Legacy") }
        target.execSQL("INSERT INTO search_history (id, query) VALUES (1, 'new library')")
        assertEquals(LegacyDeeplistImport.Result.DestinationInUse, LegacyDeeplistImport.importIfFresh(context, target))
        assertEquals(0, count("song"))
        assertEquals(1, count("search_history"))
    }

    @Test fun `transaction receipt survives database reopen without any external marker`() {
        legacy { db -> seed(db, "song", "id" to "persisted", "title" to "Committed") }
        val name = "migration-reopen.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, InternalDatabase::class.java, name).allowMainThreadQueries().build()
        open().useRoom { database ->
            assertTrue(LegacyDeeplistImport.importIfFresh(context, database.openHelper.writableDatabase) is LegacyDeeplistImport.Result.Imported)
        }
        open().useRoom { database ->
            val db = database.openHelper.writableDatabase
            assertEquals(LegacyDeeplistImport.Result.AlreadyImported, LegacyDeeplistImport.importIfFresh(context, db))
            db.query("SELECT COUNT(*) FROM song").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        }
        context.deleteDatabase(name)
    }

    @Test fun `unknown legacy version fails without touching either library`() {
        legacy { db -> seed(db, "song", "id" to "old", "title" to "Unknown version"); db.version = 37 }
        assertTrue(runCatching { LegacyDeeplistImport.importIfFresh(context, target) }.isFailure)
        assertEquals(0, count("song"))
        SQLiteDatabase.openDatabase(context.getDatabasePath("song.db").path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(37, it.version)
        }
    }

    private fun count(table: String): Int = target.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun legacy(block: (SQLiteDatabase) -> Unit) {
        context.openOrCreateDatabase("song.db", 0, null).use { db ->
            val fixture = requireNotNull(javaClass.classLoader!!.getResourceAsStream("deeplist-v38.sql")).bufferedReader().use { it.readText() }
            fixture.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
            db.version = 38
            block(db)
        }
    }

    private fun seed(db: SQLiteDatabase, table: String, vararg fields: Pair<String, Any>) {
        val values = ContentValues()
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) != 0 && c.isNull(4)) {
                    if (c.getString(2).contains("TEXT")) values.put(c.getString(1), "") else values.put(c.getString(1), 0)
                }
            }
        }
        fields.forEach { (key, value) -> when (value) {
            is String -> values.put(key, value)
            is Int -> values.put(key, value)
            else -> error("unsupported fixture value")
        } }
        db.insertOrThrow(table, null, values)
    }
}

private inline fun <T : androidx.room.RoomDatabase, R> T.useRoom(block: (T) -> R): R =
    try { block(this) } finally { close() }
