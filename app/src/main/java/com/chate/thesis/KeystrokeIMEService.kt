package com.chate.thesis

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

class KeystrokeIMEService : InputMethodService() {
    private val eventBuffer = mutableListOf<KeystrokeEvent>()
    private val featureExtractor = FeatureExtractor()
    private var capsMode = CapsMode.OFF
    private val isShifted: Boolean get() = capsMode.isShifted()
    private var isSymbols = false
    private var isAltSymbols = false
    private lateinit var keyboardView: View
    private lateinit var preferences: KeyboardPreferences
    private var audioManager: AudioManager? = null
    private var soundPool: SoundPool? = null
    private var soundStandardId: Int = 0
    private var soundSpacebarId: Int = 0
    private var soundDeleteId: Int = 0
    private var soundReturnId: Int = 0
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Main layout containers
    private lateinit var keyboardRootFrame: FrameLayout
    private lateinit var keyboardRowsContainer: LinearLayout
    private lateinit var resizePanel: LinearLayout
    private lateinit var keyboardOverlayContainer: FrameLayout
    private lateinit var variantsPopupCard: LinearLayout
    private lateinit var keyPreviewBubble: FrameLayout
    private lateinit var tvPreviewChar: TextView

    // Resize controls
    private lateinit var btnResizeToggle: ImageView
    private lateinit var resizeDragHandleArea: LinearLayout
    private lateinit var tvResizeHeightLabel: TextView
    private lateinit var btnResizeReset: TextView
    private lateinit var btnResizeDone: TextView
    private lateinit var btnModeToggle: TextView

    // Risk indicator views
    private lateinit var statusBar: View
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var enrollmentText: TextView

    // Engine & State
    private lateinit var profileManager: ProfileManager
    private lateinit var riskScorer: RiskScorer
    private lateinit var securityLogger: SecurityLogger

    private var currentMode = "IDLE"
    private var currentUserId = MainActivity.DEFAULT_USER_ID
    private val windowThreshold = 20 // Window size for extraction

    // Touch & Long-Press Handling
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeLongPressRunnable: Runnable? = null
    private var activeRepeatRunnable: Runnable? = null
    private var isLongPressActive = false
    private var isRapidFiring = false
    private var selectedVariantIndex = 0
    private var currentVariantsList: List<String> = emptyList()
    private var touchSlop = 20
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Dynamic Theme Tokens & Context
    private lateinit var themedContext: Context
    private var currentPrimaryColor: Int = 0
    private var currentOnPrimaryColor: Int = 0
    private var currentPrimaryContainer: Int = 0
    private var currentOnPrimaryContainer: Int = 0
    private var currentKeyTextColor: Int = 0
    private var currentOnSurfaceVariant: Int = 0
    private var isAmoledActive: Boolean = false
    private var lastThemedConfig: String = ""

    private fun getThemeSignature(): String {
        val themePrefs = ThemePreferences(this)
        val isNight =
            when (themePrefs.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> {
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                }
            }
        return "${themePrefs.themeMode.id}_${themePrefs.palette.id}_${themePrefs.isAmoledBlackEnabled}_$isNight"
    }

