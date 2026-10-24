package com.metrolist.music.utils

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicPreferenceTest {
    @Test fun `switching lyric style rebinds both the size reader and writer`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apple = floatPreferencesKey("test-apple-size")
        val fish = floatPreferencesKey("test-fish-size")
        context.dataStore.edit { it[apple] = 32f; it[fish] = 120f }
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val selected = mutableStateOf(apple)
        var size: MutableState<Float>? = null
        suspend fun waitFor(value: Float) {
            repeat(200) {
                Snapshot.sendApplyNotifications()
                clock.sendFrame(System.nanoTime())
                delay(5)
                if (size?.value == value) return
            }
            assertEquals(value, size?.value)
        }
        try {
            composition.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalLifecycleOwner provides owner) {
                    size = rememberPreference(selected.value, 52f)
                }
            }
            waitFor(32f)
            selected.value = fish
            waitFor(120f)
            size!!.value = 180f
            waitFor(180f)
            val saved = context.dataStore.data.first()
            assertEquals(32f, saved[apple])
            assertEquals(180f, saved[fish])
        } finally {
            composition.dispose()
            recomposer.close()
            runner.join()
        }
    }
}
