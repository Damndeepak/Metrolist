package com.metrolist.music.deeplist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.MainActivity
import kotlinx.coroutines.launch

class DeeplistAccountActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeeplistAccount.initialize(this)
        lifecycleScope.launch {
            intent.data?.let { DeeplistAccount.claim(it) }
            startActivity(Intent(this@DeeplistAccountActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }
}
