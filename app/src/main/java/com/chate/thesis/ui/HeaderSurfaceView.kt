package com.chate.thesis.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chate.thesis.HeaderEffect
import com.chate.thesis.R
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

    // 2. Maze effect state
    private data class MazeLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float)
    private val mazeLines = mutableListOf<MazeLine>()
    private var mazeScanY: Float = 0f

    // 3. Circuit effect state
    private data class CircuitNode(val x: Float, val y: Float, val isTerminal: Boolean)
    private data class CircuitTrace(val path: Path, val startNode: CircuitNode, val endNode: CircuitNode, var pulseProgress: Float)
    private val circuitNodes = mutableListOf<CircuitNode>()
    private val circuitTraces = mutableListOf<CircuitTrace>()

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
                mazeLines.clear()
                circuitNodes.clear()
                circuitTraces.clear()
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
        mazeLines.clear()
        mazeScanY = 0f
        val step = 20f * resources.displayMetrics.density
        var x = 0f
        while (x < w) {
            var y = 0f
            while (y < h) {
                if (Random.nextBoolean()) {
                    mazeLines.add(MazeLine(x, y, x + step, y + step))
                } else {
                    mazeLines.add(MazeLine(x, y + step, x + step, y))
                }
                y += step
            }
            x += step
        }
    }

    private fun initCircuit(w: Float, h: Float) {
        circuitNodes.clear()
        circuitTraces.clear()
        val density = resources.displayMetrics.density
        val step = 32f * density
        val cols = (w / step).toInt().coerceAtLeast(4)
        val rows = (h / step).toInt().coerceAtLeast(3)

        val grid = Array(cols) { c ->
            Array(rows) { r ->
                val nx = (c + 0.5f) * step + (Random.nextFloat() - 0.5f) * 8f * density
                val ny = (r + 0.5f) * step + (Random.nextFloat() - 0.5f) * 8f * density
                val isTerminal = Random.nextFloat() < 0.35f
                CircuitNode(nx, ny, isTerminal).also { circuitNodes.add(it) }
            }
        }

        for (c in 0 until cols - 1) {
            for (r in 0 until rows) {
                if (Random.nextFloat() < 0.45f) {
                    val p = Path()
                    val from = grid[c][r]
                    val targetR = if (Random.nextBoolean() && r + 1 < rows) r + 1 else if (r - 1 >= 0) r - 1 else r
                    val to = grid[c + 1][targetR]
                    p.moveTo(from.x, from.y)
                    val midX = (from.x + to.x) / 2f
                    p.lineTo(midX, from.y)
                    p.lineTo(midX, to.y)
                    p.lineTo(to.x, to.y)
                    circuitTraces.add(CircuitTrace(p, from, to, Random.nextFloat()))
                }
            }
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
        val h = height.toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2f * resources.displayMetrics.density

        // Subtle background maze
        paint.color = accentColor
        paint.alpha = 45
        for (line in mazeLines) {
            canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paint)
        }

        // Pulse scanline
        mazeScanY += 1.8f
        if (mazeScanY > h) mazeScanY = 0f

        paint.alpha = 130
        paint.strokeWidth = 3f * resources.displayMetrics.density
        for (line in mazeLines) {
            if (line.startY >= mazeScanY - 24f && line.startY <= mazeScanY + 24f) {
                canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paint)
            }
        }
    }

    private fun drawCircuit(canvas: Canvas) {
        val density = resources.displayMetrics.density
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // Traces
        paint.strokeWidth = 1.5f * density
        paint.color = accentColor
        paint.alpha = 50
        for (trace in circuitTraces) {
            canvas.drawPath(trace.path, paint)
        }

        // Nodes
        paint.style = Paint.Style.FILL
        for (node in circuitNodes) {
            if (node.isTerminal) {
                paint.color = accentColor
                paint.alpha = 90
                canvas.drawCircle(node.x, node.y, 2.5f * density, paint)
            }
        }

        // Animated pulses
        paint.color = Color.WHITE
        paint.alpha = 200
        for (trace in circuitTraces) {
            trace.pulseProgress += 0.012f
            if (trace.pulseProgress > 1f) trace.pulseProgress = 0f

            val px = trace.startNode.x + (trace.endNode.x - trace.startNode.x) * trace.pulseProgress
            val py = trace.startNode.y + (trace.endNode.y - trace.startNode.y) * trace.pulseProgress
            canvas.drawCircle(px, py, 2.8f * density, paint)
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

