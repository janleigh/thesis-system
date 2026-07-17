package com.chate.thesis

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    // UI elements
    private lateinit var statusBadge: TextView
    private lateinit var profileStatusText: TextView
    private lateinit var keystrokeCountText: TextView
    private lateinit var enrollmentProgress: ProgressBar
    private lateinit var enrollmentPercentText: TextView
    private lateinit var compositeScoreText: TextView
    private lateinit var ocsvmScoreText: TextView
    private lateinit var mahalScoreText: TextView
    private lateinit var actionText: TextView
    private lateinit var actionCard: LinearLayout
    private lateinit var testInput: EditText
    private lateinit var enableKeyboardButton: Button
    private lateinit var deviceAdminButton: Button
    private lateinit var enrollButton: Button
    private lateinit var resetButton: Button

    private lateinit var profileManager: ProfileManager

    companion object {
        const val ACTION_RISK_UPDATE = "com.chate.thesis.RISK_UPDATE"
        const val ACTION_ENROLLMENT_UPDATE = "com.chate.thesis.ENROLLMENT_UPDATE"
        const val DEFAULT_USER_ID = "default_user"
    }

    private val riskUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_RISK_UPDATE -> {
                    val composite = intent.getDoubleExtra("composite_score", 0.0)
                    val ocsvm = intent.getDoubleExtra("ocsvm_score", 0.0)
                    val mahal = intent.getDoubleExtra("mahal_score", 0.0)
                    val action = intent.getStringExtra("action") ?: "ALLOW"
                    val keystrokeCount = intent.getIntExtra("keystroke_count", 0)
                    updateRiskDisplay(composite, ocsvm, mahal, action, keystrokeCount)
                }
                ACTION_ENROLLMENT_UPDATE -> {
                    val keystrokeCount = intent.getIntExtra("keystroke_count", 0)
                    val progress = intent.getDoubleExtra("progress_pct", 0.0)
                    val status = intent.getStringExtra("status") ?: "PENDING"
                    updateEnrollmentDisplay(keystrokeCount, progress, status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileManager = ProfileManager(this, testingMode = true)

        initViews()
        setupButtons()
        refreshProfileDisplay()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(ACTION_RISK_UPDATE)
            addAction(ACTION_ENROLLMENT_UPDATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(riskUpdateReceiver, filter)
        refreshProfileDisplay()
        checkKeyboardState()
        checkDeviceAdminState()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(riskUpdateReceiver)
    }

    private fun checkDeviceAdminState() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, ThesisDeviceAdminReceiver::class.java)
        val isActive = dpm.isAdminActive(adminComponent)

        if (isActive) {
            deviceAdminButton.text = "Device Admin Active"
            deviceAdminButton.isEnabled = false
        } else {
            deviceAdminButton.text = "Activate Device Admin"
            deviceAdminButton.isEnabled = true
            deviceAdminButton.setOnClickListener {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to automatically lock the device upon security threat detection.")
                }
                startActivity(intent)
            }
        }
    }

    private fun checkKeyboardState() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        var isEnabled = false
        val ourPackage = packageName
        for (method in enabledMethods) {
            if (method.packageName == ourPackage) {
                isEnabled = true
                break
            }
        }

        val currentInputMethodId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val isSelected = currentInputMethodId != null && currentInputMethodId.startsWith(ourPackage)

        if (!isEnabled) {
            enableKeyboardButton.text = "Enable Keyboard"
            enableKeyboardButton.isEnabled = true
            enableKeyboardButton.setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        } else if (!isSelected) {
            enableKeyboardButton.text = "Select Keyboard"
            enableKeyboardButton.isEnabled = true
            enableKeyboardButton.setOnClickListener {
                imm.showInputMethodPicker()
            }
        } else {
            enableKeyboardButton.text = "Keyboard Active"
            enableKeyboardButton.isEnabled = false
        }
    }

    private fun initViews() {
        statusBadge = findViewById(R.id.statusBadge)
        profileStatusText = findViewById(R.id.profileStatusText)
        keystrokeCountText = findViewById(R.id.keystrokeCountText)
        enrollmentProgress = findViewById(R.id.enrollmentProgress)
        enrollmentPercentText = findViewById(R.id.enrollmentPercentText)
        compositeScoreText = findViewById(R.id.compositeScoreText)
        ocsvmScoreText = findViewById(R.id.ocsvmScoreText)
        mahalScoreText = findViewById(R.id.mahalScoreText)
        actionText = findViewById(R.id.actionText)
        actionCard = findViewById(R.id.actionCard)
        testInput = findViewById(R.id.testInput)
        enableKeyboardButton = findViewById(R.id.enableKeyboardButton)
        deviceAdminButton = findViewById(R.id.deviceAdminButton)
        enrollButton = findViewById(R.id.enrollButton)
        resetButton = findViewById(R.id.resetButton)
    }

    private fun setupButtons() {
        checkKeyboardState()
        checkDeviceAdminState()

        enrollButton.setOnClickListener {
            val profile = profileManager.getOrCreateProfile(DEFAULT_USER_ID)
            if (profile.status == "PENDING") {
                // Signal the IME to start enrollment mode
                val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE)
                intent.putExtra("mode", "ENROLL")
                intent.putExtra("user_id", DEFAULT_USER_ID)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Toast.makeText(this, "Enrollment started — type naturally!", Toast.LENGTH_LONG).show()
                enrollButton.text = "Enrolling..."
                enrollButton.isEnabled = false
                testInput.requestFocus()
            } else if (profile.status == "READY" || profile.status == "ACTIVE") {
                // Switch to verification mode
                val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE)
                intent.putExtra("mode", "VERIFY")
                intent.putExtra("user_id", DEFAULT_USER_ID)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Toast.makeText(this, "Verification mode active", Toast.LENGTH_SHORT).show()
                testInput.requestFocus()
            }
        }

        resetButton.setOnClickListener {
            profileManager.deleteUser(DEFAULT_USER_ID)
            val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE)
            intent.putExtra("mode", "IDLE")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            refreshProfileDisplay()
            resetScoreDisplay()
            enrollButton.text = "Start Enrollment"
            enrollButton.isEnabled = true
            Toast.makeText(this, "Profile reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshProfileDisplay() {
        val profile = profileManager.getProfile(DEFAULT_USER_ID)
        if (profile != null) {
            profileStatusText.text = profile.status
            keystrokeCountText.text = "${profile.keystrokeCount}"
            val progress = profile.progressPct.toInt().coerceIn(0, 100)
            enrollmentProgress.progress = progress
            enrollmentPercentText.text = "${progress}%"

            // Update status badge
            when (profile.status) {
                "PENDING" -> {
                    statusBadge.text = "P"
                    setStatusBadgeColor(R.color.risk_warn)
                    enrollButton.text = "Start Enrollment"
                    enrollButton.isEnabled = true
                }
                "READY" -> {
                    statusBadge.text = "R"
                    setStatusBadgeColor(R.color.risk_allow)
                    enrollButton.text = "Start Verification"
                    enrollButton.isEnabled = true
                }
                "ACTIVE" -> {
                    statusBadge.text = "A"
                    setStatusBadgeColor(R.color.accent_blue)
                    enrollButton.text = "Verifying..."
                    enrollButton.isEnabled = false
                }
            }
        } else {
            profileStatusText.text = "Not Enrolled"
            keystrokeCountText.text = "0"
            enrollmentProgress.progress = 0
            enrollmentPercentText.text = "0%"
            statusBadge.text = "—"
            setStatusBadgeColor(R.color.text_secondary)
        }
    }

    private fun updateRiskDisplay(composite: Double, ocsvm: Double, mahal: Double, action: String, keystrokeCount: Int) {
        compositeScoreText.text = String.format("%.3f", composite)
        ocsvmScoreText.text = String.format("%.3f", ocsvm)
        mahalScoreText.text = String.format("%.3f", mahal)
        actionText.text = action

        // Color-code the action
        val (textColor, bgColor) = when (action) {
            "ALLOW" -> Pair(R.color.risk_allow, R.color.risk_allow_bg)
            "WARN" -> Pair(R.color.risk_warn, R.color.risk_warn_bg)
            "STEP_UP_AUTH" -> Pair(R.color.risk_step_up, R.color.risk_step_up_bg)
            "BLOCK_SESSION" -> Pair(R.color.risk_block, R.color.risk_block_bg)
            else -> Pair(R.color.text_secondary, R.color.card_bg)
        }
        actionText.setTextColor(ContextCompat.getColor(this, textColor))

        // Update keystroke count display
        keystrokeCountText.text = "$keystrokeCount"
    }

    private fun updateEnrollmentDisplay(keystrokeCount: Int, progress: Double, status: String) {
        keystrokeCountText.text = "$keystrokeCount"
        val progressInt = progress.toInt().coerceIn(0, 100)
        enrollmentProgress.progress = progressInt
        enrollmentPercentText.text = "${progressInt}%"
        profileStatusText.text = status

        if (status == "READY") {
            statusBadge.text = "R"
            setStatusBadgeColor(R.color.risk_allow)
            enrollButton.text = "Start Verification"
            enrollButton.isEnabled = true
            Toast.makeText(this, "✅ Enrollment complete! Profile is READY.", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetScoreDisplay() {
        compositeScoreText.text = "0.000"
        ocsvmScoreText.text = "0.000"
        mahalScoreText.text = "0.000"
        actionText.text = "—"
        actionText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun setStatusBadgeColor(colorRes: Int) {
        val bg = statusBadge.background
        if (bg is GradientDrawable) {
            bg.setColor(ContextCompat.getColor(this, colorRes))
        }
    }
}