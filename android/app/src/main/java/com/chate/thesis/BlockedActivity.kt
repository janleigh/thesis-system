package com.chate.thesis

import android.content.Context
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
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                Toast.makeText(this, "Secure lock screen not set up. Cannot unblock.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            val authIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Unblock Session",
                "Enter your PIN/Password to unblock the application."
            )
            
            if (authIntent != null) {
                startActivityForResult(authIntent, 1001)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                // Authentication succeeded, proceed with unblocking
                profileManager.deleteUser(MainActivity.DEFAULT_USER_ID)
                
                val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
                    putExtra("mode", "IDLE")
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

                Toast.makeText(this, "Profile reset. Try enrolling again.", Toast.LENGTH_SHORT).show()
                
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(mainIntent)
                finish()
            } else {
                Toast.makeText(this, "Authentication failed. Session remains blocked.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        // Disable back button on blocked screen to enforce lockout
    }
}
