package com.metrolist.music.discord

import com.metrolist.music.db.entities.Song
import timber.log.Timber

object DiscordActivityBuilder {
    private const val TAG = "DiscordSvc"

    fun build(
        song: Song,
        artistName: String,
        albumName: String?,
        artistThumbnail: String?,
        songTitle: String,
        startTimestamp: Long,
        endTimestamp: Long?,
        advancedMode: Boolean,
        activityType: Int = DiscordActivity.TYPE_LISTENING,
        activityName: String? = null,
        stateTemplate: String = DiscordDefaults.STATE_TEMPLATE,
        detailsTemplate: String = DiscordDefaults.DETAILS_TEMPLATE,
        btn1Enabled: Boolean = true,
        btn1Label: String = DiscordDefaults.BUTTON1_LABEL,
        btn1Url: String = DiscordDefaults.BUTTON1_URL_TEMPLATE,
        btn2Enabled: Boolean = true,
        btn2Label: String = DiscordDefaults.BUTTON2_LABEL,
        btn2Url: String = DiscordDefaults.BUTTON2_URL,
    ): DiscordActivity {
        // Public branding and destinations must not inherit old saved templates.
        val state = if (advancedMode) DiscordTemplateRenderer.render(
            stateTemplate.ifEmpty { DiscordDefaults.STATE_TEMPLATE },
            songTitle, artistName, albumName, song.song.id,
        ) else artistName
        val details = if (advancedMode) DiscordTemplateRenderer.render(
            detailsTemplate.ifEmpty { DiscordDefaults.DETAILS_TEMPLATE },
            songTitle, artistName, albumName, song.song.id,
        ) else songTitle

        val result = DiscordActivity(
            activityType = DiscordActivity.TYPE_LISTENING,
            name = DiscordDefaults.ACTIVITY_NAME,
            state = state,
            details = details,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            largeImage = song.song.thumbnailUrl,
            largeText = albumName,
            smallImage = artistThumbnail,
            smallText = artistName,
            button1Label = DiscordDefaults.BUTTON1_LABEL,
            button1Url = DiscordDefaults.BUTTON1_URL_TEMPLATE,
            button2Label = DiscordDefaults.BUTTON2_LABEL,
            button2Url = DiscordDefaults.BUTTON2_URL,
        )

        Timber.tag(TAG).d(
            "build: result — name=%s, type=%d, state=%s, details=%s, btn1=%s, btn2=%s",
            result.name, result.activityType, result.state, result.details,
            result.button1Label?.take(30), result.button2Label?.take(30),
        )

        return result
    }
}
