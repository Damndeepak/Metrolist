package com.metrolist.music.deeplist

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.*
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class DeeplistCloudDataTest {
    @Test fun `logical backup restores real schema without copying credentials or download flags`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val source=Room.inMemoryDatabaseBuilder(context,InternalDatabase::class.java).allowMainThreadQueries().build()
        val target=Room.inMemoryDatabaseBuilder(context,InternalDatabase::class.java).allowMainThreadQueries().build()
        try {
            val a=MusicDatabase(source); val b=MusicDatabase(target)
            a.insert(SongEntity("song", "Cloud song", liked=true, isDownloaded=true, libraryAddToken="secret"))
            a.insert(SongEntity("local-file", "Local recording", isLocal=true))
            a.insert(ArtistEntity("artist", "Artist"));a.insert(SongArtistMap("song","artist",0))
            a.insert(AlbumEntity(id="album",title="Album",songCount=1,duration=60));a.insert(SongAlbumMap("song","album",0));a.insert(AlbumArtistMap("album","artist",0))
            a.insert(PlaylistEntity(id="list",name="My playlist",isLocal=true));a.insert(PlaylistSongMap(playlistId="list",songId="song"))
            a.insert(Event(songId="song",timestamp=LocalDateTime.of(2026,10,31,12,0),playTime=60000))
            val prefs=mutablePreferencesOf(InnerTubeCookieKey to "SAPISID=secret",DeeplistTokenKey to "integration-secret",VerciStyleKey to "APPLE",DeeplistSavedLyricsKey to "[]")
            val snapshot=DeeplistCloudData.snapshot(a,prefs)
            assertFalse(snapshot.toString().contains("secret"));assertFalse(snapshot.toString().contains("Local recording"));assertFalse(snapshot.toString().contains("isDownloaded"))
            b.withTransaction { DeeplistCloudData.apply(this,JSONObject(),snapshot) }
            b.withTransaction { DeeplistCloudData.apply(this,snapshot,snapshot) }
            assertEquals(1L,target.openHelper.readableDatabase.query("SELECT COUNT(*) FROM event").use { it.moveToFirst();it.getLong(0) })
            target.openHelper.readableDatabase.query("SELECT liked,isDownloaded,totalPlayTime FROM song WHERE id='song'").use { assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0));assertEquals(0,it.getInt(1));assertEquals(60000L,it.getLong(2)) }
            assertEquals(1,b.playlistEntitiesByNameAsc().size)
            assertEquals("APPLE",DeeplistCloudData.settings(snapshot)[VerciStyleKey.name])
            val deleted=JSONObject(snapshot.toString());deleted.getJSONObject("records").keys().asSequence().filter { it.startsWith("playlist:")||it.startsWith("event:") }.toList().forEach { deleted.getJSONObject("records").remove(it) }
            b.withTransaction { DeeplistCloudData.apply(this,snapshot,deleted) }
            assertEquals(0,b.playlistEntitiesByNameAsc().size)
            assertEquals(0L,target.openHelper.readableDatabase.query("SELECT COUNT(*) FROM event").use { it.moveToFirst();it.getLong(0) })
        } finally { source.close();target.close() }
    }
    @Test fun `remote preference removal preserves device credentials`() {
        val prefs=mutablePreferencesOf(InnerTubeCookieKey to "private-cookie",DarkModeKey to "ON",VerciStyleKey to "SHIP")
        DeeplistCloudData.applySettings(prefs,JSONObject("""{"settings":{"verciStyle":"APPLE"}}"""))
        assertEquals("private-cookie",prefs[InnerTubeCookieKey]);assertNull(prefs[DarkModeKey]);assertEquals("APPLE",prefs[VerciStyleKey])
    }
    @Test fun `unknown credentials are rejected`() {
        assertThrows(Exception::class.java) { DeeplistCloudData.settings(JSONObject("""{"settings":{"innerTubeCookie":"secret"}}""")) }
    }
}
