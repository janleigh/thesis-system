package com.chate.thesis

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class KeystrokeIMEService : InputMethodService() {

    private val eventBuffer = mutableListOf<KeystrokeEvent>()
    private val featureExtractor = FeatureExtractor()
    private var isShifted = false
    private var isSymbols = false
    private lateinit var keyboardView: View

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

    companion object {
        const val ACTION_SET_MODE = "com.chate.thesis.SET_MODE"
        const val ACTION_STEP_UP_RESULT = "com.chate.thesis.STEP_UP_RESULT"
    }

    private val modeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == ACTION_SET_MODE) {
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
                        riskScorer.resetHistory() // reset scoring history for new session
                    } else {
                        enrollmentText.text = ""
                        statusText.text = "IDLE"
                        setIndicatorColor(R.color.text_secondary)
                        scoreText.text = ""
                    }
                }
                eventBuffer.clear()
            } else if (intent.action == ACTION_STEP_UP_RESULT) {
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
        profileManager = ProfileManager(this, testingMode = true)
        riskScorer = RiskScorer()
        securityLogger = SecurityLogger(this)
        
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_MODE)
            addAction(ACTION_STEP_UP_RESULT)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(modeReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(modeReceiver)
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        // Init UI elements
        statusBar = keyboardView.findViewById(R.id.status_bar)
        statusIndicator = keyboardView.findViewById(R.id.status_indicator)
        statusText = keyboardView.findViewById(R.id.status_text)
        scoreText = keyboardView.findViewById(R.id.score_text)
        enrollmentText = keyboardView.findViewById(R.id.enrollment_text)

        val touchListener = View.OnTouchListener { v, event ->
            handleTouch(v, event, v.id)
            true
        }

        // Wire up all letter keys
        keyMap.keys.forEach { buttonId ->
            keyboardView.findViewById<TextView>(buttonId)?.setOnTouchListener(touchListener)
        }

        // Space bar
        keyboardView.findViewById<TextView>(R.id.key_space).setOnTouchListener { v, event ->
            handleSpecialTouch(v, event, KeyEvent.KEYCODE_SPACE, " ") {
                currentInputConnection?.commitText(" ", 1)
            }
            true
        }

        // Delete key
        keyboardView.findViewById<TextView>(R.id.key_delete).setOnTouchListener { v, event ->
            handleSpecialTouch(v, event, KeyEvent.KEYCODE_DEL, "DEL") {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            true
        }

        // Enter key
        keyboardView.findViewById<TextView>(R.id.key_enter).setOnTouchListener { v, event ->
            handleSpecialTouch(v, event, KeyEvent.KEYCODE_ENTER, "ENTER") {
                currentInputConnection?.commitText("\n", 1)
            }
            true
        }

        // Comma key
        keyboardView.findViewById<TextView>(R.id.key_comma).setOnTouchListener { v, event ->
            handleSpecialTouch(v, event, KeyEvent.KEYCODE_COMMA, ",") {
                currentInputConnection?.commitText(",", 1)
            }
            true
        }

        // Period key
        keyboardView.findViewById<TextView>(R.id.key_period).setOnTouchListener { v, event ->
            handleSpecialTouch(v, event, KeyEvent.KEYCODE_PERIOD, ".") {
                currentInputConnection?.commitText(".", 1)
            }
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

    private fun updateShiftKey(view: View) {
        val shiftButton = view.findViewById<TextView>(R.id.key_shift)
        shiftButton.setTextColor(if (isShifted) android.graphics.Color.CYAN else android.graphics.Color.WHITE)
        updateKeyboardLabels(view)
    }

    private fun updateKeyboardLabels(view: View) {
        val currentMap = if (isSymbols) symbolsMap else keyMap
        currentMap.forEach { (buttonId, keyData) ->
            val button = view.findViewById<TextView>(buttonId)
            button.text = if (isShifted && !isSymbols) keyData.second.uppercase() else keyData.second
        }
        val symbolsButton = view.findViewById<TextView>(R.id.key_symbols)
        symbolsButton.text = if (isSymbols) "ABC" else "?123"
    }

    private fun handleTouch(view: View, event: MotionEvent, buttonId: Int) {
        val currentTime = event.eventTime.toDouble()
        val currentMap = if (isSymbols) symbolsMap else keyMap
        val keyData = currentMap[buttonId] ?: Pair(0, "")
        val actualKeyCode = keyData.first
        val actualKeyLabel = if (isShifted && !isSymbols) keyData.second.uppercase() else keyData.second

        processMotionEvent(view, event, actualKeyCode, actualKeyLabel, currentTime) {
            currentInputConnection?.commitText(actualKeyLabel, 1)
        }
    }

    private fun handleSpecialTouch(view: View, event: MotionEvent, keyCode: Int, keyLabel: String, commitAction: () -> Unit) {
        val currentTime = event.eventTime.toDouble()
        processMotionEvent(view, event, keyCode, keyLabel, currentTime, commitAction)
    }

    private fun processMotionEvent(view: View, event: MotionEvent, keyCode: Int, keyLabel: String, currentTime: Double, commitAction: () -> Unit) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                eventBuffer.add(KeystrokeEvent(keyCode, 0, currentTime))
            }
            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                commitAction()
                
                eventBuffer.add(KeystrokeEvent(keyCode, 1, currentTime))
                
                checkAndFlush()

                if (isShifted) {
                    isShifted = false
                    updateShiftKey(keyboardView)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
            }
        }
    }

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
        
        // Broadcast update to Activity
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