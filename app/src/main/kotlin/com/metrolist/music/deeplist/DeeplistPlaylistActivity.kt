package com.metrolist.music.deeplist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.metrolist.music.LocalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.ui.menu.DeeplistPlaylistImportDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DeeplistPlaylistActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.data?.toString().orEmpty()
        val remote = intent.data?.getQueryParameter("list")
        if (remote != null && remote.matches(Regex("[A-Za-z0-9_-]{1,150}"))) {
            startActivity(android.content.Intent(this, com.metrolist.music.MainActivity::class.java).setData(android.net.Uri.parse("https://music.youtube.com/playlist?list=$remote")))
            finish(); return
        }
        setContent { MaterialTheme { CompositionLocalProvider(LocalDatabase provides database) {
            DeeplistPlaylistImportDialog(initial = link, onDismiss = { finish() })
        } } }
    }
}
