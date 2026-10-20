package com.chate.thesis

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
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
import kotlin.math.abs

class KeystrokeIMEService : InputMethodService() {

    private val eventBuffer = mutableListOf<KeystrokeEvent>()
    private val featureExtractor = FeatureExtractor()
    private var isShifted = false
    private var isSymbols = false
    private lateinit var keyboardView: View
    private lateinit var preferences: KeyboardPreferences

    // Main layout containers
    private lateinit var keyboardRootFrame: FrameLayout
    private lateinit var keyboardRowsContainer: LinearLayout
    private lateinit var resizePanel: LinearLayout
    private lateinit var keyboardOverlayContainer: FrameLayout
    private lateinit var variantsPopupCard: LinearLayout

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

    companion object {
        const val ACTION_SET_MODE = "com.chate.thesis.SET_MODE"
        const val ACTION_STEP_UP_RESULT = "com.chate.thesis.STEP_UP_RESULT"
    }

    private val modeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
            }
        }
    }

    // Map of button IDs to key codes and labels
    private val keyMap = mapOf(
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
        R.id.key_m to Pair(KeyEvent.KEYCODE_M, "m")
    )

    private val symbolsMap = mapOf(
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
        R.id.key_f to Pair(KeyEvent.KEYCODE_STAR, "*"),
        R.id.key_g to Pair(KeyEvent.KEYCODE_MINUS, "-"),
        R.id.key_h to Pair(KeyEvent.KEYCODE_PLUS, "+"),
        R.id.key_j to Pair(KeyEvent.KEYCODE_EQUALS, "="),
        R.id.key_k to Pair(KeyEvent.KEYCODE_SLASH, "/"),
        R.id.key_l to Pair(KeyEvent.KEYCODE_BACKSLASH, "\\"),
        R.id.key_z to Pair(KeyEvent.KEYCODE_LEFT_BRACKET, "["),
        R.id.key_x to Pair(KeyEvent.KEYCODE_RIGHT_BRACKET, "]"),
        R.id.key_c to Pair(KeyEvent.KEYCODE_APOSTROPHE, "'"),
        R.id.key_v to Pair(KeyEvent.KEYCODE_GRAVE, "`"),
        R.id.key_b to Pair(KeyEvent.KEYCODE_SEMICOLON, ";"),
        R.id.key_n to Pair(KeyEvent.KEYCODE_COMMA, ","),
        R.id.key_m to Pair(KeyEvent.KEYCODE_PERIOD, ".")
    )

    override fun onCreate() {
        super.onCreate()
        preferences = KeyboardPreferences(this)
        profileManager = ProfileManager(this, testingMode = true)
        riskScorer = RiskScorer()
        securityLogger = SecurityLogger(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_MODE)
            addAction(ACTION_STEP_UP_RESULT)
            addAction(KeyboardPreferences.ACTION_SETTINGS_CHANGED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(modeReceiver, filter)
    }

    override fun onDestroy() {
        cancelAllRunnables()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(modeReceiver)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
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

        setupResizeControls()
        setupModeToggle()

        // Wire up all letter keys with touch listener
        keyMap.keys.forEach { buttonId ->
            val button = keyboardView.findViewById<TextView>(buttonId)
            button?.setOnTouchListener { v, event ->
                handleKeyTouch(v, event, v.id)
                true
            }
        }

        // Space bar
        keyboardView.findViewById<TextView>(R.id.key_space).setOnTouchListener { v, event ->
            handleSpecialKeyTouch(v, event, KeyEvent.KEYCODE_SPACE, " ", allowRepeat = true) {
                currentInputConnection?.commitText(" ", 1)
            }
            true
        }

        // Delete key (Rapid-Fire repeat enabled!)
        keyboardView.findViewById<TextView>(R.id.key_delete).setOnTouchListener { v, event ->
            handleDeleteKeyTouch(v, event)
            true
        }

        // Enter key
        keyboardView.findViewById<TextView>(R.id.key_enter).setOnTouchListener { v, event ->
            handleSpecialKeyTouch(v, event, KeyEvent.KEYCODE_ENTER, "ENTER", allowRepeat = false) {
                currentInputConnection?.commitText("\n", 1)
            }
            true
        }

        // Comma key
        keyboardView.findViewById<TextView>(R.id.key_comma).setOnTouchListener { v, event ->
            handleKeyWithVariantsTouch(v, event, KeyEvent.KEYCODE_COMMA, ",")
            true
        }

        // Period key
        keyboardView.findViewById<TextView>(R.id.key_period).setOnTouchListener { v, event ->
            handleKeyWithVariantsTouch(v, event, KeyEvent.KEYCODE_PERIOD, ".")
            true
        }

        // Shift key
        keyboardView.findViewById<TextView>(R.id.key_shift).setOnClickListener {
            isShifted = !isShifted
            updateShiftKey(keyboardView)
        }

        // Symbols key
        keyboardView.findViewById<TextView>(R.id.key_symbols).setOnClickListener {
            isSymbols = !isSymbols
            updateKeyboardLabels(keyboardView)
        }

        // Dismiss variants popup on overlay touch outside
        keyboardOverlayContainer.setOnTouchListener { _, _ ->
            hideVariantsPopup()
            true
        }

        // Apply saved keyboard height and preferences
        applyKeyboardPreferences()

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

        return keyboardView
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
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
                    val finalDp = (keyboardRowsContainer.height / density).toInt().coerceIn(
                        KeyboardPreferences.MIN_HEIGHT_DP,
                        KeyboardPreferences.MAX_HEIGHT_DP
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
        btnResizeToggle.setColorFilter(ContextCompat.getColor(this, R.color.lavender_blue))
        updateResizeHeightLabel(preferences.heightDp)
    }

    private fun hideResizePanel() {
        resizePanel.visibility = View.GONE
        btnResizeToggle.setColorFilter(ContextCompat.getColor(this, R.color.key_text_special))
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
        val presets = mapOf(
            R.id.btn_size_compact to 210,
            R.id.btn_size_short to 245,
            R.id.btn_size_normal to 280,
            R.id.btn_size_tall to 320,
            R.id.btn_size_xtall to 360
        )
        val activeTextColor = ContextCompat.getColor(this, R.color.key_accent_text)
        val defaultTextColor = ContextCompat.getColor(this, R.color.key_text)
        presets.forEach { (btnId, dpVal) ->
            val btn = keyboardView.findViewById<TextView>(btnId) ?: return@forEach
            if (abs(currentDp - dpVal) <= 12) {
                btn.setBackgroundResource(R.drawable.variant_item_selected)
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
            val newMode = if (preferences.longPressMode == KeyboardPreferences.MODE_VARIANTS) {
                KeyboardPreferences.MODE_RAPID_FIRE
            } else {
                KeyboardPreferences.MODE_VARIANTS
            }
            preferences.longPressMode = newMode
            updateModeToggleButton()

            val msg = if (newMode == KeyboardPreferences.MODE_VARIANTS) {
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
            btnModeToggle.setTextColor(ContextCompat.getColor(this, R.color.lavender_blue))
        } else {
            btnModeToggle.text = "REP"
            btnModeToggle.setTextColor(ContextCompat.getColor(this, R.color.risk_warn))
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

    private fun handleKeyTouch(view: View, event: MotionEvent, buttonId: Int) {
        val currentMap = if (isSymbols) symbolsMap else keyMap
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
            }
        )
    }

    private fun handleKeyWithVariantsTouch(view: View, event: MotionEvent, keyCode: Int, keyChar: String) {
        processKeyGesture(
            view = view,
            event = event,
            keyCode = keyCode,
            keyChar = keyChar,
            variantsKey = keyChar,
            commitAction = {
                currentInputConnection?.commitText(keyChar, 1)
            }
        )
    }

    private fun handleSpecialKeyTouch(
        view: View,
        event: MotionEvent,
        keyCode: Int,
        keyLabel: String,
        allowRepeat: Boolean,
        commitAction: () -> Unit
    ) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                cancelAllRunnables()
                eventBuffer.add(KeystrokeEvent(keyCode, 0, currentTime))

                if (allowRepeat) {
                    val repeatInterval = preferences.repeatIntervalMs
                    val delay = preferences.longPressDelayMs
                    activeLongPressRunnable = Runnable {
                        isRapidFiring = true
                        activeRepeatRunnable = object : Runnable {
                            override fun run() {
                                commitAction()
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

    private fun handleDeleteKeyTouch(view: View, event: MotionEvent) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                cancelAllRunnables()
                currentInputConnection?.deleteSurroundingText(1, 0)
                eventBuffer.add(KeystrokeEvent(KeyEvent.KEYCODE_DEL, 0, currentTime))

                // Delete key ALWAYS supports rapid-fire auto-repeat!
                val delay = preferences.longPressDelayMs
                val repeatInterval = preferences.repeatIntervalMs
                activeLongPressRunnable = Runnable {
                    isRapidFiring = true
                    activeRepeatRunnable = object : Runnable {
                        override fun run() {
                            currentInputConnection?.deleteSurroundingText(1, 0)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
        commitAction: () -> Unit
    ) {
        val currentTime = event.eventTime.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                cancelAllRunnables()
                isLongPressActive = false
                isRapidFiring = false

                initialTouchX = event.x
                initialTouchY = event.y

                eventBuffer.add(KeystrokeEvent(keyCode, 0, currentTime))

                val delay = preferences.longPressDelayMs
                val mode = preferences.longPressMode
                val variants = CharacterVariants.getVariants(variantsKey, isShifted)

                activeLongPressRunnable = Runnable {
                    isLongPressActive = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                    if (mode == KeyboardPreferences.MODE_RAPID_FIRE || variants.isEmpty()) {
                        // Rapid-fire key repeat
                        isRapidFiring = true
                        val repeatInterval = preferences.repeatIntervalMs
                        activeRepeatRunnable = object : Runnable {
                            override fun run() {
                                commitAction()
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelAllRunnables()
                view.isPressed = false

                if (isVariantsPopupShowing()) {
                    val chosen = getSelectedVariant()
                    hideVariantsPopup()
                    if (chosen != null) {
                        currentInputConnection?.commitText(chosen, 1)
                    } else {
                        commitAction()
                    }
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (isShifted) {
                        isShifted = false
                        updateShiftKey(keyboardView)
                    }
                } else if (isRapidFiring) {
                    isRapidFiring = false
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (isShifted) {
                        isShifted = false
                        updateShiftKey(keyboardView)
                    }
                } else {
                    // Normal tap
                    commitAction()
                    eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                    checkAndFlush()

                    if (isShifted) {
                        isShifted = false
                        updateShiftKey(keyboardView)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAllRunnables()
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
        return ::keyboardOverlayContainer.isInitialized && keyboardOverlayContainer.visibility == View.VISIBLE
    }

    private fun showVariantsPopup(anchorKey: View, variants: List<String>) {
        if (!::keyboardOverlayContainer.isInitialized || variants.isEmpty()) return

        currentVariantsList = variants
        variantsPopupCard.removeAllViews()

        val density = resources.displayMetrics.density
        val itemMinWidth = (38 * density).toInt()
        val itemHeight = (46 * density).toInt()

        // Build variant item views
        variants.forEachIndexed { index, variantStr ->
            val tv = TextView(this).apply {
                text = variantStr
                textSize = 20f
                gravity = Gravity.CENTER
                minWidth = itemMinWidth
                height = itemHeight
                setTextColor(ContextCompat.getColor(this@KeystrokeIMEService, R.color.key_text))
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
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
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
            val childCenterX = if (child.width > 0) {
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
        val onPrimaryColor = ContextCompat.getColor(this, R.color.key_accent_text)
        val defaultTextColor = ContextCompat.getColor(this, R.color.key_text)

        for (i in 0 until count) {
            val child = variantsPopupCard.getChildAt(i) as? TextView ?: continue
            if (i == index) {
                child.setBackgroundResource(R.drawable.variant_item_selected)
                child.setTextColor(onPrimaryColor)
            } else {
                child.background = null
                child.setTextColor(defaultTextColor)
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
        if (::keyboardOverlayContainer.isInitialized) {
            keyboardOverlayContainer.visibility = View.GONE
        }
        isLongPressActive = false
    }

    // =========================================================================
    // KEYBOARD LABELS & SHIFT MANAGEMENT
    // =========================================================================

    private fun updateShiftKey(view: View) {
        val shiftButton = view.findViewById<TextView>(R.id.key_shift) ?: return
        if (isShifted) {
            shiftButton.setBackgroundResource(R.drawable.key_background_accent)
            shiftButton.setTextColor(ContextCompat.getColor(this, R.color.key_accent_text))
            shiftButton.text = "⬆"
        } else {
            shiftButton.setBackgroundResource(R.drawable.key_background_special)
            shiftButton.setTextColor(ContextCompat.getColor(this, R.color.key_text_special))
            shiftButton.text = "⇧"
        }
        updateKeyboardLabels(view)
    }

    private fun updateKeyboardLabels(view: View) {
        val currentMap = if (isSymbols) symbolsMap else keyMap
        val showHints = preferences.showKeyHints

        currentMap.forEach { (buttonId, keyData) ->
            val button = view.findViewById<TextView>(buttonId) ?: return@forEach
            val baseChar = if (isShifted && !isSymbols) keyData.second.uppercase() else keyData.second
            button.text = CharacterVariants.formatKeyLabel(baseChar, isSymbols, showHints, this)
        }

        val symbolsButton = view.findViewById<TextView>(R.id.key_symbols)
        symbolsButton?.text = if (isSymbols) "ABC" else "123"
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
        
        val intent = Intent(MainActivity.ACTION_ENROLLMENT_UPDATE).apply {
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
        
        val colorRes = when (decision.action) {
            Action.ALLOW -> R.color.risk_allow
            Action.WARN -> R.color.risk_warn
            Action.STEP_UP_AUTH -> R.color.risk_step_up
            Action.BLOCK_SESSION -> R.color.risk_block
        }
        setIndicatorColor(colorRes)
        
        // Log event
        val payload = mapOf(
            "composite" to decision.compositeScore.toString(),
            "ocsvm" to decision.ocsvmScore.toString(),
            "mahal" to decision.mahalScore.toString(),
            "threshold" to decision.thresholdUsed.toString()
        )
        securityLogger.log(
            userId = currentUserId,
            eventType = "AUTH_EVALUATION",
            payload = com.google.gson.Gson().toJson(payload),
            riskScore = decision.compositeScore,
            action = decision.action.displayName
        )
        
        // Broadcast to Activity
        val profile = profileManager.getProfile(currentUserId)
        val intent = Intent(MainActivity.ACTION_RISK_UPDATE).apply {
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
            val stepUpIntent = Intent(this, StepUpAuthActivity::class.java).apply {
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
                val intent = Intent(context, BlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    private fun setIndicatorColor(colorRes: Int) {
        val bg = statusIndicator.background
        if (bg is GradientDrawable) {
            bg.setColor(ContextCompat.getColor(this, colorRes))
        }
    }
}