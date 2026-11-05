// DiscordLoginActivity.kt

package com.mochyy.mochymusic.discord

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.di.DiscordEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Where Discord's login page sends the browser back to. It passes the answer on and brings the
 * app to the front again, so the user lands back on the Discord page instead of in the browser.
 */
class DiscordLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.data?.let { redirect ->
            EntryPointAccessors.fromApplication(applicationContext, DiscordEntryPoint::class.java)
                .discordAuth()
                .handleRedirect(redirect)
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
