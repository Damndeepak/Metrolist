// ScrobbleSendWorker.kt

package com.mochyy.mochymusic.scrobble

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mochyy.mochymusic.di.ScrobblerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Sends the scrobbles that couldn't go out at the time. WorkManager runs it once there's a network,
 * even with the app closed, and tries again later (each time waiting longer) while a service is down.
 */
class ScrobbleSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scrobbler = EntryPointAccessors.fromApplication(applicationContext, ScrobblerEntryPoint::class.java).scrobbler()
        return if (scrobbler.send()) Result.success() else Result.retry()
    }
}