    private val themePreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in listOf(ThemePreferences.KEY_THEME_MODE, ThemePreferences.KEY_PALETTE, ThemePreferences.KEY_AMOLED_BLACK)) {
                mainHandler.post {
                    reloadInputView()
                }
            }
        }

    companion object {
        const val ACTION_SET_MODE = "com.chate.thesis.SET_MODE"
        const val ACTION_STEP_UP_RESULT = "com.chate.thesis.STEP_UP_RESULT"
    }

    private val modeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                intent ?: return
                when (intent.action) {
                    ACTION_SET_MODE -> {
                        currentMode = intent.getStringExtra("mode") ?: "IDLE"
                        currentUserId = intent.getStringExtra("user_id") ?: MainActivity.DEFAULT_USER_ID

                        if (::enrollmentText.isInitialized) {
                            if (currentMode == "ENROLL") {
                                enrollmentText.text = "ENROLLING..."
                                statusText.text = "PENDING"
                                setIndicatorColor(R.color.risk_warn)
                                scoreText.text = ""
                            } else if (currentMode == "VERIFY") {
                                enrollmentText.text = ""
                                statusText.text = "READY"
                                setIndicatorColor(R.color.risk_allow)
                                scoreText.text = "0.00"
                                riskScorer.resetHistory()
                            } else {
                                enrollmentText.text = ""
                                statusText.text = "IDLE"
                                setIndicatorColor(R.color.text_secondary)
                                scoreText.text = ""
                            }
                        }
                        eventBuffer.clear()
                    }
                    ACTION_STEP_UP_RESULT -> {
                        val success = intent.getBooleanExtra("success", false)
                        if (success) {
                            riskScorer.resetHistory()
                            if (::scoreText.isInitialized) {
                                scoreText.text = "0.00"
                                statusText.text = "READY"
                                setIndicatorColor(R.color.risk_allow)
                            }
                        } else {
                            lockDevice(this@KeystrokeIMEService)
                        }
                    }
                    KeyboardPreferences.ACTION_SETTINGS_CHANGED -> {
                        applyKeyboardPreferences()
                    }
                    ThemePreferences.ACTION_THEME_CHANGED -> {
                        reloadInputView()
                    }
                }
            }
        }

    // Map of button IDs to key codes and labels
    private val keyMap =
        mapOf(
            R.id.key_q to Pair(KeyEvent.KEYCODE_Q, "q"),
            R.id.key_w to Pair(KeyEvent.KEYCODE_W, "w"),
            R.id.key_e to Pair(KeyEvent.KEYCODE_E, "e"),
            R.id.key_r to Pair(KeyEvent.KEYCODE_R, "r"),
            R.id.key_t to Pair(KeyEvent.KEYCODE_T, "t"),
            R.id.key_y to Pair(KeyEvent.KEYCODE_Y, "y"),
            R.id.key_u to Pair(KeyEvent.KEYCODE_U, "u"),
            R.id.key_i to Pair(KeyEvent.KEYCODE_I, "i"),
            R.id.key_o to Pair(KeyEvent.KEYCODE_O, "o"),
            R.id.key_p to Pair(KeyEvent.KEYCODE_P, "p"),
            R.id.key_a to Pair(KeyEvent.KEYCODE_A, "a"),
            R.id.key_s to Pair(KeyEvent.KEYCODE_S, "s"),
            R.id.key_d to Pair(KeyEvent.KEYCODE_D, "d"),
            R.id.key_f to Pair(KeyEvent.KEYCODE_F, "f"),
            R.id.key_g to Pair(KeyEvent.KEYCODE_G, "g"),
            R.id.key_h to Pair(KeyEvent.KEYCODE_H, "h"),
            R.id.key_j to Pair(KeyEvent.KEYCODE_J, "j"),
            R.id.key_k to Pair(KeyEvent.KEYCODE_K, "k"),
            R.id.key_l to Pair(KeyEvent.KEYCODE_L, "l"),
            R.id.key_z to Pair(KeyEvent.KEYCODE_Z, "z"),
            R.id.key_x to Pair(KeyEvent.KEYCODE_X, "x"),
            R.id.key_c to Pair(KeyEvent.KEYCODE_C, "c"),
            R.id.key_v to Pair(KeyEvent.KEYCODE_V, "v"),
            R.id.key_b to Pair(KeyEvent.KEYCODE_B, "b"),
            R.id.key_n to Pair(KeyEvent.KEYCODE_N, "n"),
            R.id.key_m to Pair(KeyEvent.KEYCODE_M, "m"),
        )

    private val symbolsMap =
        mapOf(
            R.id.key_q to Pair(KeyEvent.KEYCODE_1, "1"),
            R.id.key_w to Pair(KeyEvent.KEYCODE_2, "2"),
            R.id.key_e to Pair(KeyEvent.KEYCODE_3, "3"),
            R.id.key_r to Pair(KeyEvent.KEYCODE_4, "4"),
            R.id.key_t to Pair(KeyEvent.KEYCODE_5, "5"),
            R.id.key_y to Pair(KeyEvent.KEYCODE_6, "6"),
            R.id.key_u to Pair(KeyEvent.KEYCODE_7, "7"),
            R.id.key_i to Pair(KeyEvent.KEYCODE_8, "8"),
            R.id.key_o to Pair(KeyEvent.KEYCODE_9, "9"),
            R.id.key_p to Pair(KeyEvent.KEYCODE_0, "0"),
            R.id.key_a to Pair(KeyEvent.KEYCODE_AT, "@"),
            R.id.key_s to Pair(KeyEvent.KEYCODE_POUND, "#"),
            R.id.key_d to Pair(KeyEvent.KEYCODE_4, "$"),
            R.id.key_f to Pair(KeyEvent.KEYCODE_5, "%"),
            R.id.key_g to Pair(KeyEvent.KEYCODE_7, "&"),
            R.id.key_h to Pair(KeyEvent.KEYCODE_MINUS, "-"),
            R.id.key_j to Pair(KeyEvent.KEYCODE_PLUS, "+"),
            R.id.key_k to Pair(KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN, "("),
            R.id.key_l to Pair(KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN, ")"),
            R.id.key_z to Pair(KeyEvent.KEYCODE_STAR, "*"),
            R.id.key_x to Pair(KeyEvent.KEYCODE_APOSTROPHE, "\""),
            R.id.key_c to Pair(KeyEvent.KEYCODE_APOSTROPHE, "'"),
            R.id.key_v to Pair(KeyEvent.KEYCODE_SEMICOLON, ":"),
            R.id.key_b to Pair(KeyEvent.KEYCODE_SEMICOLON, ";"),
            R.id.key_n to Pair(KeyEvent.KEYCODE_1, "!"),
            R.id.key_m to Pair(KeyEvent.KEYCODE_SLASH, "?"),
        )

    private val altSymbolsMap =
        mapOf(
            R.id.key_q to Pair(KeyEvent.KEYCODE_GRAVE, "~"),
            R.id.key_w to Pair(KeyEvent.KEYCODE_GRAVE, "`"),
            R.id.key_e to Pair(KeyEvent.KEYCODE_POUND, "|"),
            R.id.key_r to Pair(KeyEvent.KEYCODE_6, "^"),
            R.id.key_t to Pair(KeyEvent.KEYCODE_EQUALS, "="),
            R.id.key_y to Pair(KeyEvent.KEYCODE_SLASH, "/"),
            R.id.key_u to Pair(KeyEvent.KEYCODE_BACKSLASH, "\\"),
            R.id.key_i to Pair(KeyEvent.KEYCODE_LEFT_BRACKET, "{"),
            R.id.key_o to Pair(KeyEvent.KEYCODE_RIGHT_BRACKET, "}"),
            R.id.key_p to Pair(KeyEvent.KEYCODE_MINUS, "_"),
            R.id.key_a to Pair(KeyEvent.KEYCODE_4, "€"),
            R.id.key_s to Pair(KeyEvent.KEYCODE_4, "£"),
            R.id.key_d to Pair(KeyEvent.KEYCODE_4, "¥"),
            R.id.key_f to Pair(KeyEvent.KEYCODE_4, "₩"),
            R.id.key_g to Pair(KeyEvent.KEYCODE_POUND, "§"),
            R.id.key_h to Pair(KeyEvent.KEYCODE_0, "°"),
            R.id.key_j to Pair(KeyEvent.KEYCODE_PLUS, "±"),
            R.id.key_k to Pair(KeyEvent.KEYCODE_LEFT_BRACKET, "["),
            R.id.key_l to Pair(KeyEvent.KEYCODE_RIGHT_BRACKET, "]"),
            R.id.key_z to Pair(KeyEvent.KEYCODE_COMMA, "<"),
            R.id.key_x to Pair(KeyEvent.KEYCODE_PERIOD, ">"),
            R.id.key_c to Pair(KeyEvent.KEYCODE_APOSTROPHE, "“"),
            R.id.key_v to Pair(KeyEvent.KEYCODE_APOSTROPHE, "”"),
            R.id.key_b to Pair(KeyEvent.KEYCODE_EQUALS, "≠"),
            R.id.key_n to Pair(KeyEvent.KEYCODE_1, "¡"),
            R.id.key_m to Pair(KeyEvent.KEYCODE_SLASH, "¿"),
        )

    override fun onCreate() {
        super.onCreate()
        preferences = KeyboardPreferences(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        initSoundFeedback()
        profileManager = ProfileManager(this, testingMode = true)
        riskScorer = RiskScorer()
        securityLogger = SecurityLogger(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val filter =
            IntentFilter().apply {
                addAction(ACTION_SET_MODE)
                addAction(ACTION_STEP_UP_RESULT)
                addAction(KeyboardPreferences.ACTION_SETTINGS_CHANGED)
                addAction(ThemePreferences.ACTION_THEME_CHANGED)
            }
        LocalBroadcastManager.getInstance(this).registerReceiver(modeReceiver, filter)

        val themePrefs = getSharedPreferences(ThemePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        themePrefs.registerOnSharedPreferenceChangeListener(themePreferenceListener)
    }

    override fun onDestroy() {
        releaseSoundFeedback()
        val themePrefs = getSharedPreferences(ThemePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        themePrefs.unregisterOnSharedPreferenceChangeListener(themePreferenceListener)
        cancelAllRunnables()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(modeReceiver)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        themedContext = getThemedContext()
        keyboardView = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_layout, null)

        keyboardRootFrame = keyboardView.findViewById(R.id.keyboard_root_frame)
        keyboardRowsContainer = keyboardView.findViewById(R.id.keyboard_rows_container)
        resizePanel = keyboardView.findViewById(R.id.resize_panel)
        keyboardOverlayContainer = keyboardView.findViewById(R.id.keyboard_overlay_container)
        variantsPopupCard = keyboardView.findViewById(R.id.variants_popup_card)

        // Init UI elements
        statusBar = keyboardView.findViewById(R.id.status_bar)
        statusIndicator = keyboardView.findViewById(R.id.status_indicator)
        statusText = keyboardView.findViewById(R.id.status_text)
        scoreText = keyboardView.findViewById(R.id.score_text)
        enrollmentText = keyboardView.findViewById(R.id.enrollment_text)

        // Setup Resize controls
        btnResizeToggle = keyboardView.findViewById(R.id.btn_resize_toggle)
        resizeDragHandleArea = keyboardView.findViewById(R.id.resize_drag_handle_area)
        tvResizeHeightLabel = keyboardView.findViewById(R.id.tv_resize_height_label)
        btnResizeReset = keyboardView.findViewById(R.id.btn_resize_reset)
        btnResizeDone = keyboardView.findViewById(R.id.btn_resize_done)
        btnModeToggle = keyboardView.findViewById(R.id.btn_mode_toggle)
        keyPreviewBubble = keyboardView.findViewById(R.id.key_preview_bubble)
        tvPreviewChar = keyboardView.findViewById(R.id.tv_preview_char)

        keyboardView.findViewById<View>(R.id.btn_keyboard_dismiss)?.setOnClickListener {
            triggerKeyFeedback(it)
            requestHideSelf(0)
        }

        setupResizeControls()
        setupModeToggle()

        // Wire up all letter keys with touch listener
        keyMap.keys.forEach { buttonId ->
            val button = keyboardView.findViewById<TextView>(buttonId)
            button?.isHapticFeedbackEnabled = true
            button?.setOnTouchListener { v, event ->
                handleKeyTouch(v, event, v.id)
                true
            }
        }

        // Space bar
        keyboardView.findViewById<TextView>(R.id.key_space)?.let { keySpace ->
            keySpace.isHapticFeedbackEnabled = true
            keySpace.setOnTouchListener { v, event ->
                handleSpecialKeyTouch(v, event, KeyEvent.KEYCODE_SPACE, " ", allowRepeat = true) {
                    currentInputConnection?.commitText(" ", 1)
                }
                true
            }
        }

        // Delete key (Rapid-Fire repeat enabled!)
        keyboardView.findViewById<TextView>(R.id.key_delete)?.let { keyDelete ->
            keyDelete.isHapticFeedbackEnabled = true
            keyDelete.setOnTouchListener { v, event ->
                handleDeleteKeyTouch(v, event)
                true
            }
        }

        // Enter key
        keyboardView.findViewById<TextView>(R.id.key_enter)?.let { keyEnter ->
            keyEnter.isHapticFeedbackEnabled = true
            keyEnter.setOnTouchListener { v, event ->
                handleSpecialKeyTouch(v, event, KeyEvent.KEYCODE_ENTER, "ENTER", allowRepeat = false) {
                    currentInputConnection?.commitText("\n", 1)
                }
                true
            }
        }

        // Comma key
        keyboardView.findViewById<TextView>(R.id.key_comma)?.let { keyComma ->
            keyComma.isHapticFeedbackEnabled = true
            keyComma.setOnTouchListener { v, event ->
                handleKeyWithVariantsTouch(v, event, KeyEvent.KEYCODE_COMMA, ",")
                true
            }
        }

        // Period key
        keyboardView.findViewById<TextView>(R.id.key_period)?.let { keyPeriod ->
            keyPeriod.isHapticFeedbackEnabled = true
            keyPeriod.setOnTouchListener { v, event ->
                handleKeyWithVariantsTouch(v, event, KeyEvent.KEYCODE_PERIOD, ".")
                true
            }
        }

        // Shift key (3 modes in alphabet: OFF -> ON_ONCE -> LOCKED -> OFF, or #+= toggle in symbols mode)
        keyboardView.findViewById<TextView>(R.id.key_shift)?.let { keyShift ->
            keyShift.isHapticFeedbackEnabled = true
            keyShift.setOnClickListener {
                triggerKeyFeedback(it)
                if (isSymbols) {
                    isAltSymbols = !isAltSymbols
                    updateShiftKey(keyboardView)
                } else {
                    capsMode = capsMode.nextMode()
                    updateShiftKey(keyboardView)
                }
            }
        }

        // Combined Symbols (?123 / ABC) key
        keyboardView.findViewById<TextView>(R.id.key_symbols)?.let { keySymbols ->
            keySymbols.isHapticFeedbackEnabled = true
            keySymbols.setOnClickListener {
                triggerKeyFeedback(it)
                if (isSymbols) {
                    isSymbols = false
                    isAltSymbols = false
                } else {
                    isSymbols = true
                    isAltSymbols = false
                }
                updateShiftKey(keyboardView)
            }
        }

        // Dismiss variants popup on overlay touch outside
        keyboardOverlayContainer.setOnTouchListener { _, _ ->
            hideVariantsPopup()
            true
        }

        // Apply dynamic theme colors and styles
        applyThemeColors(themedContext)

        // Apply saved keyboard height and preferences
        applyKeyboardPreferences()
        updateShiftKey(keyboardView)

        // Update mode on attach
        val profile = profileManager.getProfile(MainActivity.DEFAULT_USER_ID)
        if (profile != null) {
            if (profile.isReady) {
                currentMode = "VERIFY"
                statusText.text = "READY"
                setIndicatorColor(R.color.risk_allow)
                scoreText.text = "0.00"
            } else {
                currentMode = "ENROLL"
                statusText.text = "PENDING"
                setIndicatorColor(R.color.risk_warn)
                enrollmentText.text = "${profile.progressPct.toInt()}%"
            }
        } else {
            currentMode = "IDLE"
            statusText.text = "IDLE"
            setIndicatorColor(R.color.text_secondary)
        }

        lastThemedConfig = getThemeSignature()
        return keyboardView
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        if (getThemeSignature() != lastThemedConfig) {
            reloadInputView()
        }
        applyKeyboardPreferences()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelAllRunnables()
        hideVariantsPopup()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onShowInputRequested(
        flags: Int,
        configChange: Boolean,
    ): Boolean {
        return true
    }

    // =========================================================================
    // KEYBOARD RESIZING IMPLEMENTATION
    // =========================================================================

    private fun setupResizeControls() {
        btnResizeToggle.setOnClickListener {
            toggleResizePanel()
        }

        btnResizeDone.setOnClickListener {
            hideResizePanel()
        }

        btnResizeReset.setOnClickListener {
            setKeyboardHeightDp(KeyboardPreferences.DEFAULT_HEIGHT_DP)
        }

        // Preset buttons
        keyboardView.findViewById<View>(R.id.btn_size_compact)?.setOnClickListener { setKeyboardHeightDp(210) }
        keyboardView.findViewById<View>(R.id.btn_size_short)?.setOnClickListener { setKeyboardHeightDp(245) }
        keyboardView.findViewById<View>(R.id.btn_size_normal)?.setOnClickListener { setKeyboardHeightDp(280) }
        keyboardView.findViewById<View>(R.id.btn_size_tall)?.setOnClickListener { setKeyboardHeightDp(320) }
        keyboardView.findViewById<View>(R.id.btn_size_xtall)?.setOnClickListener { setKeyboardHeightDp(360) }

        // Interactive touch-drag handle
        var dragStartY = 0f
        var startHeightPx = 0
        val density = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels
        val maxAllowedPx = (screenHeight * 0.65f).toInt().coerceAtMost((KeyboardPreferences.MAX_HEIGHT_DP * density).toInt())
        val minAllowedPx = (KeyboardPreferences.MIN_HEIGHT_DP * density).toInt()

        resizeDragHandleArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    startHeightPx = keyboardRowsContainer.height
                    resizeDragHandleArea.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Dragging upwards increases keyboard height
                    val deltaY = dragStartY - event.rawY
                    val targetHeight = (startHeightPx + deltaY).toInt().coerceIn(minAllowedPx, maxAllowedPx)

                    val params = keyboardRowsContainer.layoutParams
                    params.height = targetHeight
                    keyboardRowsContainer.layoutParams = params
                    keyboardRowsContainer.requestLayout()

                    val targetDp = (targetHeight / density).toInt()
                    tvResizeHeightLabel.text = "↕ Drag to resize: $targetDp dp"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resizeDragHandleArea.isPressed = false
                    val finalDp =
                        (keyboardRowsContainer.height / density).toInt().coerceIn(
                            KeyboardPreferences.MIN_HEIGHT_DP,
                            KeyboardPreferences.MAX_HEIGHT_DP,
                        )
                    preferences.heightDp = finalDp
                    updateResizeHeightLabel(finalDp)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleResizePanel() {
        if (resizePanel.visibility == View.VISIBLE) {
            hideResizePanel()
        } else {
            showResizePanel()
        }
    }

    private fun showResizePanel() {
        resizePanel.visibility = View.VISIBLE
        btnResizeToggle.setColorFilter(currentPrimaryColor)
        updateResizeHeightLabel(preferences.heightDp)
    }

    private fun hideResizePanel() {
        resizePanel.visibility = View.GONE
        btnResizeToggle.setColorFilter(currentOnSurfaceVariant)
    }

    private fun setKeyboardHeightDp(heightDp: Int) {
        val density = resources.displayMetrics.density
        val targetPx = (heightDp * density).toInt()
        val params = keyboardRowsContainer.layoutParams
        params.height = targetPx
        keyboardRowsContainer.layoutParams = params
        keyboardRowsContainer.requestLayout()

        preferences.heightDp = heightDp
        updateResizeHeightLabel(heightDp)
    }

    private fun updateResizeHeightLabel(heightDp: Int) {
        if (::tvResizeHeightLabel.isInitialized) {
            tvResizeHeightLabel.text = "↕ Drag to resize: $heightDp dp"
        }
        updatePresetHighlights(heightDp)
    }

    private fun updatePresetHighlights(currentDp: Int) {
        if (!::keyboardView.isInitialized) return
        val presets =
            mapOf(
                R.id.btn_size_compact to 210,
                R.id.btn_size_short to 245,
                R.id.btn_size_normal to 280,
                R.id.btn_size_tall to 320,
                R.id.btn_size_xtall to 360,
            )
        val activeTextColor = currentOnPrimaryColor
        val defaultTextColor = currentKeyTextColor
        presets.forEach { (btnId, dpVal) ->
            val btn = keyboardView.findViewById<TextView>(btnId) ?: return@forEach
            if (abs(currentDp - dpVal) <= 12) {
                btn.background = createRoundedDrawable(currentPrimaryColor, 16f)
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundResource(R.drawable.resize_chip_bg)
                btn.setTextColor(defaultTextColor)
            }
        }
    }

    private fun setupModeToggle() {
        updateModeToggleButton()
        btnModeToggle.setOnClickListener {
            val newMode =
                if (preferences.longPressMode == KeyboardPreferences.MODE_VARIANTS) {
                    KeyboardPreferences.MODE_RAPID_FIRE
                } else {
                    KeyboardPreferences.MODE_VARIANTS
                }
            preferences.longPressMode = newMode
            updateModeToggleButton()

            val msg =
                if (newMode == KeyboardPreferences.MODE_VARIANTS) {
                    "Long-press: Variants (Gboard style)"
                } else {
                    "Long-press: Rapid-fire repeat"
                }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModeToggleButton() {
        if (!::btnModeToggle.isInitialized) return
        if (preferences.longPressMode == KeyboardPreferences.MODE_VARIANTS) {
            btnModeToggle.text = "VAR"
            btnModeToggle.setTextColor(currentPrimaryColor)
        } else {
            btnModeToggle.text = "REP"
            val warnColor = ContextCompat.getColor(if (::themedContext.isInitialized) themedContext else this, R.color.risk_warn)
            btnModeToggle.setTextColor(warnColor)
        }
    }

    private fun applyKeyboardPreferences() {
        if (!::keyboardRowsContainer.isInitialized) return

        val density = resources.displayMetrics.density
        val savedHeightDp = preferences.heightDp
        val targetPx = (savedHeightDp * density).toInt()

        if (keyboardRowsContainer.layoutParams.height != targetPx) {
            val params = keyboardRowsContainer.layoutParams
            params.height = targetPx
            keyboardRowsContainer.layoutParams = params
            keyboardRowsContainer.requestLayout()
        }

        updateResizeHeightLabel(savedHeightDp)
        updateModeToggleButton()
        updateKeyboardLabels(keyboardView)
    }

    // =========================================================================
    // TOUCH & LONG-PRESS (RAPID-FIRE / VARIANTS) HANDLING
    // =========================================================================

    private fun initSoundFeedback() {
        try {
            val audioAttrs =
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            val sp =
                SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(audioAttrs)
                    .build()
            soundStandardId = sp.load(this, R.raw.keypress_standard, 1)
            soundSpacebarId = sp.load(this, R.raw.keypress_spacebar, 1)
            soundDeleteId = sp.load(this, R.raw.keypress_delete, 1)
            soundReturnId = sp.load(this, R.raw.keypress_return, 1)
            soundPool = sp
        } catch (e: Exception) {
            Log.w("KeystrokeIME", "Failed to init SoundPool: ${e.message}")
        }
        try {
            audioManager?.loadSoundEffects()
        } catch (_: Exception) {
        }
    }

    private fun releaseSoundFeedback() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (_: Exception) {
        }
        try {
            audioManager?.unloadSoundEffects()
        } catch (_: Exception) {
        }
    }

    private fun triggerKeyFeedback(
        view: View,
        keyCode: Int = 0,
    ) {
        if (preferences.isVibrationEnabled) {
            val durationMs = preferences.vibrationDurationMs.coerceAtLeast(10L)
            try {
                val vib = vibrator
                if (vib != null && vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(
                            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(durationMs)
                    }
                } else {
                    view.isHapticFeedbackEnabled = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            } catch (e: Exception) {
                Log.w("KeystrokeIME", "Vibration failed: ${e.message}")
                try {
                    view.isHapticFeedbackEnabled = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Exception) {
                }
            }
        }

        if (preferences.isSoundEnabled) {
            val soundId =
                when (keyCode) {
                    KeyEvent.KEYCODE_SPACE -> soundSpacebarId
                    KeyEvent.KEYCODE_DEL -> soundDeleteId
                    KeyEvent.KEYCODE_ENTER -> soundReturnId
                    else -> soundStandardId
                }
            if (soundId != 0) {
                soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
            try {
                val effect =
                    when (keyCode) {
                        KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                        KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                        KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                        else -> AudioManager.FX_KEYPRESS_STANDARD
                    }
                audioManager?.playSoundEffect(effect, 1.0f)
            } catch (_: Exception) {
            }
        }
    }

    private fun triggerLongPressFeedback(view: View) {
        if (!preferences.isVibrationEnabled) return
        val durationMs = (preferences.vibrationDurationMs * 1.5).toLong().coerceIn(30L, 100L)
        try {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(durationMs)
                }
            } else {
                view.isHapticFeedbackEnabled = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Exception) {
            try {
                view.isHapticFeedbackEnabled = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleKeyTouch(
        view: View,
        event: MotionEvent,
        buttonId: Int,
    ) {
        val currentMap =
            when {
                !isSymbols -> keyMap
                isAltSymbols -> altSymbolsMap
                else -> symbolsMap
            }
        val keyData = currentMap[buttonId] ?: Pair(0, "")
        val actualKeyCode = keyData.first
        val rawChar = keyData.second
        val actualKeyLabel = if (isShifted && !isSymbols) rawChar.uppercase() else rawChar

        processKeyGesture(
            view = view,
            event = event,
            keyCode = actualKeyCode,
            keyChar = actualKeyLabel,
            variantsKey = rawChar,
            commitAction = {
                currentInputConnection?.commitText(actualKeyLabel, 1)
            },
        )
    }

    private fun handleKeyWithVariantsTouch(
        view: View,
        event: MotionEvent,
        keyCode: Int,
        keyChar: String,
    ) {
        processKeyGesture(
            view = view,
            event = event,
            keyCode = keyCode,
            keyChar = keyChar,
            variantsKey = keyChar,
            commitAction = {
                currentInputConnection?.commitText(keyChar, 1)
            },
        )
    }

    private fun handleSpecialKeyTouch(
        view: View,
        event: MotionEvent,
        keyCode: Int,
        keyLabel: String,
        allowRepeat: Boolean,
        commitAction: () -> Unit,
    ) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                triggerKeyFeedback(view, keyCode)
                view.isPressed = true
                cancelAllRunnables()
                eventBuffer.add(KeystrokeEvent(keyCode, 0, currentTime))

                if (allowRepeat) {
                    val repeatInterval = preferences.repeatIntervalMs
                    val delay = preferences.longPressDelayMs
                    activeLongPressRunnable =
                        Runnable {
                            isRapidFiring = true
                            activeRepeatRunnable =
                                object : Runnable {
                                    override fun run() {
                                        commitAction()
                                        triggerKeyFeedback(view, keyCode)
                                        eventBuffer.add(KeystrokeEvent(keyCode, 0, SystemClock.uptimeMillis().toDouble()))
                                        eventBuffer.add(KeystrokeEvent(keyCode, 1, (SystemClock.uptimeMillis() + 15).toDouble()))
                                        mainHandler.postDelayed(this, repeatInterval)
                                    }
                                }
                            mainHandler.post(activeRepeatRunnable!!)
                        }
                    mainHandler.postDelayed(activeLongPressRunnable!!, delay)
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelAllRunnables()
                hideKeyPreview()
                view.isPressed = false
                if (!isRapidFiring) {
                    commitAction()
                }
                isRapidFiring = false
                eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                checkAndFlush()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAllRunnables()
                isRapidFiring = false
                view.isPressed = false
            }
        }
    }

    private fun handleDeleteKeyTouch(
        view: View,
        event: MotionEvent,
    ) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                triggerKeyFeedback(view, KeyEvent.KEYCODE_DEL)
                view.isPressed = true
                cancelAllRunnables()
                currentInputConnection?.deleteSurroundingText(1, 0)
                eventBuffer.add(KeystrokeEvent(KeyEvent.KEYCODE_DEL, 0, currentTime))

                // Delete key ALWAYS supports rapid-fire auto-repeat!
                val delay = preferences.longPressDelayMs
                val repeatInterval = preferences.repeatIntervalMs
                activeLongPressRunnable =
                    Runnable {
                        isRapidFiring = true
                        activeRepeatRunnable =
                            object : Runnable {
                                override fun run() {
                                    currentInputConnection?.deleteSurroundingText(1, 0)
                                    triggerKeyFeedback(view, KeyEvent.KEYCODE_DEL)
                                    val now = SystemClock.uptimeMillis().toDouble()
                                    eventBuffer.add(KeystrokeEvent(KeyEvent.KEYCODE_DEL, 0, now))
                                    eventBuffer.add(KeystrokeEvent(KeyEvent.KEYCODE_DEL, 1, now + 10))
                                    mainHandler.postDelayed(this, repeatInterval)
                                }
                            }
                        mainHandler.post(activeRepeatRunnable!!)
                    }
                mainHandler.postDelayed(activeLongPressRunnable!!, delay)
            }
            MotionEvent.ACTION_UP -> {
                cancelAllRunnables()
                isRapidFiring = false
                view.isPressed = false
                eventBuffer.add(KeystrokeEvent(KeyEvent.KEYCODE_DEL, 1, currentTime))
                checkAndFlush()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAllRunnables()
                isRapidFiring = false
                view.isPressed = false
            }
        }
    }

    private fun processKeyGesture(
        view: View,
        event: MotionEvent,
        keyCode: Int,
        keyChar: String,
        variantsKey: String,
        commitAction: () -> Unit,
    ) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                triggerKeyFeedback(view, keyCode)
                view.isPressed = true
                cancelAllRunnables()
                isLongPressActive = false
                isRapidFiring = false

                initialTouchX = event.x
                initialTouchY = event.y

                eventBuffer.add(KeystrokeEvent(keyCode, 0, currentTime))
                val outputChar = if (isShifted && !isSymbols && keyChar.length == 1 && keyChar[0].isLetter()) keyChar.uppercase() else keyChar
                showKeyPreview(view, outputChar)

                val delay = preferences.longPressDelayMs
                val mode = preferences.longPressMode
                val variants = CharacterVariants.getVariants(variantsKey, isShifted)

                activeLongPressRunnable =
                    Runnable {
                        isLongPressActive = true
                        hideKeyPreview()
                        triggerLongPressFeedback(view)

                        if (mode == KeyboardPreferences.MODE_RAPID_FIRE || variants.isEmpty()) {
                            // Rapid-fire key repeat
                            isRapidFiring = true
                            val repeatInterval = preferences.repeatIntervalMs
                            activeRepeatRunnable =
                                object : Runnable {
                                    override fun run() {
                                        commitAction()
                                        triggerKeyFeedback(view, keyCode)
                                        val now = SystemClock.uptimeMillis().toDouble()
                                        eventBuffer.add(KeystrokeEvent(keyCode, 0, now))
                                        eventBuffer.add(KeystrokeEvent(keyCode, 1, now + 15))
                                        mainHandler.postDelayed(this, repeatInterval)
                                    }
                                }
                            mainHandler.post(activeRepeatRunnable!!)
                        } else {
                            // Gboard-style Character Variants Popup
                            showVariantsPopup(view, variants)
                        }
                    }
                mainHandler.postDelayed(activeLongPressRunnable!!, delay)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isVariantsPopupShowing()) {
                    updateVariantSelectionByTouch(event.rawX)
                } else if (!isLongPressActive) {
                    val dx = abs(event.x - initialTouchX)
                    val dy = abs(event.y - initialTouchY)
                    if (dx > touchSlop * 2 || dy > touchSlop * 2) {
                        cancelAllRunnables()
                        hideKeyPreview()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelAllRunnables()
                hideKeyPreview()
                view.isPressed = false

                if (isVariantsPopupShowing()) {
                    val chosen = getSelectedVariant()
                    hideVariantsPopup()
                    if (chosen != null) {
                        triggerKeyFeedback(view, keyCode)
                        currentInputConnection?.commitText(chosen, 1)
                    } else {
                        commitAction()
                    }
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (capsMode == CapsMode.ON_ONCE) {
                        capsMode = CapsMode.OFF
                        updateShiftKey(keyboardView)
                    }
                } else if (isRapidFiring) {
                    isRapidFiring = false
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (capsMode == CapsMode.ON_ONCE) {
                        capsMode = CapsMode.OFF
                        updateShiftKey(keyboardView)
                    }
                } else {
                    // Normal tap
                    commitAction()
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (capsMode == CapsMode.ON_ONCE) {
                        capsMode = CapsMode.OFF
                        updateShiftKey(keyboardView)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAllRunnables()
                hideKeyPreview()
                hideVariantsPopup()
                isRapidFiring = false
                isLongPressActive = false
                view.isPressed = false
            }
        }
    }

    private fun cancelAllRunnables() {
        activeLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
        activeLongPressRunnable = null
        activeRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
        activeRepeatRunnable = null
    }

    // =========================================================================
    // CHARACTER VARIANTS POPUP (GBOARD STYLE)
    // =========================================================================

    private fun isVariantsPopupShowing(): Boolean {
        return ::variantsPopupCard.isInitialized && variantsPopupCard.visibility == View.VISIBLE && variantsPopupCard.childCount > 0
    }

    private fun isKeyPreviewShowing(): Boolean {
        return ::keyPreviewBubble.isInitialized && keyPreviewBubble.visibility == View.VISIBLE
    }

    private fun showKeyPreview(
        anchorKey: View,
        char: String,
    ) {
        if (!::keyPreviewBubble.isInitialized || !::tvPreviewChar.isInitialized) return
        tvPreviewChar.text = char
        keyPreviewBubble.visibility = View.VISIBLE
        if (::keyboardOverlayContainer.isInitialized) {
            keyboardOverlayContainer.visibility = View.VISIBLE
        }

        val density = resources.displayMetrics.density
        val bubbleWidth = (56 * density).toInt()
        val bubbleHeight = (56 * density).toInt()

        val rootLocation = IntArray(2)
        keyboardRootFrame.getLocationInWindow(rootLocation)

        val keyLocation = IntArray(2)
        anchorKey.getLocationInWindow(keyLocation)

        val relKeyX = keyLocation[0] - rootLocation[0]
        val relKeyY = keyLocation[1] - rootLocation[1]
        val rootWidth = keyboardRootFrame.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels

        val margin = (4 * density).toInt()
        val maxPosX = (rootWidth - bubbleWidth - margin).coerceAtLeast(margin)
        val rawPosX = relKeyX + (anchorKey.width / 2) - (bubbleWidth / 2)
        val posX = rawPosX.coerceIn(margin, maxPosX)

        var posY = relKeyY - bubbleHeight - (8 * density).toInt()
        if (posY < (2 * density).toInt()) {
            posY = (2 * density).toInt()
        }

        val lp =
            (keyPreviewBubble.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(bubbleWidth, bubbleHeight)
        lp.width = bubbleWidth
        lp.height = bubbleHeight
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = posX
        lp.topMargin = posY
        keyPreviewBubble.layoutParams = lp
    }

    private fun hideKeyPreview() {
        if (::keyPreviewBubble.isInitialized) {
            keyPreviewBubble.visibility = View.GONE
        }
        if (!isVariantsPopupShowing() && ::keyboardOverlayContainer.isInitialized) {
            keyboardOverlayContainer.visibility = View.GONE
        }
    }

    private fun showVariantsPopup(
        anchorKey: View,
        variants: List<String>,
    ) {
        if (!::keyboardOverlayContainer.isInitialized || variants.isEmpty()) return
        hideKeyPreview()

        currentVariantsList = variants
        variantsPopupCard.removeAllViews()

        val density = resources.displayMetrics.density
        val itemMinWidth = (38 * density).toInt()
        val itemHeight = (46 * density).toInt()

        // Build variant item views
        val tvContext = if (::themedContext.isInitialized) themedContext else this
        variants.forEachIndexed { index, variantStr ->
            val tv =
                TextView(tvContext).apply {
                    text = variantStr
                    textSize = 20f
                    gravity = Gravity.CENTER
                    minWidth = itemMinWidth
                    height = itemHeight
                    setTextColor(currentKeyTextColor)
                    tag = index
                    setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                }
            variantsPopupCard.addView(tv)
        }

        // Pre-select default variant (index 0)
        selectedVariantIndex = 0
        highlightVariantItem(selectedVariantIndex)

        // Measure and position the popup card
        variantsPopupCard.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val popupWidth = variantsPopupCard.measuredWidth
        val popupHeight = variantsPopupCard.measuredHeight

        val rootLocation = IntArray(2)
        keyboardRootFrame.getLocationInWindow(rootLocation)

        val keyLocation = IntArray(2)
        anchorKey.getLocationInWindow(keyLocation)

        val relKeyX = keyLocation[0] - rootLocation[0]
        val relKeyY = keyLocation[1] - rootLocation[1]

        val rootWidth = keyboardRootFrame.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels

        // Center horizontally above the anchor key, safely clamped inside root bounds
        val margin = (4 * density).toInt()
        val maxPosX = (rootWidth - popupWidth - margin).coerceAtLeast(margin)
        val rawPosX = relKeyX + (anchorKey.width / 2) - (popupWidth / 2)
        val posX = rawPosX.coerceIn(margin, maxPosX)

        // Position directly above the key
        var posY = relKeyY - popupHeight - (6 * density).toInt()
        if (posY < (2 * density).toInt()) {
            posY = (2 * density).toInt() // keep visible inside top area
        }

        val lp =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = posX
                topMargin = posY
            }
        variantsPopupCard.layoutParams = lp

        keyboardOverlayContainer.visibility = View.VISIBLE
    }

    private fun updateVariantSelectionByTouch(rawTouchX: Float) {
        if (!isVariantsPopupShowing()) return

        val rootLocation = IntArray(2)
        keyboardRootFrame.getLocationInWindow(rootLocation)
        val relTouchX = rawTouchX - rootLocation[0]

        val count = variantsPopupCard.childCount
        if (count == 0) return

        var closestIndex = 0
        var minDistance = Float.MAX_VALUE

        val cardLocation = IntArray(2)
        variantsPopupCard.getLocationInWindow(cardLocation)
        val relCardX = cardLocation[0] - rootLocation[0]

        for (i in 0 until count) {
            val child = variantsPopupCard.getChildAt(i)
            val childCenterX =
                if (child.width > 0) {
                    relCardX + child.left + (child.width / 2f)
                } else {
                    val itemWidth = variantsPopupCard.measuredWidth / count.toFloat()
                    variantsPopupCard.left + (i * itemWidth) + (itemWidth / 2f)
                }
            val dist = abs(relTouchX - childCenterX)
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }

        if (closestIndex != selectedVariantIndex) {
            selectedVariantIndex = closestIndex
            highlightVariantItem(selectedVariantIndex)
            keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun highlightVariantItem(index: Int) {
        val count = variantsPopupCard.childCount
        for (i in 0 until count) {
            val child = variantsPopupCard.getChildAt(i) as? TextView ?: continue
            if (i == index) {
                child.background = createRoundedDrawable(currentPrimaryColor, 8f)
                child.setTextColor(currentOnPrimaryColor)
            } else {
                child.background = null
                child.setTextColor(currentKeyTextColor)
            }
        }
    }

    private fun getSelectedVariant(): String? {
        if (selectedVariantIndex in currentVariantsList.indices) {
            return currentVariantsList[selectedVariantIndex]
        }
        return null
    }

    private fun hideVariantsPopup() {
        if (!::keyboardOverlayContainer.isInitialized) return
        if (::variantsPopupCard.isInitialized) {
            variantsPopupCard.visibility = View.GONE
            variantsPopupCard.removeAllViews()
        }
        currentVariantsList = emptyList()
        selectedVariantIndex = 0
        isLongPressActive = false
        if (!isKeyPreviewShowing()) {
            keyboardOverlayContainer.visibility = View.GONE
        }
    }

    // =========================================================================
    // KEYBOARD LABELS & SHIFT MANAGEMENT
    // =========================================================================

    private fun updateShiftKey(view: View) {
        val shiftButton = view.findViewById<TextView>(R.id.key_shift) ?: return
        if (isSymbols) {
            if (isAmoledActive) {
                shiftButton.background =
                    createKeyDrawable(
                        normalColor = Color.parseColor("#0C0E10"),
                        pressedColor = Color.parseColor("#1F2327"),
                        shadowColor = Color.BLACK,
                        borderColor = Color.parseColor("#1D2024"),
                    )
                shiftButton.setTextColor(Color.parseColor("#DEE4E6"))
            } else {
                shiftButton.background = createSpecialKeyDrawable(currentPrimaryContainer)
                shiftButton.setTextColor(currentOnPrimaryContainer)
            }
            shiftButton.textSize = 14f
            shiftButton.text = if (isAltSymbols) "123" else "#+="
            updateKeyboardLabels(view)
            return
        }
        shiftButton.textSize = 18f
        when (capsMode) {
            CapsMode.OFF -> {
                if (isAmoledActive) {
                    shiftButton.background =
                        createKeyDrawable(
                            normalColor = Color.parseColor("#0C0E10"),
                            pressedColor = Color.parseColor("#1F2327"),
                            shadowColor = Color.BLACK,
                            borderColor = Color.parseColor("#1D2024"),
                        )
                    shiftButton.setTextColor(Color.parseColor("#DEE4E6"))
                } else {
                    shiftButton.background = createSpecialKeyDrawable(currentPrimaryContainer)
                    shiftButton.setTextColor(currentOnPrimaryContainer)
                }
                shiftButton.text = "⇧"
            }
            CapsMode.ON_ONCE -> {
                shiftButton.background = createAccentDrawable(currentPrimaryColor)
                shiftButton.setTextColor(currentOnPrimaryColor)
                shiftButton.text = "⬆"
            }
            CapsMode.LOCKED -> {
                shiftButton.background = createAccentDrawable(currentPrimaryColor)
                shiftButton.setTextColor(currentOnPrimaryColor)
                shiftButton.text = "⇪"
            }
        }
        updateKeyboardLabels(view)
    }

    private fun updateKeyboardLabels(view: View) {
        val currentMap =
            when {
                !isSymbols -> keyMap
                isAltSymbols -> altSymbolsMap
                else -> symbolsMap
            }
        val showHints = preferences.showKeyHints && !isSymbols
        val ctx = if (::themedContext.isInitialized) themedContext else this

        currentMap.forEach { (buttonId, keyData) ->
            val button = view.findViewById<TextView>(buttonId) ?: return@forEach
            val baseChar = if (isShifted && !isSymbols) keyData.second.uppercase() else keyData.second
            button.text = CharacterVariants.formatKeyLabel(baseChar, isSymbols, showHints, ctx)
        }

        val symbolsButton = view.findViewById<TextView>(R.id.key_symbols)
        symbolsButton?.text = if (isSymbols) "ABC" else "?123"

        view.findViewById<TextView>(R.id.key_comma)?.let { btn ->
            btn.text = CharacterVariants.formatKeyLabel(",", isSymbols, showHints, ctx)
        }
        view.findViewById<TextView>(R.id.key_period)?.let { btn ->
            btn.text = CharacterVariants.formatKeyLabel(".", isSymbols, showHints, ctx)
        }
    }

    // =========================================================================
    // BIOMETRICS FLUSH & VERIFICATION PIPELINE
    // =========================================================================

    private fun checkAndFlush() {
        // Find pairs and filter
        var validPairs = 0
        val downEvents = mutableMapOf<Int, KeystrokeEvent>()
        for (e in eventBuffer) {
            if (e.eventType == 0) {
                downEvents[e.keyCode] = e
            } else if (e.eventType == 1) {
                if (downEvents.containsKey(e.keyCode)) {
                    validPairs++
                    downEvents.remove(e.keyCode)
                }
            }
        }

        if (validPairs >= windowThreshold) {
            val fv = featureExtractor.extract(eventBuffer)

            if (fv != null && fv.isValid()) {
                if (currentMode == "ENROLL") {
                    handleEnrollment(fv)
                } else if (currentMode == "VERIFY") {
                    handleVerification(fv)
                }
            }

            // Sliding window: Retain unpaired DOWN events for the next window
            val unpairedDowns = mutableListOf<KeystrokeEvent>()
            val upCounts = mutableMapOf<Int, Int>()

            for (e in eventBuffer) {
                if (e.eventType == 1) { // UP
                    upCounts[e.keyCode] = (upCounts[e.keyCode] ?: 0) + 1
                }
            }

            for (e in eventBuffer) {
                if (e.eventType == 0) { // DOWN
                    val ups = upCounts[e.keyCode] ?: 0
                    if (ups > 0) {
                        upCounts[e.keyCode] = ups - 1
                    } else {
                        unpairedDowns.add(e)
                    }
                }
            }

            eventBuffer.clear()
            eventBuffer.addAll(unpairedDowns)
        }
    }

    private fun handleEnrollment(fv: FeatureVector) {
        val profile = profileManager.addEnrollmentWindow(currentUserId, fv)

        enrollmentText.text = "${profile.progressPct.toInt()}%"

        val intent =
            Intent(MainActivity.ACTION_ENROLLMENT_UPDATE).apply {
                putExtra("keystroke_count", profile.keystrokeCount)
                putExtra("progress_pct", profile.progressPct)
                putExtra("status", profile.status)
            }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        if (profile.isReady) {
            currentMode = "VERIFY"
            statusText.text = "READY"
            setIndicatorColor(R.color.risk_allow)
            enrollmentText.text = ""
        }
    }

    private fun handleVerification(fv: FeatureVector) {
        val ocsvm = profileManager.getOcsvm(currentUserId)
        val mahal = profileManager.getMahalanobis(currentUserId)

        if (ocsvm == null || mahal == null) {
            Log.e("KeystrokeIMEService", "Models not found for user $currentUserId")
            return
        }

        val statArr = fv.toStatArray()
        val ocsvmScore = ocsvm.anomalyScore(statArr)
        val mahalScore = mahal.normalizedScore(statArr)

        val decision = riskScorer.score(ocsvmScore, mahalScore, fv.keystrokeCount)

        // Update IME UI
        scoreText.text = String.format("%.2f", decision.compositeScore)
        statusText.text = decision.action.displayName

        val colorRes =
            when (decision.action) {
                Action.ALLOW -> R.color.risk_allow
                Action.WARN -> R.color.risk_warn
                Action.STEP_UP_AUTH -> R.color.risk_step_up
                Action.BLOCK_SESSION -> R.color.risk_block
            }
        setIndicatorColor(colorRes)

        // Log event
        val payload =
            mapOf(
                "composite" to decision.compositeScore.toString(),
                "ocsvm" to decision.ocsvmScore.toString(),
                "mahal" to decision.mahalScore.toString(),
                "threshold" to decision.thresholdUsed.toString(),
            )
        securityLogger.log(
            userId = currentUserId,
            eventType = "AUTH_EVALUATION",
            payload = com.google.gson.Gson().toJson(payload),
            riskScore = decision.compositeScore,
            action = decision.action.displayName,
        )

        // Broadcast to Activity
        val profile = profileManager.getProfile(currentUserId)
        val intent =
            Intent(MainActivity.ACTION_RISK_UPDATE).apply {
                putExtra("composite_score", decision.compositeScore)
                putExtra("ocsvm_score", decision.ocsvmScore)
                putExtra("mahal_score", decision.mahalScore)
                putExtra("action", decision.action.displayName)
                putExtra("keystroke_count", profile?.keystrokeCount ?: 0)
            }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        if (decision.action == Action.BLOCK_SESSION) {
            lockDevice(this)
        } else if (decision.action == Action.STEP_UP_AUTH) {
            val stepUpIntent =
                Intent(this, StepUpAuthActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            startActivity(stepUpIntent)
        }
    }

    private fun lockDevice(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ThesisDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow()
            } catch (e: SecurityException) {
                Log.e("KeystrokeIMEService", "Failed to lock device screen", e)
            }
        } else {
            Log.w("KeystrokeIMEService", "Device Admin is not active. Attempting root fallback to turn off screen.")
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26"))
            } catch (e: Exception) {
                Log.e("KeystrokeIMEService", "Root fallback failed, showing BlockedActivity.", e)
                val intent =
                    Intent(context, BlockedActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                context.startActivity(intent)
            }
        }
    }

    private fun setIndicatorColor(colorRes: Int) {
        val bg = statusIndicator.background
        if (bg is GradientDrawable) {
            val ctx = if (::themedContext.isInitialized) themedContext else this
            bg.setColor(ContextCompat.getColor(ctx, colorRes))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post {
            reloadInputView()
        }
    }

    private fun reloadInputView() {
        if (!::keyboardRootFrame.isInitialized) return

        val wasCapsMode = capsMode
        val wasSymbols = isSymbols
        val wasAltSymbols = isAltSymbols
        val lastScore = if (::scoreText.isInitialized) scoreText.text?.toString() else null
        val lastStatus = if (::statusText.isInitialized) statusText.text?.toString() else null
        val lastEnroll = if (::enrollmentText.isInitialized) enrollmentText.text?.toString() else null
        val isResizeOpen = ::resizePanel.isInitialized && resizePanel.visibility == View.VISIBLE

        val newView = onCreateInputView()

        capsMode = wasCapsMode
        isSymbols = wasSymbols
        isAltSymbols = wasAltSymbols
        if (::scoreText.isInitialized && lastScore != null) scoreText.text = lastScore
        if (::statusText.isInitialized && lastStatus != null) statusText.text = lastStatus
        if (::enrollmentText.isInitialized && lastEnroll != null) enrollmentText.text = lastEnroll
        if (isResizeOpen) showResizePanel()

        updateKeyboardLabels(newView)
        updateShiftKey(newView)

        setInputView(newView)
    }

    private fun getThemedContext(): Context {
        val themePrefs = ThemePreferences(this)
        val isNight =
            when (themePrefs.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> {
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                }
            }

        val overrideConfig =
            Configuration(resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
        val configContext = createConfigurationContext(overrideConfig)

        val themeResId =
            if (themePrefs.palette == ColorPalette.MONET) {
                R.style.Theme_Thesis
            } else {
                themePrefs.palette.getThemeResId()
            }

        var themed: Context = ContextThemeWrapper(configContext, themeResId)

        if (themePrefs.palette == ColorPalette.MONET && DynamicColors.isDynamicColorAvailable()) {
            themed = DynamicColors.wrapContextIfAvailable(themed)
        }

        if (isNight && themePrefs.isAmoledBlackEnabled) {
            themed = ContextThemeWrapper(themed, R.style.Theme_Thesis_Amoled)
        }

        return themed
    }

    private fun applyThemeColors(ctx: Context) {
        val themePrefs = ThemePreferences(this)
        val isNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        isAmoledActive = isNight && themePrefs.isAmoledBlackEnabled

        currentPrimaryColor =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.md_theme_primary),
            )
        currentOnPrimaryColor =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnPrimary,
                ContextCompat.getColor(ctx, R.color.md_theme_onPrimary),
            )
        currentPrimaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorPrimaryContainer,
                ContextCompat.getColor(ctx, R.color.key_bg_special),
            )
        currentOnPrimaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                ContextCompat.getColor(ctx, R.color.key_text_special),
            )
        currentKeyTextColor =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(ctx, R.color.key_text),
            )
        currentOnSurfaceVariant =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.key_text_special),
            )
        val surfaceContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainer,
                ContextCompat.getColor(ctx, R.color.keyboard_bg),
            )
        val surfaceContainerLow =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                ContextCompat.getColor(ctx, R.color.keyboard_bar_bg),
            )

        val mainContent = keyboardView.findViewById<LinearLayout>(R.id.keyboard_main_content)
        if (isAmoledActive) {
            mainContent?.setBackgroundColor(Color.BLACK)
            statusBar.setBackgroundColor(Color.BLACK)
            resizePanel.setBackgroundColor(Color.BLACK)
            applyAmoledKeyBackgrounds()
        } else {
            mainContent?.setBackgroundColor(surfaceContainer)
            statusBar.setBackgroundColor(surfaceContainerLow)
            resizePanel.setBackgroundColor(surfaceContainerLow)

            val specialDrawable = createSpecialKeyDrawable(currentPrimaryContainer)
            keyboardView.findViewById<TextView>(R.id.key_delete)?.apply {
                background = specialDrawable.constantState?.newDrawable()?.mutate() ?: specialDrawable
                setTextColor(currentOnPrimaryContainer)
            }
            keyboardView.findViewById<TextView>(R.id.key_symbols)?.apply {
                background = specialDrawable.constantState?.newDrawable()?.mutate() ?: specialDrawable
                setTextColor(currentOnPrimaryContainer)
            }
        }

        // Return / Enter Key
        val keyEnter = keyboardView.findViewById<TextView>(R.id.key_enter)
        if (keyEnter != null) {
            keyEnter.background = createAccentDrawable(currentPrimaryColor)
            keyEnter.setTextColor(currentOnPrimaryColor)
        }

        // Shift key
        updateShiftKey(keyboardView)

        // Mode toggle button
        updateModeToggleButton()

        // Enrollment text
        if (::enrollmentText.isInitialized) {
            enrollmentText.setTextColor(currentPrimaryColor)
        }

        // Resize buttons
        if (::btnResizeDone.isInitialized) {
            btnResizeDone.background = createRoundedDrawable(currentPrimaryColor, 16f)
            btnResizeDone.setTextColor(currentOnPrimaryColor)
        }

        // Resize drag handle pill & area
        val dragHandle = keyboardView.findViewById<View>(R.id.resize_drag_handle_area)
        if (dragHandle is ViewGroup && dragHandle.childCount > 0) {
            val pill = dragHandle.getChildAt(0)
            pill?.background = createRoundedDrawable(currentPrimaryColor, 3f)
            if (isAmoledActive) {
                dragHandle.setBackgroundColor(Color.parseColor("#121417"))
            }
        }

        // Resize toggle icon tint
        if (::btnResizeToggle.isInitialized) {
            val isPanelOpen = ::resizePanel.isInitialized && resizePanel.visibility == View.VISIBLE
            btnResizeToggle.setColorFilter(if (isPanelOpen) currentPrimaryColor else currentOnSurfaceVariant)
        }
    }

    private fun applyAmoledKeyBackgrounds() {
        val normalAmoledDrawable =
            createKeyDrawable(
                normalColor = Color.parseColor("#15181C"),
                pressedColor = Color.parseColor("#262B31"),
                shadowColor = Color.BLACK,
                borderColor = Color.parseColor("#252A2F"),
            )
        val specialAmoledDrawable =
            createKeyDrawable(
                normalColor = Color.parseColor("#0C0E10"),
                pressedColor = Color.parseColor("#1F2327"),
                shadowColor = Color.BLACK,
                borderColor = Color.parseColor("#1D2024"),
            )

        keyMap.keys.forEach { buttonId ->
            keyboardView.findViewById<TextView>(buttonId)?.apply {
                background = normalAmoledDrawable.constantState?.newDrawable()?.mutate() ?: normalAmoledDrawable
                setTextColor(Color.WHITE)
            }
        }
        keyboardView.findViewById<TextView>(R.id.key_comma)?.apply {
            background = normalAmoledDrawable.constantState?.newDrawable()?.mutate() ?: normalAmoledDrawable
            setTextColor(Color.WHITE)
        }
        keyboardView.findViewById<TextView>(R.id.key_period)?.apply {
            background = normalAmoledDrawable.constantState?.newDrawable()?.mutate() ?: normalAmoledDrawable
            setTextColor(Color.WHITE)
        }
        keyboardView.findViewById<TextView>(R.id.key_space)?.apply {
            background = normalAmoledDrawable.constantState?.newDrawable()?.mutate() ?: normalAmoledDrawable
            setTextColor(Color.parseColor("#9497A0"))
        }
        keyboardView.findViewById<TextView>(R.id.key_delete)?.apply {
            background = specialAmoledDrawable.constantState?.newDrawable()?.mutate() ?: specialAmoledDrawable
            setTextColor(Color.parseColor("#DEE4E6"))
        }
        keyboardView.findViewById<TextView>(R.id.key_symbols)?.apply {
            background = specialAmoledDrawable.constantState?.newDrawable()?.mutate() ?: specialAmoledDrawable
            setTextColor(Color.parseColor("#DEE4E6"))
        }
        keyboardView.findViewById<TextView>(R.id.key_shift)?.apply {
            if (!isShifted) {
                background = specialAmoledDrawable.constantState?.newDrawable()?.mutate() ?: specialAmoledDrawable
                setTextColor(Color.parseColor("#DEE4E6"))
            }
        }
    }

    private fun createAccentDrawable(
        primaryColor: Int,
        cornerRadiusDp: Float = 7f,
    ): Drawable {
        val density = resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val strokePx = (0.75f * density).toInt().coerceAtLeast(1)
        val insetPx = (1.5f * density).toInt().coerceAtLeast(1)

        val pressedColor = adjustColorBrightness(primaryColor, 0.85f)
        val shadowColor = adjustColorBrightness(primaryColor, 0.60f)
        val borderColor = adjustColorBrightness(primaryColor, 0.75f)

        val pressedShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(pressedColor)
                setStroke(strokePx, borderColor)
            }
        val pressedLayer =
            LayerDrawable(arrayOf(pressedShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
            }

        val shadowShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(shadowColor)
            }
        val surfaceShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(primaryColor)
                setStroke(strokePx, borderColor)
            }
        val normalLayer =
            LayerDrawable(arrayOf(shadowShape, surfaceShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
                setLayerInset(1, 0, 0, 0, insetPx)
            }

        val stateList =
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
                addState(intArrayOf(), normalLayer)
            }

        val rippleAlpha = 60
        val rippleColor =
            Color.argb(
                rippleAlpha,
                Color.red(primaryColor),
                Color.green(primaryColor),
                Color.blue(primaryColor),
            )
        return RippleDrawable(ColorStateList.valueOf(rippleColor), stateList, null)
    }

    private fun createSpecialKeyDrawable(
        containerColor: Int,
        cornerRadiusDp: Float = 7f,
    ): Drawable {
        val density = resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val strokePx = (0.75f * density).toInt().coerceAtLeast(1)
        val insetPx = (1.5f * density).toInt().coerceAtLeast(1)

        val pressedColor = adjustColorBrightness(containerColor, 0.88f)
        val shadowColor = adjustColorBrightness(containerColor, 0.65f)
        val borderColor = adjustColorBrightness(containerColor, 0.78f)

        val pressedShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(pressedColor)
                setStroke(strokePx, borderColor)
            }
        val pressedLayer =
            LayerDrawable(arrayOf(pressedShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
            }

        val shadowShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(shadowColor)
            }
        val surfaceShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(containerColor)
                setStroke(strokePx, borderColor)
            }
        val normalLayer =
            LayerDrawable(arrayOf(shadowShape, surfaceShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
                setLayerInset(1, 0, 0, 0, insetPx)
            }

        val stateList =
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
                addState(intArrayOf(), normalLayer)
            }

        val rippleAlpha = 55
        val rippleColor =
            Color.argb(
                rippleAlpha,
                Color.red(currentPrimaryColor),
                Color.green(currentPrimaryColor),
                Color.blue(currentPrimaryColor),
            )
        return RippleDrawable(ColorStateList.valueOf(rippleColor), stateList, null)
    }

    private fun createKeyDrawable(
        normalColor: Int,
        pressedColor: Int,
        shadowColor: Int,
        borderColor: Int,
        cornerRadiusDp: Float = 7f,
    ): Drawable {
        val density = resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val strokePx = (0.75f * density).toInt().coerceAtLeast(1)
        val insetPx = (1.5f * density).toInt().coerceAtLeast(1)

        val pressedShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(pressedColor)
                setStroke(strokePx, borderColor)
            }
        val pressedLayer =
            LayerDrawable(arrayOf(pressedShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
            }

        val shadowShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(shadowColor)
            }
        val surfaceShape =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(normalColor)
                setStroke(strokePx, borderColor)
            }
        val normalLayer =
            LayerDrawable(arrayOf(shadowShape, surfaceShape)).apply {
                setLayerInset(0, 0, insetPx, 0, 0)
                setLayerInset(1, 0, 0, 0, insetPx)
            }

        val stateList =
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
                addState(intArrayOf(), normalLayer)
            }

        val rippleAlpha = 40
        val rippleColor =
            Color.argb(
                rippleAlpha,
                (Color.red(normalColor) + 50).coerceAtMost(255),
                (Color.green(normalColor) + 50).coerceAtMost(255),
                (Color.blue(normalColor) + 50).coerceAtMost(255),
            )
        return RippleDrawable(ColorStateList.valueOf(rippleColor), stateList, null)
    }

    private fun createRoundedDrawable(
        color: Int,
        radiusDp: Float,
    ): Drawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(color)
        }
    }

    private fun adjustColorBrightness(
        color: Int,
        factor: Float,
    ): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }
}
