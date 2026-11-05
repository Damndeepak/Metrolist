package com.metrolist.music.discord

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DiscordRpcRecoveryTest {
    private val originals = mutableMapOf<String, Any?>()

    private fun set(name: String, value: Any?) {
        val field = DiscordRpcManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        if (!originals.containsKey(name)) originals[name] = field.get(DiscordRpcManager)
        field.set(DiscordRpcManager, value)
    }

    @After
    fun restore() {
        originals.forEach { (name, value) ->
            val field = DiscordRpcManager::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(DiscordRpcManager, value)
        }
    }

    private fun sentActivity(sentAt: Long) {
        set("_ready", true)
        set("currentSongId", "song")
        set("currentIsPlaying", true)
        set("currentActivityHadImages", false)
        set("lastActivity", DiscordPresence.buildActivity(name = "Deeplist", type = ActivityType.Listening))
        set("lastActivitySentAtMs", sentAt)
    }

    @Test
    fun freshSendIsDeduplicatedButFailedOrExpiredSendIsRetried() {
        sentActivity(System.currentTimeMillis())
        assertTrue(DiscordRpcManager.isShowingSong("song", true))
        assertFalse(DiscordRpcManager.isShowingSong("another-song", true))
        assertFalse(DiscordRpcManager.isShowingSong("song", false))
        set("lastActivitySentAtMs", 0L)
        assertFalse(DiscordRpcManager.isShowingSong("song", true))
        set("lastActivitySentAtMs", System.currentTimeMillis() - 61_000L)
        assertFalse(DiscordRpcManager.isShowingSong("song", true))
    }

    @Test
    fun disconnectedGatewayCannotBeTreatedAsShowingTheSong() {
        sentActivity(System.currentTimeMillis())
        set("_ready", false)
        assertFalse(DiscordRpcManager.isShowingSong("song", true))
    }
    @Test
    fun failedGatewaySendDoesNotMarkNewSongAsSent() = kotlinx.coroutines.runBlocking {
        sentActivity(System.currentTimeMillis())
        val activity = DiscordActivityBuilder.build(
            song = com.metrolist.music.db.entities.Song(
                com.metrolist.music.db.entities.SongEntity("new-song", "New song"), emptyList(),
            ),
            artistName = "Artist", albumName = null, artistThumbnail = null,
            songTitle = "New song", startTimestamp = 0L, endTimestamp = null,
            advancedMode = true, activityType = DiscordActivity.TYPE_PLAYING,
            activityName = "YouTube Music", btn1Label = "Old", btn1Url = "https://music.youtube.com",
            btn2Label = "Visit Metrolist", btn2Url = "https://github.com/MetrolistGroup/Metrolist",
        )
        org.junit.Assert.assertEquals("Deeplist by damndeepak", activity.name)
        org.junit.Assert.assertEquals(DiscordActivity.TYPE_LISTENING, activity.activityType)
        org.junit.Assert.assertEquals("Listen live", activity.button1Label)
        org.junit.Assert.assertEquals("https://world.damndeepak.workers.dev/deeplist.html", activity.button1Url)
        org.junit.Assert.assertEquals("Get Deeplist", activity.button2Label)
        org.junit.Assert.assertEquals("https://world.damndeepak.workers.dev/#download", activity.button2Url)
        set("gateway", DiscordGateway("test", { "" }, this))
        set("currentActivityHadImages", false)
        set("lastActivitySentAtMs", 0L)
        DiscordRpcManager.setActivity(activity, songId = "new-song", isPlaying = true)
        assertFalse(DiscordRpcManager.isShowingSong("new-song", true))
    }

}
