package com.metrolist.music.deeplist

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.metrolist.music.BuildConfig
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DeeplistAccountGateStartupTest {
    @Test fun `startup gate renders before the app database composition provider exists`() {
        assumeTrue(BuildConfig.APPLICATION_ID == "com.deepu.wordstorm")
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity=controller.get()
        val room=Room.inMemoryDatabaseBuilder(activity,InternalDatabase::class.java).allowMainThreadQueries().build()
        var rendered=false
        try {
            activity.setContent {
                // The production gate wraps MetrolistApp, so it cannot read its descendant's locals.
                DeeplistAccountGate(connection=null,database=MusicDatabase(room)) {}
                rendered=true
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Account gate must compose without LocalDatabase",rendered)
        } finally { controller.pause().stop().destroy();room.close() }
    }
}
