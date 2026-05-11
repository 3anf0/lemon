package com.smartcal.app.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.smartcal.app.MainActivity

/**
 * Transparent trampoline activity — launched from widget or tile.
 * Opens MainActivity directly on the Marek (Voice) screen.
 */
class QuickLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("start_destination", "voice")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }
}
