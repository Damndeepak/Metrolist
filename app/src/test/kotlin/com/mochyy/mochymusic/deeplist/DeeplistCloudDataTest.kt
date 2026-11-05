package com.mochyy.mochymusic.deeplist

import android.app.Application
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DeeplistCloudDataTest {
    @Test fun `legacy metadata survives Mochy library round trip without applying old settings`() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("mochy.db")
        val db = MusicDatabase.newInstance(context)
        try {
            db.openHelper.writableDatabase.execSQL("INSERT INTO artist(id,name,lastUpdateTime) VALUES('UCtest','Artist',0)")
            db.openHelper.writableDatabase.execSQL("INSERT INTO song(id,title,duration,liked,totalPlayTime) VALUES('abcdefghijk','Song',180,1,12000)")
            val initial = DeeplistCloudData.snapshot(db)
            val records = initial.getJSONObject("records")
            val artistKey = records.keys().asSequence().first { it.startsWith("artist:") }
            val songKey = records.keys().asSequence().first { it.startsWith("song:") }
            records.getJSONObject(artistKey).put("isPodcastChannel", 1)
            records.getJSONObject(songKey).put("isEpisode", 1).put("playbackPosition", 27000)
            initial.getJSONObject("settings").put("verciStyle", "APPLE").put("deeplistSavedLyrics", "[]")
            val snapshot = DeeplistCloudData.snapshot(db, initial)
            assertEquals(1, snapshot.getJSONObject("records").getJSONObject(artistKey).getInt("isPodcastChannel"))
            assertEquals(27000, snapshot.getJSONObject("records").getJSONObject(songKey).getInt("playbackPosition"))
            assertEquals("APPLE", snapshot.getJSONObject("settings").getString("verciStyle"))
            val remote = JSONObject(snapshot.toString())
            remote.getJSONObject("records").getJSONObject(songKey).put("title", "Updated song")
            DeeplistCloudData.apply(db, snapshot, remote)
            db.openHelper.readableDatabase.query("SELECT title,liked,totalPlayTime FROM song WHERE id='abcdefghijk'").use {
                assertTrue(it.moveToFirst()); assertEquals("Updated song", it.getString(0)); assertEquals(1, it.getInt(1)); assertEquals(12000L, it.getLong(2))
            }
            val final = DeeplistCloudData.snapshot(db, remote)
            assertEquals(1, final.getJSONObject("records").getJSONObject(songKey).getInt("isEpisode"))
            assertEquals("APPLE", final.getJSONObject("settings").getString("verciStyle"))
        } finally {
            db.close(); context.deleteDatabase("mochy.db")
        }
    }
}
