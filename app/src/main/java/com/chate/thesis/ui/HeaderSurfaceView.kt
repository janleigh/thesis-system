package com.chate.thesis.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chate.thesis.HeaderEffect
import com.chate.thesis.R
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class HeaderSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentEffect: HeaderEffect = HeaderEffect.NOTHING
    private var isPaused: Boolean = false

    // Color & Paint
    private var accentColor: Int = ContextCompat.getColor(context, R.color.md_theme_primary)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val clipRect = RectF()

    // 1. Snow effect state
    private data class Snowflake(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedY: Float,
        var swayOffset: Float,
        var alpha: Int
    )
    private val snowflakes = mutableListOf<Snowflake>()

    // 2. Pac-Man Maze effect state
    private data class MazeCorridor(val path: Path)
    private data class MazePellet(val x: Float, val y: Float, val isPower: Boolean, var isEaten: Boolean)
    private data class PacManState(
        var x: Float,
        var y: Float,
        var speed: Float,
        var direction: Float,
        var mouthProgress: Float,
        var mouthOpening: Boolean
    )
    private data class GhostState(
        var x: Float,
        var y: Float,
        var color: Int,
        var speed: Float,
        var direction: Float
    )

    private val mazeCorridors = mutableListOf<MazeCorridor>()
    private val mazePellets = mutableListOf<MazePellet>()
    private var pacMan = PacManState(0f, 0f, 2.5f, 1f, 0f, true)
    private var ghost = GhostState(0f, 0f, Color.parseColor("#FF5555"), 2.2f, 1f)
    private val pacRect = RectF()
    private val ghostPath = Path()
    private var mazeAnimTicks = 0

    // 3. Authentic PCB Circuit effect state
    private data class PcbTrace(
        val path: Path,
        val pathMeasure: PathMeasure,
        val length: Float,
        var pulseProgress: Float,
        val pulseSpeed: Float
    )
    private data class PcbVia(
        val x: Float,
        val y: Float,
        val outerRadius: Float,
        val innerRadius: Float
    )
    private data class PcbChip(
        val rect: RectF,
        val label: String,
        val pinsLeft: Int,
        val pinsRight: Int
    )

    private val pcbTraces = mutableListOf<PcbTrace>()
    private val pcbVias = mutableListOf<PcbVia>()
    private val pcbChips = mutableListOf<PcbChip>()
    private val pulsePos = FloatArray(2)
    private val pulseTan = FloatArray(2)

    // 4. Code Rain effect state
    private data class RainColumn(
        val x: Float,
        var y: Float,
        var speed: Float,
        var chars: CharArray,
        var length: Int
    )
    private val rainColumns = mutableListOf<RainColumn>()
    private val glyphPool = "01011010ABCDEF<>/*{}#!=~+;:".toCharArray()

    init {
        paint.style = Paint.Style.STROKE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
    }

    fun setEffect(effect: HeaderEffect) {
        if (currentEffect != effect) {
            currentEffect = effect
            reinitializeCurrentEffect()
            invalidate()
        }
    }

    fun getEffect(): HeaderEffect = currentEffect

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    fun pauseAnimation() {
        isPaused = true
    }

    fun resumeAnimation() {
        if (isPaused) {
            isPaused = false
            if (currentEffect != HeaderEffect.NOTHING && isShown) {
                postInvalidateOnAnimation()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        clipPath.reset()
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val cornerRadius = 32f * resources.displayMetrics.density
        clipPath.addRoundRect(
            clipRect,
            floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius),
            Path.Direction.CW
        )

        reinitializeCurrentEffect()
    }

    private fun reinitializeCurrentEffect() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        when (currentEffect) {
            HeaderEffect.NOTHING -> {
                snowflakes.clear()
                mazeCorridors.clear()
                mazePellets.clear()
                pcbTraces.clear()
                pcbVias.clear()
                pcbChips.clear()
                rainColumns.clear()
            }
            HeaderEffect.SNOW -> initSnow(w, h)
            HeaderEffect.MAZE -> initMaze(w, h)
            HeaderEffect.CIRCUIT -> initCircuit(w, h)
            HeaderEffect.CODE_RAIN -> initCodeRain(w, h)
        }
    }

    // ---------------------------------------------------------
    // Init Effects
    // ---------------------------------------------------------
    private fun initSnow(w: Float, h: Float) {
        snowflakes.clear()
        val count = (w / 12f).toInt().coerceIn(30, 60)
        for (i in 0 until count) {
            snowflakes.add(
                Snowflake(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = Random.nextFloat() * 2.5f + 1.2f,
                    speedY = Random.nextFloat() * 1.5f + 0.8f,
                    swayOffset = Random.nextFloat() * 3.14f * 2f,
                    alpha = Random.nextInt(60, 160)
                )
            )
        }
    }

    private fun initMaze(w: Float, h: Float) {
        mazeCorridors.clear()
        mazePellets.clear()
        val density = resources.displayMetrics.density

        // Setup arcade corridors
        val wallPath = Path()
        val midY = h * 0.52f
        val topY = h * 0.22f
        val botY = h * 0.82f
        val cornerRadius = 6f * density

        // Top horizontal corridor wall with gap
        wallPath.moveTo(0f, topY)
        wallPath.lineTo(w * 0.38f, topY)
        wallPath.moveTo(w * 0.62f, topY)
        wallPath.lineTo(w, topY)

        // Bottom horizontal corridor wall with gap
        wallPath.moveTo(0f, botY)
        wallPath.lineTo(w * 0.38f, botY)
        wallPath.moveTo(w * 0.62f, botY)
        wallPath.lineTo(w, botY)

        // Mid maze obstacle blocks (classic retro rounded rectangles)
        val block1 = RectF(w * 0.12f, h * 0.32f, w * 0.28f, h * 0.44f)
        val block2 = RectF(w * 0.72f, h * 0.32f, w * 0.88f, h * 0.44f)
        val block3 = RectF(w * 0.12f, h * 0.60f, w * 0.28f, h * 0.72f)
        val block4 = RectF(w * 0.72f, h * 0.60f, w * 0.88f, h * 0.72f)
        val centerT = RectF(w * 0.42f, h * 0.34f, w * 0.58f, h * 0.42f)

        wallPath.addRoundRect(block1, cornerRadius, cornerRadius, Path.Direction.CW)
        wallPath.addRoundRect(block2, cornerRadius, cornerRadius, Path.Direction.CW)
        wallPath.addRoundRect(block3, cornerRadius, cornerRadius, Path.Direction.CW)
        wallPath.addRoundRect(block4, cornerRadius, cornerRadius, Path.Direction.CW)
        wallPath.addRoundRect(centerT, cornerRadius, cornerRadius, Path.Direction.CW)

        mazeCorridors.add(MazeCorridor(wallPath))

        // Populate pellets across the main mid corridor and vertical crossways
        val pelletSpacing = 18f * density
        var px = pelletSpacing * 0.8f
        while (px < w) {
            val isPower = (px < pelletSpacing * 2f) || (px > w - pelletSpacing * 2f)
            mazePellets.add(MazePellet(px, midY, isPower, false))
            px += pelletSpacing
        }

        // Additional pellets in upper and lower alleys
        var ux = pelletSpacing * 1.5f
        while (ux < w) {
            mazePellets.add(MazePellet(ux, topY - 10f * density, false, false))
            mazePellets.add(MazePellet(ux, botY + 10f * density, false, false))
            ux += pelletSpacing * 2.5f
        }

        // Initialize Pac-Man & Ghost
        pacMan = PacManState(
            x = -15f * density,
            y = midY,
            speed = 2.4f * density,
            direction = 1f,
            mouthProgress = 0f,
            mouthOpening = true
        )

        ghost = GhostState(
            x = -65f * density,
            y = midY,
            color = Color.parseColor("#FF5252"),
            speed = 2.1f * density,
            direction = 1f
        )
    }

    private fun initCircuit(w: Float, h: Float) {
        pcbTraces.clear()
        pcbVias.clear()
        pcbChips.clear()
        val density = resources.displayMetrics.density

        // 1. SMD IC Chips
        val chip1Rect = RectF(w * 0.14f, h * 0.28f, w * 0.32f, h * 0.62f)
        val chip2Rect = RectF(w * 0.66f, h * 0.36f, w * 0.84f, h * 0.70f)
        pcbChips.add(PcbChip(chip1Rect, "SEC-MCU", 4, 4))
        pcbChips.add(PcbChip(chip2Rect, "BUS-IC", 3, 3))

        // 2. PCB Bus Traces with authentic 45° chamfers
        fun createChamferedTrace(
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            initialHorizontal: Float,
            speed: Float
        ) {
            val path = Path()
            path.moveTo(x1, y1)
            val dy = y2 - y1
            val chamferDx = abs(dy)
            val bx1 = x1 + initialHorizontal
            path.lineTo(bx1, y1)
            val bx2 = bx1 + (if (x2 > x1) chamferDx else -chamferDx)
            path.lineTo(bx2, y2)
            path.lineTo(x2, y2)

            val pm = PathMeasure(path, false)
            val length = pm.length
            if (length > 0f) {
                pcbTraces.add(PcbTrace(path, pm, length, Random.nextFloat(), speed))
            }
        }

        // Chip 1 pin breakout traces
        val c1Top = chip1Rect.top
        val c1Step = chip1Rect.height() / 5f
        for (i in 1..4) {
            val py = c1Top + i * c1Step
            // Right breakout traces toward Chip 2 and vias
            val targetY = h * 0.18f + i * 22f * density
            createChamferedTrace(
                chip1Rect.right, py,
                w * 0.62f, targetY,
                16f * density + i * 4f * density,
                0.007f + (i * 0.0015f)
            )
            // Left breakout traces to edge vias
            createChamferedTrace(
                chip1Rect.left, py,
                18f * density, py + (if (i % 2 == 0) 18f * density else -18f * density),
                -(14f * density + i * 3f * density),
                0.008f + (i * 0.0012f)
            )
        }

        // Chip 2 breakout traces
        val c2Top = chip2Rect.top
        val c2Step = chip2Rect.height() / 4f
        for (i in 1..3) {
            val py = c2Top + i * c2Step
            createChamferedTrace(
                chip2Rect.right, py,
                w - 18f * density, py + (if (i % 2 == 0) -20f * density else 20f * density),
                15f * density + i * 5f * density,
                0.0075f + (i * 0.001f)
            )
        }

        // Top & bottom horizontal power/data buses
        val busY1 = h * 0.12f
        val busY2 = h * 0.88f
        val busPath1 = Path().apply {
            moveTo(0f, busY1)
            lineTo(w * 0.45f, busY1)
            lineTo(w * 0.45f + 16f * density, busY1 + 16f * density)
            lineTo(w, busY1 + 16f * density)
        }
        val pm1 = PathMeasure(busPath1, false)
        pcbTraces.add(PcbTrace(busPath1, pm1, pm1.length, 0.2f, 0.008f))

        val busPath2 = Path().apply {
            moveTo(w, busY2)
            lineTo(w * 0.55f, busY2)
            lineTo(w * 0.55f - 14f * density, busY2 - 14f * density)
            lineTo(0f, busY2 - 14f * density)
        }
        val pm2 = PathMeasure(busPath2, false)
        pcbTraces.add(PcbTrace(busPath2, pm2, pm2.length, 0.65f, 0.009f))

        // 3. Circular Solder Vias (terminal pads)
        val viaOuter = 3.6f * density
        val viaInner = 1.6f * density
        val viaCoords = floatArrayOf(
            18f * density, h * 0.22f,
            18f * density, h * 0.78f,
            w * 0.48f, h * 0.16f,
            w * 0.52f, h * 0.84f,
            w * 0.62f, h * 0.25f,
            w * 0.62f, h * 0.72f,
            w - 18f * density, h * 0.28f,
            w - 18f * density, h * 0.68f
        )
        for (i in viaCoords.indices step 2) {
            pcbVias.add(PcbVia(viaCoords[i], viaCoords[i + 1], viaOuter, viaInner))
        }
    }

    private fun initCodeRain(w: Float, h: Float) {
        rainColumns.clear()
        val charSize = 13f * resources.displayMetrics.density
        textPaint.textSize = charSize
        val numCols = (w / (charSize * 1.3f)).toInt().coerceIn(12, 45)
        for (i in 0 until numCols) {
            val length = Random.nextInt(6, 16)
            val chars = CharArray(length) { glyphPool[Random.nextInt(glyphPool.size)] }
            rainColumns.add(
                RainColumn(
                    x = i * charSize * 1.3f + 8f,
                    y = Random.nextFloat() * h,
                    speed = Random.nextFloat() * 2.2f + 1.2f,
                    chars = chars,
                    length = length
                )
            )
        }
    }

    // ---------------------------------------------------------
    // Draw & Animate
    // ---------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        if (currentEffect == HeaderEffect.NOTHING) {
            return
        }

        canvas.save()
        canvas.clipPath(clipPath)

        when (currentEffect) {
            HeaderEffect.SNOW -> drawSnow(canvas)
            HeaderEffect.MAZE -> drawMaze(canvas)
            HeaderEffect.CIRCUIT -> drawCircuit(canvas)
            HeaderEffect.CODE_RAIN -> drawCodeRain(canvas)
            HeaderEffect.NOTHING -> {}
        }

        canvas.restore()

        if (!isPaused && isShown) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawSnow(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        paint.style = Paint.Style.FILL

        for (flake in snowflakes) {
            flake.y += flake.speedY
            flake.swayOffset += 0.03f
            val curX = flake.x + sin(flake.swayOffset.toDouble()).toFloat() * 4f
            if (flake.y > h + 10f) {
                flake.y = -10f
                flake.x = Random.nextFloat() * w
            }
            paint.color = Color.WHITE
            paint.alpha = flake.alpha
            canvas.drawCircle(curX, flake.y, flake.radius, paint)
        }
    }

    private fun drawMaze(canvas: Canvas) {
        val w = width.toFloat()
        val density = resources.displayMetrics.density
        mazeAnimTicks++

        // 1. Draw Arcade Corridors
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 2.4f * density
        paint.color = accentColor
        paint.alpha = 75
        for (corridor in mazeCorridors) {
            canvas.drawPath(corridor.path, paint)
        }

        // 2. Draw Pellets
        paint.style = Paint.Style.FILL
        for (pellet in mazePellets) {
            if (pellet.isEaten) continue
            if (pellet.isPower) {
                // Pulsing power pellet
                val pulseRadius = 3.6f * density + sin(mazeAnimTicks * 0.16f) * 1.2f * density
                paint.color = Color.parseColor("#FFF59D")
                paint.alpha = 240
                canvas.drawCircle(pellet.x, pellet.y, pulseRadius, paint)
            } else {
                paint.color = Color.parseColor("#FFE082")
                paint.alpha = 175
                canvas.drawCircle(pellet.x, pellet.y, 1.8f * density, paint)
            }
        }

        // 3. Update & Draw Pac-Man
        pacMan.x += pacMan.speed * pacMan.direction
        val wrapMargin = 28f * density
        if (pacMan.x > w + wrapMargin) {
            pacMan.x = -wrapMargin
            // Respawn all pellets when wrapping screen tunnel
            for (p in mazePellets) {
                p.isEaten = false
            }
        }

        // Eat pellets near mouth
        val eatRadius = 13f * density
        for (pellet in mazePellets) {
            if (!pellet.isEaten && abs(pellet.x - pacMan.x) < eatRadius && abs(pellet.y - pacMan.y) < eatRadius) {
                pellet.isEaten = true
            }
        }

        // Animated mouth chomping
        pacMan.mouthProgress += 0.24f
        val mouthAngle = abs(sin(pacMan.mouthProgress)) * 42f
        val pacRadius = 9f * density
        pacRect.set(pacMan.x - pacRadius, pacMan.y - pacRadius, pacMan.x + pacRadius, pacMan.y + pacRadius)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFD600")
        paint.alpha = 240
        val startAngle = if (pacMan.direction >= 0f) mouthAngle else 180f + mouthAngle
        val sweepAngle = 360f - (mouthAngle * 2f)
        canvas.drawArc(pacRect, startAngle, sweepAngle, true, paint)

        // Pac-Man eye
        paint.color = Color.parseColor("#212121")
        paint.alpha = 240
        val eyeX = pacMan.x + (if (pacMan.direction >= 0f) 1.5f * density else -1.5f * density)
        val eyeY = pacMan.y - 4.5f * density
        canvas.drawCircle(eyeX, eyeY, 1.2f * density, paint)

        // 4. Update & Draw Ghost (Blinky trailing Pac-Man)
        ghost.x += ghost.speed * ghost.direction
        if (ghost.x > w + wrapMargin) {
            ghost.x = -wrapMargin - 35f * density
        }

        val ghostW = 16f * density
        val ghostH = 17f * density
        val gx = ghost.x - ghostW / 2f
        val gy = ghost.y - ghostH / 2f

        ghostPath.reset()
        // Top dome head
        ghostPath.moveTo(gx, gy + ghostW / 2f)
        val headRect = RectF(gx, gy, gx + ghostW, gy + ghostW)
        ghostPath.arcTo(headRect, 180f, 180f, false)
        // Right side down
        ghostPath.lineTo(gx + ghostW, gy + ghostH)
        // Wavy skirt at bottom
        val waveW = ghostW / 3f
        ghostPath.lineTo(gx + ghostW - (waveW * 0.5f), gy + ghostH - (3f * density))
        ghostPath.lineTo(gx + ghostW - waveW, gy + ghostH)
        ghostPath.lineTo(gx + (waveW * 1.5f), gy + ghostH - (3f * density))
        ghostPath.lineTo(gx + waveW, gy + ghostH)
        ghostPath.lineTo(gx + (waveW * 0.5f), gy + ghostH - (3f * density))
        ghostPath.lineTo(gx, gy + ghostH)
        ghostPath.close()

        paint.style = Paint.Style.FILL
        paint.color = ghost.color
        paint.alpha = 230
        canvas.drawPath(ghostPath, paint)

        // Ghost eyes (white sclera)
        paint.color = Color.WHITE
        paint.alpha = 255
        val leftEyeX = gx + 4.5f * density
        val rightEyeX = gx + 11.5f * density
        val eyesY = gy + 6f * density
        canvas.drawCircle(leftEyeX, eyesY, 2.3f * density, paint)
        canvas.drawCircle(rightEyeX, eyesY, 2.3f * density, paint)

        // Ghost pupils (blue, glancing toward Pac-Man)
        paint.color = Color.parseColor("#1565C0")
        val lookOffset = 1.0f * density
        canvas.drawCircle(leftEyeX + lookOffset, eyesY, 1.2f * density, paint)
        canvas.drawCircle(rightEyeX + lookOffset, eyesY, 1.2f * density, paint)
    }

    private fun drawCircuit(canvas: Canvas) {
        val density = resources.displayMetrics.density

        // 1. PCB Traces
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.6f * density
        paint.color = accentColor
        paint.alpha = 70
        for (trace in pcbTraces) {
            canvas.drawPath(trace.path, paint)
        }

        // 2. SMD IC Chips
        for (chip in pcbChips) {
            // Chip body
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F1418")
            paint.alpha = 190
            canvas.drawRoundRect(chip.rect, 4f * density, 4f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = accentColor
            paint.alpha = 110
            canvas.drawRoundRect(chip.rect, 4f * density, 4f * density, paint)

            // Pin indicator / notch dot
            paint.style = Paint.Style.FILL
            paint.color = accentColor
            paint.alpha = 160
            canvas.drawCircle(chip.rect.left + 5f * density, chip.rect.top + 5f * density, 1.4f * density, paint)

            // Chip label
            textPaint.textSize = 7.5f * density
            textPaint.color = accentColor
            textPaint.alpha = 140
            canvas.drawText(chip.label, chip.rect.centerX(), chip.rect.centerY() + 2.5f * density, textPaint)

            // Side pins connecting to traces
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.parseColor("#CFD8DC")
            paint.alpha = 140
            val cStep = chip.rect.height() / (chip.pinsLeft + 1)
            for (i in 1..chip.pinsLeft) {
                val py = chip.rect.top + i * cStep
                canvas.drawLine(chip.rect.left - 3.5f * density, py, chip.rect.left, py, paint)
                canvas.drawLine(chip.rect.right, py, chip.rect.right + 3.5f * density, py, paint)
            }
        }

        // 3. Circular Solder Vias
        for (via in pcbVias) {
            // Outer copper pad
            paint.style = Paint.Style.FILL
            paint.color = accentColor
            paint.alpha = 150
            canvas.drawCircle(via.x, via.y, via.outerRadius, paint)

            // Inner dark drill hole through the PCB
            paint.color = Color.parseColor("#090D0F")
            paint.alpha = 240
            canvas.drawCircle(via.x, via.y, via.innerRadius, paint)
        }

        // 4. Animated Data Packets / Electric Current Pulses
        paint.style = Paint.Style.FILL
        for (trace in pcbTraces) {
            trace.pulseProgress += trace.pulseSpeed
            if (trace.pulseProgress > 1f) trace.pulseProgress = 0f

            trace.pathMeasure.getPosTan(trace.pulseProgress * trace.length, pulsePos, pulseTan)

            // Soft glow halo
            paint.color = accentColor
            paint.alpha = 100
            canvas.drawCircle(pulsePos[0], pulsePos[1], 4.2f * density, paint)

            // Bright core spark
            paint.color = Color.WHITE
            paint.alpha = 245
            canvas.drawCircle(pulsePos[0], pulsePos[1], 2.2f * density, paint)
        }
    }

    private fun drawCodeRain(canvas: Canvas) {
        val h = height.toFloat()
        val charStep = 15f * resources.displayMetrics.density

        for (col in rainColumns) {
            col.y += col.speed
            if (col.y - col.length * charStep > h) {
                col.y = -charStep
                col.speed = Random.nextFloat() * 2.2f + 1.2f
            }

            for (i in 0 until col.length) {
                val cy = col.y - (i * charStep)
                if (cy < 0 || cy > h + charStep) continue

                if (i == 0) {
                    textPaint.color = Color.WHITE
                    textPaint.alpha = 240
                } else {
                    textPaint.color = accentColor
                    val fadeAlpha = ((1f - (i.toFloat() / col.length.toFloat())) * 140).toInt().coerceIn(20, 160)
                    textPaint.alpha = fadeAlpha
                }

                if (Random.nextFloat() < 0.02f) {
                    col.chars[i] = glyphPool[Random.nextInt(glyphPool.size)]
                }

                canvas.drawText(col.chars[i].toString(), col.x, cy, textPaint)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (currentEffect != HeaderEffect.NOTHING && !isPaused) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isPaused = true
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && !isPaused && currentEffect != HeaderEffect.NOTHING) {
            postInvalidateOnAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && !isPaused && currentEffect != HeaderEffect.NOTHING) {
            postInvalidateOnAnimation()
        }
    }
}

