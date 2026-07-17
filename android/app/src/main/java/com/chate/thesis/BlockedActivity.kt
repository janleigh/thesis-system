package com.chate.thesis

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class BlockedActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        profileManager = ProfileManager(this, testingMode = true)

        findViewById<Button>(R.id.unblockButton).setOnClickListener {
            // Reset profile
            profileManager.deleteUser(MainActivity.DEFAULT_USER_ID)
            
            // Broadcast mode update to IME
            val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
                putExtra("mode", "IDLE")
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            Toast.makeText(this, "Profile reset. Try enrolling again.", Toast.LENGTH_SHORT).show()
            
            // Go back to main activity
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(mainIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Disable back button on blocked screen to enforce lockout
    }
}
