package com.chate.thesis

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
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
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.slider.Slider
import android.content.res.Configuration
import android.widget.CheckBox
import com.chate.thesis.ui.HeaderSurfaceView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var themePreferences: ThemePreferences
    private lateinit var headerEffectView: HeaderSurfaceView

    // Navigation & Main Containers (4 destinations)
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dashboardView: View
    private lateinit var enrollmentView: View
    private lateinit var settingsView: View
    private lateinit var eventLogView: View

    // 1. HOME TAB UI ELEMENTS
    private lateinit var heroTitleText: TextView
    private lateinit var heroSubtitleText: TextView
    private lateinit var actionText: TextView
    private lateinit var compositeScoreText: TextView
    private lateinit var ocsvmScoreText: TextView
    private lateinit var mahalScoreText: TextView

    // Security Control Cards (Live Verifier & Enforcer Policy)
    private lateinit var btnGridVerifier: View
    private lateinit var gridVerifierStatusText: TextView
    private lateinit var masterSwitch: SwitchMaterial
    private lateinit var btnGridPolicy: View
    private lateinit var gridPolicyStatusText: TextView

    // Sandbox
    private lateinit var testInput: EditText
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

    // Step 3: MONKEYTYPE WORD TYPING ARENA
    private lateinit var enrollStep3Typing: View
    private lateinit var monkeytypeWordCountText: TextView
    private lateinit var btnMonkeytypeRestart: Button
    private lateinit var monkeytypeWordsDisplay: TextView
    private lateinit var monkeytypeInput: EditText
    private lateinit var btnMonkeytypeClear: Button
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

    // Keyboard Customization Fields
    private lateinit var settingsKeyboardHeightLabel: TextView
    private lateinit var sliderKeyboardHeight: Slider
    private lateinit var btnSettingsHeightCompact: MaterialButton
    private lateinit var btnSettingsHeightShort: MaterialButton
    private lateinit var btnSettingsHeightNormal: MaterialButton
    private lateinit var btnSettingsHeightTall: MaterialButton
    private lateinit var btnSettingsHeightXtall: MaterialButton
    private lateinit var rgLongPressMode: RadioGroup
    private lateinit var rbModeVariants: RadioButton
    private lateinit var rbModeRapidFire: RadioButton
    private lateinit var settingsDelayLabel: TextView
    private lateinit var sliderLongPressDelay: Slider
    private lateinit var switchKeyHints: SwitchMaterial
    private lateinit var keyboardPreferences: KeyboardPreferences

    // Enhanced Theming & IME Settings UI
    private lateinit var themeModeToggleGroup: MaterialButtonToggleGroup
    private lateinit var btnThemeAuto: MaterialButton
    private lateinit var btnThemeLight: MaterialButton
    private lateinit var btnThemeDark: MaterialButton
    private lateinit var switchAmoledBlack: SwitchMaterial
    private lateinit var paletteChipGroup: ChipGroup
    private lateinit var effectChipGroup: ChipGroup
    private lateinit var switchPromptKeyboardSwitch: SwitchMaterial
    private lateinit var btnSwitchKeyboardManual: MaterialButton

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

    // MONKEYTYPE ENGINE STATE
    private val monkeytypeDictionary = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
        "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
        "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
        "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could",
        "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "over", "think", "also", "back", "after", "use", "two", "how",
        "our", "work", "first", "well", "way", "even", "new", "want", "because",
        "any", "these", "give", "day", "most", "us", "system", "secure", "device",
        "keystroke", "rhythm", "cadence", "behavior", "typing", "biometric", "protect",
        "speed", "focus", "finger", "hand", "input", "sensor", "model", "pattern"
    )
    private val targetWords = mutableListOf<String>()
    private var currentWordIndex = 0
    private var currentTypedWord = ""
    private val wordResults = mutableListOf<Boolean>()

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
        themePreferences = ThemePreferences(this)
        if (themePreferences.palette == ColorPalette.MONET) {
            DynamicColors.applyToActivityIfAvailable(this)
        } else {
            setTheme(themePreferences.palette.getThemeResId())
        }
        if (themePreferences.isAmoledBlackEnabled) {
            val isDark = when (themePreferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            if (isDark) {
                theme.applyStyle(R.style.Theme_Thesis_Amoled, true)
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileManager = ProfileManager(this, testingMode = true)
        securityLogger = SecurityLogger(this)
        keyboardPreferences = KeyboardPreferences(this)

        initViews()
        setupNavigation(savedInstanceState)
        setupHomeTab()
        setupOnboardingFlow()
        setupMonkeytypeEngine()
        setupSettingsTab()
        setupLogsTab()

        heroSubtitleText.text = "commit: ${BuildConfig.GIT_COMMIT_HASH}"

        refreshProfileDisplay()
        checkKeyboardState()
        checkDeviceAdminState()
        updateRecentActivityTimeline()
    }

    override fun onResume() {
        super.onResume()
        if (::headerEffectView.isInitialized) {
            headerEffectView.resumeAnimation()
        }
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
        if (::headerEffectView.isInitialized) {
            headerEffectView.pauseAnimation()
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(riskUpdateReceiver)
    }

    private fun initViews() {
        // Navigation & Tab Containers
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dashboardView = findViewById(R.id.dashboardView)
        enrollmentView = findViewById(R.id.enrollmentView)
        settingsView = findViewById(R.id.settingsView)
        eventLogView = findViewById(R.id.eventLogView)

        // Header Effect Surface
        headerEffectView = findViewById(R.id.headerEffectView)
        headerEffectView.setEffect(themePreferences.headerEffect)

        // Home Tab
        heroTitleText = findViewById(R.id.heroTitleText)
        heroSubtitleText = findViewById(R.id.heroSubtitleText)
        actionText = findViewById(R.id.actionText)
        compositeScoreText = findViewById(R.id.compositeScoreText)
        ocsvmScoreText = findViewById(R.id.ocsvmScoreText)
        mahalScoreText = findViewById(R.id.mahalScoreText)

        btnGridVerifier = findViewById(R.id.btnGridVerifier)
        gridVerifierStatusText = findViewById(R.id.gridVerifierStatusText)
        masterSwitch = findViewById(R.id.masterSwitch)
        btnGridPolicy = findViewById(R.id.btnGridPolicy)
        gridPolicyStatusText = findViewById(R.id.gridPolicyStatusText)

        testInput = findViewById(R.id.testInput)
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

        // Step 3 Monkeytype
        enrollStep3Typing = findViewById(R.id.enrollStep3Typing)
        monkeytypeWordCountText = findViewById(R.id.monkeytypeWordCountText)
        btnMonkeytypeRestart = findViewById(R.id.btnMonkeytypeRestart)
        monkeytypeWordsDisplay = findViewById(R.id.monkeytypeWordsDisplay)
        monkeytypeInput = findViewById(R.id.monkeytypeInput)
        btnMonkeytypeClear = findViewById(R.id.btnMonkeytypeClear)
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

        // Keyboard Customization
        settingsKeyboardHeightLabel = findViewById(R.id.settingsKeyboardHeightLabel)
        sliderKeyboardHeight = findViewById(R.id.sliderKeyboardHeight)
        btnSettingsHeightCompact = findViewById(R.id.btnSettingsHeightCompact)
        btnSettingsHeightShort = findViewById(R.id.btnSettingsHeightShort)
        btnSettingsHeightNormal = findViewById(R.id.btnSettingsHeightNormal)
        btnSettingsHeightTall = findViewById(R.id.btnSettingsHeightTall)
        btnSettingsHeightXtall = findViewById(R.id.btnSettingsHeightXtall)
        rgLongPressMode = findViewById(R.id.rgLongPressMode)
        rbModeVariants = findViewById(R.id.rbModeVariants)
        rbModeRapidFire = findViewById(R.id.rbModeRapidFire)
        settingsDelayLabel = findViewById(R.id.settingsDelayLabel)
        sliderLongPressDelay = findViewById(R.id.sliderLongPressDelay)
        switchKeyHints = findViewById(R.id.switchKeyHints)

        themeModeToggleGroup = findViewById(R.id.themeModeToggleGroup)
        btnThemeAuto = findViewById(R.id.btnThemeAuto)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        switchAmoledBlack = findViewById(R.id.switchAmoledBlack)
        paletteChipGroup = findViewById(R.id.paletteChipGroup)
        effectChipGroup = findViewById(R.id.effectChipGroup)
        switchPromptKeyboardSwitch = findViewById(R.id.switchPromptKeyboardSwitch)
        btnSwitchKeyboardManual = findViewById(R.id.btnSwitchKeyboardManual)

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("saved_tab_id", bottomNavigation.selectedItemId)
    }

    private fun setupNavigation(savedInstanceState: Bundle?) {
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
        val targetTabId = savedInstanceState?.getInt("saved_tab_id", R.id.nav_dashboard) ?: R.id.nav_dashboard
        bottomNavigation.selectedItemId = targetTabId
    }

    private fun showTab(activeView: View) {
        dashboardView.visibility = if (activeView == dashboardView) View.VISIBLE else View.GONE
        enrollmentView.visibility = if (activeView == enrollmentView) View.VISIBLE else View.GONE
        settingsView.visibility = if (activeView == settingsView) View.VISIBLE else View.GONE
        eventLogView.visibility = if (activeView == eventLogView) View.VISIBLE else View.GONE
        if (::headerEffectView.isInitialized) {
            if (activeView == dashboardView) {
                headerEffectView.resumeAnimation()
            } else {
                headerEffectView.pauseAnimation()
            }
        }
    }

    private fun setupHomeTab() {
        val showKeyboardForSandbox = {
            testInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT)
        }

        testInput.setOnClickListener { showKeyboardForSandbox() }
        findViewById<View>(R.id.sandboxContainerCard)?.setOnClickListener { showKeyboardForSandbox() }

        clearTextButton.setOnClickListener {
            testInput.text.clear()
        }

        masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            val profile = profileManager.getProfile(DEFAULT_USER_ID)
            if (profile != null && profile.isReady) {
                val intent = Intent(KeystrokeIMEService.ACTION_SET_MODE).apply {
                    putExtra("mode", if (isChecked) "VERIFY" else "IDLE")
                    putExtra("user_id", DEFAULT_USER_ID)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                val msg = if (isChecked) "Continuous verification active" else "Verification standby"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                gridVerifierStatusText.text = if (isChecked) "Active · System Protected" else "Standby · System Inactive"
                heroTitleText.text = if (isChecked) "Status: Active" else "Status: Standby"
                actionText.text = if (isChecked) "ALLOW" else "STANDBY"
                val (textColor, bgColor) = if (isChecked) {
                    Pair(R.color.risk_allow, R.color.risk_allow_bg)
                } else {
                    Pair(R.color.text_secondary, R.color.surface_container_high)
                }
                actionText.setTextColor(ContextCompat.getColor(this, textColor))
                val bgDrawable = actionText.background
                if (bgDrawable is GradientDrawable) {
                    bgDrawable.setColor(ContextCompat.getColor(this, bgColor))
                }
                if (isChecked) {
                    showKeyboardForSandbox()
                } else {
                    promptKeyboardSwitchIfNeeded()
                }
            } else if (isChecked) {
                masterSwitch.isChecked = false
                Toast.makeText(this, "Profile not ready. Complete enrollment first.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGridVerifier.setOnClickListener {
            if (masterSwitch.isEnabled) {
                masterSwitch.isChecked = !masterSwitch.isChecked
            } else {
                Toast.makeText(this, "Profile not ready. Complete enrollment first.", Toast.LENGTH_SHORT).show()
            }
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

            masterSwitch.isChecked = false
            gridVerifierStatusText.text = "Standby · System Inactive"
            refreshProfileDisplay()
            resetScoreDisplay()
            setEnrollmentStep(1)
            resetMonkeytypeWords()
            Toast.makeText(this, "Biometric profile deleted & reset", Toast.LENGTH_SHORT).show()
        }

        // Theme Mode Toggle Group
        when (themePreferences.themeMode) {
            ThemeMode.SYSTEM -> themeModeToggleGroup.check(R.id.btnThemeAuto)
            ThemeMode.LIGHT -> themeModeToggleGroup.check(R.id.btnThemeLight)
            ThemeMode.DARK -> themeModeToggleGroup.check(R.id.btnThemeDark)
        }
        themeModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btnThemeLight -> ThemeMode.LIGHT
                    R.id.btnThemeDark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
                if (themePreferences.themeMode != newMode) {
                    themePreferences.themeMode = newMode
                    themePreferences.applyNightMode()
                }
            }
        }

        // AMOLED True Black Switch
        switchAmoledBlack.isChecked = themePreferences.isAmoledBlackEnabled
        switchAmoledBlack.setOnCheckedChangeListener { _, isChecked ->
            if (themePreferences.isAmoledBlackEnabled != isChecked) {
                themePreferences.isAmoledBlackEnabled = isChecked
                recreate()
            }
        }

        // Palette ChipGroup
        when (themePreferences.palette) {
            ColorPalette.MONET -> paletteChipGroup.check(R.id.chipPaletteMonet)
            ColorPalette.CYAN -> paletteChipGroup.check(R.id.chipPaletteCyan)
            ColorPalette.BLUE -> paletteChipGroup.check(R.id.chipPaletteBlue)
            ColorPalette.EMERALD -> paletteChipGroup.check(R.id.chipPaletteEmerald)
            ColorPalette.AMBER -> paletteChipGroup.check(R.id.chipPaletteAmber)
            ColorPalette.VIOLET -> paletteChipGroup.check(R.id.chipPaletteViolet)
            ColorPalette.CRIMSON -> paletteChipGroup.check(R.id.chipPaletteCrimson)
        }
        paletteChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedPalette = when (checkedId) {
                R.id.chipPaletteCyan -> ColorPalette.CYAN
                R.id.chipPaletteBlue -> ColorPalette.BLUE
                R.id.chipPaletteEmerald -> ColorPalette.EMERALD
                R.id.chipPaletteAmber -> ColorPalette.AMBER
                R.id.chipPaletteViolet -> ColorPalette.VIOLET
                R.id.chipPaletteCrimson -> ColorPalette.CRIMSON
                else -> ColorPalette.MONET
            }
            if (themePreferences.palette != selectedPalette) {
                themePreferences.palette = selectedPalette
                recreate()
            }
        }

        // Header Surface Effect ChipGroup
        when (themePreferences.headerEffect) {
            HeaderEffect.NOTHING -> effectChipGroup.check(R.id.chipEffectNothing)
            HeaderEffect.SNOW -> effectChipGroup.check(R.id.chipEffectSnow)
            HeaderEffect.MAZE -> effectChipGroup.check(R.id.chipEffectMaze)
            HeaderEffect.CIRCUIT -> effectChipGroup.check(R.id.chipEffectCircuit)
            HeaderEffect.CODE_RAIN -> effectChipGroup.check(R.id.chipEffectCodeRain)
        }
        effectChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedEffect = when (checkedId) {
                R.id.chipEffectSnow -> HeaderEffect.SNOW
                R.id.chipEffectMaze -> HeaderEffect.MAZE
                R.id.chipEffectCircuit -> HeaderEffect.CIRCUIT
                R.id.chipEffectCodeRain -> HeaderEffect.CODE_RAIN
                else -> HeaderEffect.NOTHING
            }
            if (themePreferences.headerEffect != selectedEffect) {
                themePreferences.headerEffect = selectedEffect
                headerEffectView.setEffect(selectedEffect)
            }
        }

        // Keyboard Prompt Switch & Manual Switch Button
        switchPromptKeyboardSwitch.isChecked = themePreferences.promptKeyboardSwitchOnDisable
        switchPromptKeyboardSwitch.setOnCheckedChangeListener { _, isChecked ->
            themePreferences.promptKeyboardSwitchOnDisable = isChecked
        }

        btnSwitchKeyboardManual.setOnClickListener {
            showKeyboardPicker()
        }

        setupKeyboardCustomizationControls()
    }

    private fun setupKeyboardCustomizationControls() {
        val notifyChange = {
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(KeyboardPreferences.ACTION_SETTINGS_CHANGED)
            )
        }

        fun updateHeightUI(dp: Int) {
            settingsKeyboardHeightLabel.text = "$dp dp"
            sliderKeyboardHeight.value = snapToSlider(sliderKeyboardHeight, dp.toFloat())
        }

        sliderKeyboardHeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val dp = value.toInt()
                keyboardPreferences.heightDp = dp
                settingsKeyboardHeightLabel.text = "$dp dp"
                notifyChange()
            }
        }

        btnSettingsHeightCompact.setOnClickListener {
            keyboardPreferences.heightDp = 210
            updateHeightUI(210)
            notifyChange()
        }
        btnSettingsHeightShort.setOnClickListener {
            keyboardPreferences.heightDp = 245
            updateHeightUI(245)
            notifyChange()
        }
        btnSettingsHeightNormal.setOnClickListener {
            keyboardPreferences.heightDp = 280
            updateHeightUI(280)
            notifyChange()
        }
        btnSettingsHeightTall.setOnClickListener {
            keyboardPreferences.heightDp = 320
            updateHeightUI(320)
            notifyChange()
        }
        btnSettingsHeightXtall.setOnClickListener {
            keyboardPreferences.heightDp = 360
            updateHeightUI(360)
            notifyChange()
        }

        rgLongPressMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbModeRapidFire) {
                KeyboardPreferences.MODE_RAPID_FIRE
            } else {
                KeyboardPreferences.MODE_VARIANTS
            }
            keyboardPreferences.longPressMode = mode
            notifyChange()
        }

        sliderLongPressDelay.addOnChangeListener { _, value, fromUser ->
            val delayMs = value.toLong()
            settingsDelayLabel.text = "$delayMs ms"
            if (fromUser) {
                keyboardPreferences.longPressDelayMs = delayMs
                notifyChange()
            }
        }

        switchKeyHints.setOnCheckedChangeListener { _, isChecked ->
            keyboardPreferences.showKeyHints = isChecked
            notifyChange()
        }

        syncKeyboardSettingsUI()
    }

    private fun snapToSlider(slider: Slider, rawVal: Float): Float {
        val from = slider.valueFrom
        val to = slider.valueTo
        val step = slider.stepSize
        val clamped = rawVal.coerceIn(from, to)
        if (step <= 0f) return clamped
        val numSteps = Math.round((clamped - from) / step)
        return (from + numSteps * step).coerceIn(from, to)
    }

    private fun syncKeyboardSettingsUI() {
        if (!::settingsKeyboardHeightLabel.isInitialized) return
        val height = keyboardPreferences.heightDp
        settingsKeyboardHeightLabel.text = "$height dp"
        sliderKeyboardHeight.value = snapToSlider(sliderKeyboardHeight, height.toFloat())

        if (keyboardPreferences.longPressMode == KeyboardPreferences.MODE_RAPID_FIRE) {
            rbModeRapidFire.isChecked = true
        } else {
            rbModeVariants.isChecked = true
        }

        val delay = keyboardPreferences.longPressDelayMs
        settingsDelayLabel.text = "$delay ms"
        sliderLongPressDelay.value = snapToSlider(sliderLongPressDelay, delay.toFloat())

        switchKeyHints.isChecked = keyboardPreferences.showKeyHints
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
            monkeytypeInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(monkeytypeInput, InputMethodManager.SHOW_IMPLICIT)
        }
        btnStep2Keyboard.setOnClickListener {
            handleKeyboardSetupClick()
        }
        btnStep2Admin.setOnClickListener {
            handleAdminSetupClick()
        }

        btnStep3Back.setOnClickListener {
            setEnrollmentStep(2)
        }

        btnLaunchMonitor.setOnClickListener {
            bottomNavigation.selectedItemId = R.id.nav_dashboard
            masterSwitch.isChecked = true
        }
    }

    // =========================================================================
    // MONKEYTYPE ENGINE IMPLEMENTATION
    // =========================================================================
    private fun setupMonkeytypeEngine() {
        resetMonkeytypeWords()

        btnMonkeytypeRestart.setOnClickListener {
            resetMonkeytypeWords()
            monkeytypeInput.requestFocus()
        }

        btnMonkeytypeClear.setOnClickListener {
            monkeytypeInput.text.clear()
        }

        monkeytypeWordsDisplay.setOnClickListener {
            monkeytypeInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(monkeytypeInput, InputMethodManager.SHOW_IMPLICIT)
        }

        monkeytypeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (targetWords.isEmpty()) return

                val rawText = s?.toString() ?: ""

                // If space is typed, advance to next word!
                if (rawText.endsWith(" ")) {
                    val typedWord = rawText.trimEnd()
                    val targetWord = targetWords.getOrElse(currentWordIndex) { "" }
                    val isMatch = typedWord == targetWord

                    wordResults.add(isMatch)
                    currentWordIndex++
                    currentTypedWord = ""

                    // If approaching end of buffered words, replenish continuously so it is endless!
                    if (currentWordIndex + 15 >= targetWords.size) {
                        generateMoreWords()
                    }

                    // Reset input field without triggering recursive space
                    monkeytypeInput.removeTextChangedListener(this)
                    monkeytypeInput.setText("")
                    monkeytypeInput.addTextChangedListener(this)

                    updateMonkeytypeStats()
                    renderMonkeytypeDisplay()
                } else {
                    currentTypedWord = rawText
                    updateMonkeytypeStats()
                    renderMonkeytypeDisplay()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun resetMonkeytypeWords() {
        targetWords.clear()
        val random = Random()
        for (i in 0 until 60) {
            val word = monkeytypeDictionary[random.nextInt(monkeytypeDictionary.size)]
            targetWords.add(word)
        }

        currentWordIndex = 0
        currentTypedWord = ""
        wordResults.clear()
        monkeytypeInput.setText("")
        monkeytypeWordCountText.text = "0 words"

        renderMonkeytypeDisplay()
    }

    private fun generateMoreWords() {
        val random = Random()
        for (i in 0 until 30) {
            targetWords.add(monkeytypeDictionary[random.nextInt(monkeytypeDictionary.size)])
        }
    }

    private fun updateMonkeytypeStats() {
        monkeytypeWordCountText.text = "$currentWordIndex words"
    }

    private fun renderMonkeytypeDisplay() {
        val ssb = SpannableStringBuilder()
        val colorPrimary = ContextCompat.getColor(this, R.color.md_theme_primary)
        val colorOnSurface = ContextCompat.getColor(this, R.color.md_theme_onSurface)
        val colorDim = ContextCompat.getColor(this, R.color.md_theme_outline)
        val colorError = ContextCompat.getColor(this, R.color.md_theme_error)
        val colorCursorBg = ContextCompat.getColor(this, R.color.md_theme_primaryContainer)

        // Show a sliding window of words (e.g. 25 words visible around current index)
        val displayStartIndex = (currentWordIndex - 3).coerceAtLeast(0)
        val displayEndIndex = (displayStartIndex + 25).coerceAtMost(targetWords.size)

        for (i in displayStartIndex until displayEndIndex) {
            val word = targetWords[i]
            val wordStart = ssb.length

            if (i < currentWordIndex) {
                // Completed word
                val isCorrect = wordResults.getOrElse(i) { true }
                ssb.append(word)
                if (isCorrect) {
                    ssb.setSpan(ForegroundColorSpan(colorOnSurface), wordStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    ssb.setSpan(ForegroundColorSpan(colorError), wordStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(StrikethroughSpan(), wordStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else if (i == currentWordIndex) {
                // Active word (character-by-character Monkeytype matching)
                val typed = currentTypedWord
                for (cIndex in word.indices) {
                    val targetChar = word[cIndex]
                    val charStart = ssb.length
                    ssb.append(targetChar)

                    if (cIndex < typed.length) {
                        if (typed[cIndex] == targetChar) {
                            ssb.setSpan(ForegroundColorSpan(colorPrimary), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            ssb.setSpan(StyleSpan(Typeface.BOLD), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        } else {
                            ssb.setSpan(ForegroundColorSpan(colorError), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            ssb.setSpan(UnderlineSpan(), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } else if (cIndex == typed.length) {
                        // Cursor caret position highlight
                        ssb.setSpan(ForegroundColorSpan(colorOnSurface), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ssb.setSpan(BackgroundColorSpan(colorCursorBg), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ssb.setSpan(StyleSpan(Typeface.BOLD), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        // Untyped remaining letters
                        ssb.setSpan(ForegroundColorSpan(colorDim), charStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                // If user typed extra letters past word end
                if (typed.length > word.length) {
                    val extraChars = typed.substring(word.length)
                    val extraStart = ssb.length
                    ssb.append(extraChars)
                    ssb.setSpan(ForegroundColorSpan(colorError), extraStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(UnderlineSpan(), extraStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                // Upcoming dimmed words
                ssb.append(word)
                ssb.setSpan(ForegroundColorSpan(colorDim), wordStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            ssb.append("  ")
        }

        monkeytypeWordsDisplay.text = ssb
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
        enrollRhythmStatus.text = "IME listening in ENROLL mode • Type words naturally"
        monkeytypeInput.requestFocus()
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
            enableKeyboardButton.isEnabled = true
            btnStep2Keyboard.text = getString(R.string.btn_keyboard_active)
            btnStep2Keyboard.isEnabled = true

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

            when (profile.status) {
                "PENDING" -> {
                    heroTitleText.text = "Status: Calibrating"
                    enrollButton.text = getString(R.string.btn_start_enrollment)
                    enrollButton.isEnabled = true
                    masterSwitch.isEnabled = false
                    masterSwitch.isChecked = false
                    gridVerifierStatusText.text = "Standby · Calibrating"
                }
                "READY", "ACTIVE" -> {
                    enrollButton.text = "Profile Calibrated"
                    enrollButton.isEnabled = true
                    masterSwitch.isEnabled = true
                    if (masterSwitch.isChecked) {
                        heroTitleText.text = "Status: Active"
                        gridVerifierStatusText.text = "Active · System Protected"
                    } else {
                        heroTitleText.text = "Status: Standby"
                        gridVerifierStatusText.text = "Standby · System Inactive"
                    }
                }
            }
        } else {
            heroTitleText.text = "Status: Standby"
            profileStatusText.text = getString(R.string.status_not_enrolled)
            keystrokeCountText.text = "0"
            enrollmentProgress.progress = 0
            enrollmentPercentText.text = "0%"
            enrollTelemetryProgressBar.progress = 0
            enrollTelemetryPercent.text = "0%"
            enrollKeystrokeCounter.text = "Keystrokes: 0 / 250"
            enrollVectorCounter.text = "Vectors: 0 / 5"
            masterSwitch.isEnabled = false
            masterSwitch.isChecked = false
            gridVerifierStatusText.text = "Standby · System Inactive"
        }
        syncKeyboardSettingsUI()
    }

    private fun updateRiskDisplay(composite: Double, ocsvm: Double, mahal: Double, action: String, keystrokeCount: Int) {
        compositeScoreText.text = String.format("%.3f", composite)
        ocsvmScoreText.text = String.format("%.3f", ocsvm)
        mahalScoreText.text = String.format("%.3f", mahal)
        actionText.text = action

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

        if (status == "READY" || status == "ACTIVE") {
            setEnrollmentStep(4)
            masterSwitch.isEnabled = true
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
    }

    private fun promptKeyboardSwitchIfNeeded() {
        if (!themePreferences.promptKeyboardSwitchOnDisable) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_switch_keyboard, null)
        val checkDontShowAgain = dialogView.findViewById<CheckBox>(R.id.checkDontShowAgain)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_switch_keyboard_title)
            .setMessage(R.string.dialog_switch_keyboard_message)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_switch_keyboard_positive) { _, _ ->
                if (checkDontShowAgain.isChecked) {
                    themePreferences.promptKeyboardSwitchOnDisable = false
                }
                showKeyboardPicker()
            }
            .setNegativeButton(R.string.dialog_switch_keyboard_negative) { _, _ ->
                if (checkDontShowAgain.isChecked) {
                    themePreferences.promptKeyboardSwitchOnDisable = false
                }
            }
            .show()
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }
}