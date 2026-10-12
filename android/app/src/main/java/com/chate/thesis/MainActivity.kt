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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    // Navigation & Main Containers (4 destinations)
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dashboardView: View
    private lateinit var enrollmentView: View
    private lateinit var settingsView: View
    private lateinit var eventLogView: View

    // 1. MONITOR TAB UI ELEMENTS (Styled like the reference image highlight)
    private lateinit var heroTitleText: TextView
    private lateinit var heroSubtitleText: TextView
    private lateinit var actionText: TextView
    private lateinit var compositeScoreText: TextView
    private lateinit var ocsvmScoreText: TextView
    private lateinit var mahalScoreText: TextView

    // 2x2 Grid Cards
    private lateinit var btnGridVerifier: View
    private lateinit var gridVerifierStatusText: TextView
    private lateinit var btnGridModels: View
    private lateinit var gridModelsStatusText: TextView
    private lateinit var btnGridSandbox: View
    private lateinit var btnGridPolicy: View
    private lateinit var gridPolicyStatusText: TextView

    // Telemetry strip
    private lateinit var telemetryScoreStat: TextView
    private lateinit var telemetryKeystrokeStat: TextView
    private lateinit var telemetryVectorStat: TextView

    // Sandbox
    private lateinit var testInput: EditText
    private lateinit var verifyToggle: SwitchMaterial
    private lateinit var clearTextButton: Button

    // Recent Activity timeline
    private lateinit var timelineEvent1Title: TextView
    private lateinit var timelineEvent1Details: TextView
    private lateinit var timelineEvent2Title: TextView
    private lateinit var timelineEvent2Details: TextView
    private lateinit var btnViewMoreLogs: Button

    // 2. ENROLLMENT TAB UI ELEMENTS
    private lateinit var stepDot1: TextView
    private lateinit var stepDot2: TextView
    private lateinit var stepDot3: TextView
    private lateinit var stepDot4: TextView
    private lateinit var stepLine1: View
    private lateinit var stepLine2: View
    private lateinit var stepLine3: View

    // Step 1
    private lateinit var enrollStep1Welcome: View
    private lateinit var btnStep1Next: Button

    // Step 2
    private lateinit var enrollStep2Setup: View
    private lateinit var step2KeyboardStatusLabel: TextView
    private lateinit var btnStep2Keyboard: Button
    private lateinit var step2AdminStatusLabel: TextView
    private lateinit var btnStep2Admin: Button
    private lateinit var btnStep2Back: Button
    private lateinit var btnStep2Next: Button

    // Step 3
    private lateinit var enrollStep3Typing: View
    private lateinit var guidedPromptText: TextView
    private lateinit var btnShufflePrompt: Button
    private lateinit var enrollmentInput: EditText
    private lateinit var btnEnrollClearInput: Button
    private lateinit var btnEnrollStartMode: Button
    private lateinit var enrollTelemetryPercent: TextView
    private lateinit var enrollTelemetryProgressBar: ProgressBar
    private lateinit var enrollKeystrokeCounter: TextView
    private lateinit var enrollVectorCounter: TextView
    private lateinit var enrollRhythmStatus: TextView
    private lateinit var btnStep3Back: Button

    // Step 4
    private lateinit var enrollStep4Complete: View
    private lateinit var btnLaunchMonitor: Button

    // 3. SETTINGS TAB UI ELEMENTS (All buttons relocated here)
    private lateinit var settingsKeyboardStatusLabel: TextView
    private lateinit var settingsAdminStatusLabel: TextView
    private lateinit var profileStatusText: TextView
    private lateinit var keystrokeCountText: TextView
    private lateinit var enrollmentPercentText: TextView
    private lateinit var enrollmentProgress: ProgressBar
    private lateinit var enableKeyboardButton: Button
    private lateinit var deviceAdminButton: Button
    private lateinit var enrollButton: Button
    private lateinit var resetButton: Button

    // 4. LOGS TAB UI ELEMENTS
    private lateinit var logCountText: TextView
    private lateinit var logSearchInput: EditText
    private lateinit var chipFilterAll: Chip
    private lateinit var chipFilterAllow: Chip
    private lateinit var chipFilterWarn: Chip
    private lateinit var chipFilterStepUp: Chip
    private lateinit var chipFilterBlock: Chip
    private lateinit var eventLogRecyclerView: RecyclerView
    private lateinit var eventLogAdapter: EventLogAdapter

    // Core Managers & Engines
    private lateinit var profileManager: ProfileManager
    private lateinit var securityLogger: SecurityLogger
    private var allLogs: List<LogEntry> = emptyList()
    private var currentFilter: String = "ALL"
    private var currentSearchQuery: String = ""

    // Prompts Collection
    private val prompts = listOf(
        "The quick brown fox jumps over the lazy dog.",
        "Pack my box with five dozen liquor jugs.",
        "Sphinx of black quartz, judge my vow.",
        "Continuous keystroke authentication verifies behavioral typing rhythm.",
        "How vexingly quick daft zebras jump over sleek bronze fountains.",
        "Zero-trust device protection detects session takeover instantly."
    )
    private var currentPromptIndex = 0

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
                    if (eventLogView.visibility == View.VISIBLE) {
                        loadEventLogs()
                    }
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
        // Apply Monet Dynamic Colors (wallpaper-derived Material You colors)
        DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileManager = ProfileManager(this, testingMode = true)
        securityLogger = SecurityLogger(this)

        initViews()
        setupNavigation()
        setupMonitorTab()
        setupOnboardingFlow()
        setupSettingsTab()
        setupLogsTab()

        refreshProfileDisplay()
        checkKeyboardState()
        checkDeviceAdminState()
        updateRecentActivityTimeline()
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
        updateRecentActivityTimeline()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(riskUpdateReceiver)
    }

    private fun initViews() {
        // Navigation & Tab Containers
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dashboardView = findViewById(R.id.dashboardView)
        enrollmentView = findViewById(R.id.enrollmentView)
        settingsView = findViewById(R.id.settingsView)
        eventLogView = findViewById(R.id.eventLogView)

        // Monitor Tab
        heroTitleText = findViewById(R.id.heroTitleText)
        heroSubtitleText = findViewById(R.id.heroSubtitleText)
        actionText = findViewById(R.id.actionText)
        compositeScoreText = findViewById(R.id.compositeScoreText)
        ocsvmScoreText = findViewById(R.id.ocsvmScoreText)
        mahalScoreText = findViewById(R.id.mahalScoreText)

        btnGridVerifier = findViewById(R.id.btnGridVerifier)
        gridVerifierStatusText = findViewById(R.id.gridVerifierStatusText)
        btnGridModels = findViewById(R.id.btnGridModels)
        gridModelsStatusText = findViewById(R.id.gridModelsStatusText)
        btnGridSandbox = findViewById(R.id.btnGridSandbox)
        btnGridPolicy = findViewById(R.id.btnGridPolicy)
        gridPolicyStatusText = findViewById(R.id.gridPolicyStatusText)

        telemetryScoreStat = findViewById(R.id.telemetryScoreStat)
        telemetryKeystrokeStat = findViewById(R.id.telemetryKeystrokeStat)
        telemetryVectorStat = findViewById(R.id.telemetryVectorStat)

        testInput = findViewById(R.id.testInput)
        verifyToggle = findViewById(R.id.verifyToggle)
        clearTextButton = findViewById(R.id.clearTextButton)

        timelineEvent1Title = findViewById(R.id.timelineEvent1Title)
        timelineEvent1Details = findViewById(R.id.timelineEvent1Details)
        timelineEvent2Title = findViewById(R.id.timelineEvent2Title)
        timelineEvent2Details = findViewById(R.id.timelineEvent2Details)
        btnViewMoreLogs = findViewById(R.id.btnViewMoreLogs)

        // Enrollment Stepper
        stepDot1 = findViewById(R.id.stepDot1)
        stepDot2 = findViewById(R.id.stepDot2)
        stepDot3 = findViewById(R.id.stepDot3)
        stepDot4 = findViewById(R.id.stepDot4)
        stepLine1 = findViewById(R.id.stepLine1)
        stepLine2 = findViewById(R.id.stepLine2)
        stepLine3 = findViewById(R.id.stepLine3)

        // Step 1
        enrollStep1Welcome = findViewById(R.id.enrollStep1Welcome)
        btnStep1Next = findViewById(R.id.btnStep1Next)

        // Step 2
        enrollStep2Setup = findViewById(R.id.enrollStep2Setup)
        step2KeyboardStatusLabel = findViewById(R.id.step2KeyboardStatusLabel)
        btnStep2Keyboard = findViewById(R.id.btnStep2Keyboard)
        step2AdminStatusLabel = findViewById(R.id.step2AdminStatusLabel)
        btnStep2Admin = findViewById(R.id.btnStep2Admin)
        btnStep2Back = findViewById(R.id.btnStep2Back)
        btnStep2Next = findViewById(R.id.btnStep2Next)

        // Step 3
        enrollStep3Typing = findViewById(R.id.enrollStep3Typing)
        guidedPromptText = findViewById(R.id.guidedPromptText)
        btnShufflePrompt = findViewById(R.id.btnShufflePrompt)
        enrollmentInput = findViewById(R.id.enrollmentInput)
        btnEnrollClearInput = findViewById(R.id.btnEnrollClearInput)
        btnEnrollStartMode = findViewById(R.id.btnEnrollStartMode)
        enrollTelemetryPercent = findViewById(R.id.enrollTelemetryPercent)
        enrollTelemetryProgressBar = findViewById(R.id.enrollTelemetryProgressBar)
        enrollKeystrokeCounter = findViewById(R.id.enrollKeystrokeCounter)
        enrollVectorCounter = findViewById(R.id.enrollVectorCounter)
        enrollRhythmStatus = findViewById(R.id.enrollRhythmStatus)
        btnStep3Back = findViewById(R.id.btnStep3Back)

        // Step 4
        enrollStep4Complete = findViewById(R.id.enrollStep4Complete)
        btnLaunchMonitor = findViewById(R.id.btnLaunchMonitor)

        // Settings Tab
        settingsKeyboardStatusLabel = findViewById(R.id.settingsKeyboardStatusLabel)
        settingsAdminStatusLabel = findViewById(R.id.settingsAdminStatusLabel)
        profileStatusText = findViewById(R.id.profileStatusText)
        keystrokeCountText = findViewById(R.id.keystrokeCountText)
        enrollmentPercentText = findViewById(R.id.enrollmentPercentText)
        enrollmentProgress = findViewById(R.id.enrollmentProgress)
        enableKeyboardButton = findViewById(R.id.enableKeyboardButton)
        deviceAdminButton = findViewById(R.id.deviceAdminButton)
        enrollButton = findViewById(R.id.enrollButton)
        resetButton = findViewById(R.id.resetButton)

        // Logs Tab
        logCountText = findViewById(R.id.logCountText)
        logSearchInput = findViewById(R.id.logSearchInput)
        chipFilterAll = findViewById(R.id.chipFilterAll)
        chipFilterAllow = findViewById(R.id.chipFilterAllow)
        chipFilterWarn = findViewById(R.id.chipFilterWarn)
        chipFilterStepUp = findViewById(R.id.chipFilterStepUp)
        chipFilterBlock = findViewById(R.id.chipFilterBlock)
        eventLogRecyclerView = findViewById(R.id.eventLogRecyclerView)
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    showTab(dashboardView)
                    updateRecentActivityTimeline()
                    true
                }
                R.id.nav_enrollment -> {
                    showTab(enrollmentView)
                    syncEnrollmentStepWithState()
                    true
                }
                R.id.nav_settings -> {
                    showTab(settingsView)
                    refreshProfileDisplay()
                    checkKeyboardState()
                    checkDeviceAdminState()
                    true
                }
                R.id.nav_event_log -> {
                    showTab(eventLogView)
                    loadEventLogs()
                    true
                }
                else -> false
            }
        }
    }

    private fun showTab(activeView: View) {
        dashboardView.visibility = if (activeView == dashboardView) View.VISIBLE else View.GONE
        enrollmentView.visibility = if (activeView == enrollmentView) View.VISIBLE else View.GONE
        settingsView.visibility = if (activeView == settingsView) View.VISIBLE else View.GONE
        eventLogView.visibility = if (activeView == eventLogView) View.VISIBLE else View.GONE
    }

    private fun setupMonitorTab() {
        clearTextButton.setOnClickListener {
            testInput.text.clear()
        }

        verifyToggle.setOnCheckedChangeListener { _, isChecked ->
            val profile = profileManager.getProfile(DEFAULT_USER_ID)
            if (profile != null && profile.isReady) {
                val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
                    putExtra("mode", if (isChecked) "VERIFY" else "IDLE")
                    putExtra("user_id", DEFAULT_USER_ID)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                val msg = if (isChecked) "Continuous verification active" else "Verification standby"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                gridVerifierStatusText.text = if (isChecked) "Active" else "Standby"
                if (isChecked) {
                    testInput.requestFocus()
                }
            } else if (isChecked) {
                verifyToggle.isChecked = false
                Toast.makeText(this, "Profile not ready. Complete enrollment first.", Toast.LENGTH_SHORT).show()
            }
        }

        // 2x2 Grid card interactions
        btnGridVerifier.setOnClickListener {
            verifyToggle.isChecked = !verifyToggle.isChecked
        }

        btnGridSandbox.setOnClickListener {
            testInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT)
        }

        btnGridModels.setOnClickListener {
            val profile = profileManager.getProfile(DEFAULT_USER_ID)
            val info = if (profile != null && profile.isReady) "OCSVM + Mahalanobis models active" else "Models awaiting calibration"
            Toast.makeText(this, info, Toast.LENGTH_SHORT).show()
        }

        btnGridPolicy.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_settings
        }

        btnViewMoreLogs.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_event_log
        }
    }

    private fun setupSettingsTab() {
        enableKeyboardButton.setOnClickListener {
            handleKeyboardSetupClick()
        }

        deviceAdminButton.setOnClickListener {
            handleAdminSetupClick()
        }

        enrollButton.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_enrollment
        }

        resetButton.setOnClickListener {
            profileManager.deleteUser(DEFAULT_USER_ID)
            val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
                putExtra("mode", "IDLE")
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            verifyToggle.isChecked = false
            gridVerifierStatusText.text = "Standby"
            refreshProfileDisplay()
            resetScoreDisplay()
            setEnrollmentStep(1)
            Toast.makeText(this, "Biometric profile deleted & reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOnboardingFlow() {
        btnStep1Next.setOnClickListener {
            setEnrollmentStep(2)
        }

        btnStep2Back.setOnClickListener {
            setEnrollmentStep(1)
        }
        btnStep2Next.setOnClickListener {
            setEnrollmentStep(3)
            startEnrollmentSession()
        }
        btnStep2Keyboard.setOnClickListener {
            handleKeyboardSetupClick()
        }
        btnStep2Admin.setOnClickListener {
            handleAdminSetupClick()
        }

        guidedPromptText.text = prompts[currentPromptIndex]
        btnShufflePrompt.setOnClickListener {
            currentPromptIndex = (currentPromptIndex + 1) % prompts.size
            guidedPromptText.text = prompts[currentPromptIndex]
        }
        btnEnrollClearInput.setOnClickListener {
            enrollmentInput.text.clear()
        }
        btnEnrollStartMode.setOnClickListener {
            startEnrollmentSession()
            enrollmentInput.requestFocus()
        }
        btnStep3Back.setOnClickListener {
            setEnrollmentStep(2)
        }

        btnLaunchMonitor.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_dashboard
            verifyToggle.isChecked = true
        }
    }

    private fun setEnrollmentStep(step: Int) {
        enrollStep1Welcome.visibility = if (step == 1) View.VISIBLE else View.GONE
        enrollStep2Setup.visibility = if (step == 2) View.VISIBLE else View.GONE
        enrollStep3Typing.visibility = if (step == 3) View.VISIBLE else View.GONE
        enrollStep4Complete.visibility = if (step == 4) View.VISIBLE else View.GONE
        updateStepperDots(step)
    }

    private fun updateStepperDots(step: Int) {
        val colorActive = ContextCompat.getColor(this, R.color.md_theme_primary)
        val colorInactive = ContextCompat.getColor(this, R.color.surface_container_high)
        val colorDone = ContextCompat.getColor(this, R.color.risk_allow)
        val textOnPrimary = ContextCompat.getColor(this, R.color.md_theme_onPrimary)
        val textOnVariant = ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant)

        if (step > 1) {
            stepDot1.setBackgroundResource(R.drawable.stepper_pill_done)
            stepDot1.setTextColor(textOnPrimary)
            stepDot1.text = "✓"
        } else {
            stepDot1.setBackgroundResource(R.drawable.stepper_pill_active)
            stepDot1.setTextColor(textOnPrimary)
            stepDot1.text = "1"
        }
        stepLine1.setBackgroundColor(if (step > 1) colorDone else colorInactive)

        if (step > 2) {
            stepDot2.setBackgroundResource(R.drawable.stepper_pill_done)
            stepDot2.setTextColor(textOnPrimary)
            stepDot2.text = "✓"
        } else if (step == 2) {
            stepDot2.setBackgroundResource(R.drawable.stepper_pill_active)
            stepDot2.setTextColor(textOnPrimary)
            stepDot2.text = "2"
        } else {
            stepDot2.setBackgroundResource(R.drawable.stepper_pill_inactive)
            stepDot2.setTextColor(textOnVariant)
            stepDot2.text = "2"
        }
        stepLine2.setBackgroundColor(if (step > 2) colorDone else colorInactive)

        if (step > 3) {
            stepDot3.setBackgroundResource(R.drawable.stepper_pill_done)
            stepDot3.setTextColor(textOnPrimary)
            stepDot3.text = "✓"
        } else if (step == 3) {
            stepDot3.setBackgroundResource(R.drawable.stepper_pill_active)
            stepDot3.setTextColor(textOnPrimary)
            stepDot3.text = "3"
        } else {
            stepDot3.setBackgroundResource(R.drawable.stepper_pill_inactive)
            stepDot3.setTextColor(textOnVariant)
            stepDot3.text = "3"
        }
        stepLine3.setBackgroundColor(if (step > 3) colorDone else colorInactive)

        if (step == 4) {
            stepDot4.setBackgroundResource(R.drawable.stepper_pill_done)
            stepDot4.setTextColor(textOnPrimary)
            stepDot4.text = "★"
        } else {
            stepDot4.setBackgroundResource(R.drawable.stepper_pill_inactive)
            stepDot4.setTextColor(textOnVariant)
            stepDot4.text = "4"
        }
    }

    private fun syncEnrollmentStepWithState() {
        val profile = profileManager.getProfile(DEFAULT_USER_ID)
        if (profile != null && profile.isReady) {
            setEnrollmentStep(4)
        } else if (profile != null && profile.keystrokeCount > 0) {
            setEnrollmentStep(3)
            updateEnrollmentDisplay(profile.keystrokeCount, profile.progressPct, profile.status)
        } else {
            setEnrollmentStep(1)
        }
        checkKeyboardState()
        checkDeviceAdminState()
    }

    private fun startEnrollmentSession() {
        profileManager.getOrCreateProfile(DEFAULT_USER_ID)
        val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
            putExtra("mode", "ENROLL")
            putExtra("user_id", DEFAULT_USER_ID)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        enrollRhythmStatus.text = "IME listening in ENROLL mode • Type naturally"
        enrollmentInput.requestFocus()
    }

    private fun checkDeviceAdminState() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, ThesisDeviceAdminReceiver::class.java)
        val isActive = dpm.isAdminActive(adminComponent)

        if (isActive) {
            deviceAdminButton.text = getString(R.string.btn_admin_active)
            deviceAdminButton.isEnabled = false
            btnStep2Admin.text = getString(R.string.btn_admin_active)
            btnStep2Admin.isEnabled = false

            settingsAdminStatusLabel.text = "Active & Enforced"
            settingsAdminStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_allow))
            step2AdminStatusLabel.text = "Active & Enforced"
            step2AdminStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_allow))

            gridPolicyStatusText.text = "Enforced"
        } else {
            deviceAdminButton.text = getString(R.string.btn_activate_admin)
            deviceAdminButton.isEnabled = true
            btnStep2Admin.text = getString(R.string.btn_activate_admin)
            btnStep2Admin.isEnabled = true

            settingsAdminStatusLabel.text = "Optional / Recommended"
            settingsAdminStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant))
            step2AdminStatusLabel.text = "Optional / Recommended"
            step2AdminStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant))

            gridPolicyStatusText.text = "Standby"
        }
    }

    private fun handleAdminSetupClick() {
        val adminComponent = ComponentName(this, ThesisDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to automatically lock the device upon continuous anomaly detection.")
        }
        startActivity(intent)
    }

    private fun checkKeyboardState() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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
            enableKeyboardButton.text = getString(R.string.btn_enable_keyboard)
            enableKeyboardButton.isEnabled = true
            btnStep2Keyboard.text = getString(R.string.btn_enable_keyboard)
            btnStep2Keyboard.isEnabled = true

            settingsKeyboardStatusLabel.text = "Not Enabled in System"
            settingsKeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
            step2KeyboardStatusLabel.text = "Not Enabled in System"
            step2KeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
        } else if (!isSelected) {
            enableKeyboardButton.text = getString(R.string.btn_select_keyboard)
            enableKeyboardButton.isEnabled = true
            btnStep2Keyboard.text = getString(R.string.btn_select_keyboard)
            btnStep2Keyboard.isEnabled = true

            settingsKeyboardStatusLabel.text = "Enabled, but Not Active Keyboard"
            settingsKeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_warn))
            step2KeyboardStatusLabel.text = "Enabled, but Not Active Keyboard"
            step2KeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_warn))
        } else {
            enableKeyboardButton.text = getString(R.string.btn_keyboard_active)
            enableKeyboardButton.isEnabled = false
            btnStep2Keyboard.text = getString(R.string.btn_keyboard_active)
            btnStep2Keyboard.isEnabled = false

            settingsKeyboardStatusLabel.text = "Active & Ready"
            settingsKeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_allow))
            step2KeyboardStatusLabel.text = "Active & Ready"
            step2KeyboardStatusLabel.setTextColor(ContextCompat.getColor(this, R.color.risk_allow))
        }
    }

    private fun handleKeyboardSetupClick() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        var isEnabled = false
        val ourPackage = packageName
        for (method in enabledMethods) {
            if (method.packageName == ourPackage) {
                isEnabled = true
                break
            }
        }

        if (!isEnabled) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } else {
            imm.showInputMethodPicker()
        }
    }

    private fun setupLogsTab() {
        eventLogAdapter = EventLogAdapter(emptyList())
        eventLogRecyclerView.layoutManager = LinearLayoutManager(this)
        eventLogRecyclerView.adapter = eventLogAdapter

        chipFilterAll.setOnClickListener { setLogFilter("ALL") }
        chipFilterAllow.setOnClickListener { setLogFilter("ALLOW") }
        chipFilterWarn.setOnClickListener { setLogFilter("WARN") }
        chipFilterStepUp.setOnClickListener { setLogFilter("STEP_UP_AUTH") }
        chipFilterBlock.setOnClickListener { setLogFilter("BLOCK_SESSION") }

        logSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyLogFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setLogFilter(filter: String) {
        currentFilter = filter
        chipFilterAll.isChecked = filter == "ALL"
        chipFilterAllow.isChecked = filter == "ALLOW"
        chipFilterWarn.isChecked = filter == "WARN"
        chipFilterStepUp.isChecked = filter == "STEP_UP_AUTH"
        chipFilterBlock.isChecked = filter == "BLOCK_SESSION"
        applyLogFilters()
    }

    private fun loadEventLogs() {
        allLogs = securityLogger.readAll(DEFAULT_USER_ID).reversed()
        applyLogFilters()
    }

    private fun applyLogFilters() {
        var filtered = allLogs
        if (currentFilter != "ALL") {
            filtered = filtered.filter { it.action == currentFilter }
        }
        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                (it.action?.contains(currentSearchQuery, ignoreCase = true) == true) ||
                (it.eventType.contains(currentSearchQuery, ignoreCase = true)) ||
                (it.payload.contains(currentSearchQuery, ignoreCase = true))
            }
        }
        eventLogAdapter.updateLogs(filtered)
        logCountText.text = "${filtered.size} of ${allLogs.size} events"
    }

    private fun refreshProfileDisplay() {
        val profile = profileManager.getProfile(DEFAULT_USER_ID)
        if (profile != null) {
            profileStatusText.text = profile.status
            keystrokeCountText.text = "${profile.keystrokeCount}"
            val progress = profile.progressPct.toInt().coerceIn(0, 100)
            enrollmentProgress.progress = progress
            enrollmentPercentText.text = "${progress}%"

            enrollTelemetryProgressBar.progress = progress
            enrollTelemetryPercent.text = "${progress}%"
            enrollKeystrokeCounter.text = "Keystrokes: ${profile.keystrokeCount} / ${profile.minKeystrokes}"
            enrollVectorCounter.text = "Vectors: ${profile.vectors.size} / 5"

            telemetryKeystrokeStat.text = "⌨ ${profile.keystrokeCount} Keys"
            telemetryVectorStat.text = "⚡ ${profile.vectors.size} Windows"

            when (profile.status) {
                "PENDING" -> {
                    heroTitleText.text = "Calibrating"
                    enrollButton.text = getString(R.string.btn_start_enrollment)
                    enrollButton.isEnabled = true
                    verifyToggle.isEnabled = false
                    gridVerifierStatusText.text = "Standby"
                    gridModelsStatusText.text = "Calibrating"
                }
                "READY", "ACTIVE" -> {
                    heroTitleText.text = "Active"
                    enrollButton.text = "Profile Calibrated"
                    enrollButton.isEnabled = true
                    verifyToggle.isEnabled = true
                    gridModelsStatusText.text = "Fitted"
                }
            }
        } else {
            heroTitleText.text = "Standby"
            profileStatusText.text = getString(R.string.status_not_enrolled)
            keystrokeCountText.text = "0"
            enrollmentProgress.progress = 0
            enrollmentPercentText.text = "0%"
            enrollTelemetryProgressBar.progress = 0
            enrollTelemetryPercent.text = "0%"
            enrollKeystrokeCounter.text = "Keystrokes: 0 / 250"
            enrollVectorCounter.text = "Vectors: 0 / 5"
            telemetryKeystrokeStat.text = "⌨ 0 Keys"
            telemetryVectorStat.text = "⚡ 0 Windows"
            verifyToggle.isEnabled = false
            gridVerifierStatusText.text = "Standby"
            gridModelsStatusText.text = "Not Trained"
        }
    }

    private fun updateRiskDisplay(composite: Double, ocsvm: Double, mahal: Double, action: String, keystrokeCount: Int) {
        compositeScoreText.text = String.format("%.3f", composite)
        ocsvmScoreText.text = String.format("%.3f", ocsvm)
        mahalScoreText.text = String.format("%.3f", mahal)
        actionText.text = action
        telemetryScoreStat.text = String.format("★ Risk: %.3f", composite)

        val (textColor, bgColor) = when (action) {
            "ALLOW" -> Pair(R.color.risk_allow, R.color.risk_allow_bg)
            "WARN" -> Pair(R.color.risk_warn, R.color.risk_warn_bg)
            "STEP_UP_AUTH" -> Pair(R.color.risk_step_up, R.color.risk_step_up_bg)
            "BLOCK_SESSION" -> Pair(R.color.risk_block, R.color.risk_block_bg)
            else -> Pair(R.color.text_secondary, R.color.surface_container_high)
        }
        actionText.setTextColor(ContextCompat.getColor(this, textColor))
        val bgDrawable = actionText.background
        if (bgDrawable is GradientDrawable) {
            bgDrawable.setColor(ContextCompat.getColor(this, bgColor))
        }

        keystrokeCountText.text = "$keystrokeCount"
        telemetryKeystrokeStat.text = "⌨ $keystrokeCount Keys"
        updateRecentActivityTimeline()
    }

    private fun updateEnrollmentDisplay(keystrokeCount: Int, progress: Double, status: String) {
        keystrokeCountText.text = "$keystrokeCount"
        val progressInt = progress.toInt().coerceIn(0, 100)
        enrollmentProgress.progress = progressInt
        enrollmentPercentText.text = "${progressInt}%"
        profileStatusText.text = status

        enrollTelemetryProgressBar.progress = progressInt
        enrollTelemetryPercent.text = "${progressInt}%"
        val profile = profileManager.getProfile(DEFAULT_USER_ID)
        val minKs = profile?.minKeystrokes ?: 250
        val vectorCount = profile?.vectors?.size ?: 0
        enrollKeystrokeCounter.text = "Keystrokes: $keystrokeCount / $minKs"
        enrollVectorCounter.text = "Vectors: $vectorCount / 5"
        enrollRhythmStatus.text = "Capturing rhythm: $keystrokeCount keystrokes collected"

        telemetryKeystrokeStat.text = "⌨ $keystrokeCount Keys"
        telemetryVectorStat.text = "⚡ $vectorCount Windows"

        if (status == "READY" || status == "ACTIVE") {
            heroTitleText.text = "Active"
            setEnrollmentStep(4)
            verifyToggle.isEnabled = true
            gridModelsStatusText.text = "Fitted"
            Toast.makeText(this, "🎉 Biometric Profile Calibrated!", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateRecentActivityTimeline() {
        val logs = securityLogger.readAll(DEFAULT_USER_ID).reversed()
        if (logs.isNotEmpty()) {
            val latest = logs[0]
            timelineEvent1Title.text = "${latest.action ?: "LOG"}: ${latest.eventType}"
            timelineEvent1Details.text = "Score: ${latest.riskScore?.let { String.format("%.3f", it) } ?: "N/A"} · ${latest.timestamp.take(16)}"
        } else {
            timelineEvent1Title.text = "Zero-trust engine active"
            timelineEvent1Details.text = "Awaiting continuous typing events..."
        }

        if (logs.size > 1) {
            val second = logs[1]
            timelineEvent2Title.text = "${second.action ?: "LOG"}: ${second.eventType}"
            timelineEvent2Details.text = "Score: ${second.riskScore?.let { String.format("%.3f", it) } ?: "N/A"} · ${second.timestamp.take(16)}"
        } else {
            timelineEvent2Title.text = "Biometric models on standby"
            timelineEvent2Details.text = "OCSVM & Mahalanobis distance ready"
        }
    }

    private fun resetScoreDisplay() {
        compositeScoreText.text = "0.000"
        ocsvmScoreText.text = "0.000"
        mahalScoreText.text = "0.000"
        actionText.text = "ALLOW"
        actionText.setTextColor(ContextCompat.getColor(this, R.color.risk_allow))
        telemetryScoreStat.text = "★ Risk: 0.000"
    }
}