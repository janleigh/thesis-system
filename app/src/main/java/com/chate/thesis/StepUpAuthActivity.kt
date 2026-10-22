package com.chate.thesis

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class StepUpAuthActivity : Activity() {
    companion object {
        const val REQUEST_CODE_CONFIRM_CREDENTIALS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isKeyguardSecure) {
            Toast.makeText(this, "Secure lock screen not set up. Step-up auth bypassed.", Toast.LENGTH_SHORT).show()
            notifyImeAndFinish(true)
            return
        }

        val intent =
            keyguardManager.createConfirmDeviceCredentialIntent(
                "Step-Up Authentication",
                "Suspicious typing behavior detected. Please verify your identity.",
            )

        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE_CONFIRM_CREDENTIALS)
        } else {
            notifyImeAndFinish(true)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONFIRM_CREDENTIALS) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Identity verified.", Toast.LENGTH_SHORT).show()
                notifyImeAndFinish(true)
            } else {
                Toast.makeText(this, "Verification failed or canceled. Blocking session.", Toast.LENGTH_LONG).show()
                notifyImeAndFinish(false)
            }
        }
    }

    private fun notifyImeAndFinish(success: Boolean) {
        val intent =
            Intent(KeystrokeIMEService.ACTION_STEP_UP_RESULT).apply {
                putExtra("success", success)
            }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        finish()
    }
}
