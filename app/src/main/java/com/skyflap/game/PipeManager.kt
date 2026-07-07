package com.skyflap.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.random.Random

class Pipe {
    var x = 0f
    var gapCenter = 0f
    var scored = false
    var oscillates = false     // late-game pipes drift up and down
    var oscPhase = 0f
    var baseGapCenter = 0f
}

/**
 * Spawns, moves, recycles and collision-tests pipes.
 * Difficulty ramps with score: pipes speed up, the gap narrows and,
 * past score 30, some pipes slowly oscillate vertically.
 */
class PipeManager(screenW: Int, screenH: Int) {

    val pipes = ArrayList<Pipe>(6)
    private val pool = ArrayList<Pipe>(6)

    private var w = 0f
    private var h = 0f
    private var groundY = 0f
    var pipeWidth = 0f
        private set
    private var spawnTimer = 0f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 82, 31)
        style = Paint.Style.STROKE
    }
    private val rect = RectF()

    init {
        onResize(screenW, screenH)
    }

    fun onResize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        groundY = h * GameEngine.GROUND_FRACTION
        pipeWidth = w * 0.16f
        outline.strokeWidth = h * 0.004f
        bodyPaint.shader = LinearGradient(
            0f, 0f, pipeWidth, 0f,
            intArrayOf(Color.rgb(96, 176, 66), Color.rgb(140, 214, 96), Color.rgb(76, 150, 52)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        capPaint.shader = LinearGradient(
            0f, 0f, pipeWidth * 1.14f, 0f,
            intArrayOf(Color.rgb(106, 190, 74), Color.rgb(152, 226, 106), Color.rgb(84, 160, 58)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
    }

    fun reset() {
        pool.addAll(pipes)
        pipes.clear()
        spawnTimer = 0.8f
    }

    private fun speed(score: Int): Float =
        h * (0.34f + 0.006f * score.coerceAtMost(40))          // caps at ~1.7x base

    private fun gapSize(score: Int): Float =
        h * (0.30f - 0.0022f * score.coerceAtMost(45))         // caps at ~0.20h

    private fun spawnInterval(score: Int): Float =
        (w * 0.62f) / speed(score)                             // constant horizontal spacing

    fun update(dt: Float, score: Int) {
        val v = speed(score)
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawn(score)
            spawnTimer = spawnInterval(score)
        }
        val it = pipes.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x -= v * dt
            if (p.oscillates) {
                p.oscPhase += dt * 1.6f
                val amp = h * 0.045f
                p.gapCenter = p.baseGapCenter + kotlin.math.sin(p.oscPhase) * amp
            }
            if (p.x + pipeWidth < -w * 0.05f) {
                it.remove()
                pool.add(p)
            }
        }
    }

    private fun spawn(score: Int) {
        val p = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else Pipe()
        val gap = gapSize(score)
        val margin = h * 0.09f
        val min = margin + gap / 2f
        val max = groundY - margin - gap / 2f
        p.x = w * 1.02f
        p.baseGapCenter = min + Random.nextFloat() * (max - min)
        p.gapCenter = p.baseGapCenter
        p.scored = false
        p.oscillates = score >= 30 && Random.nextFloat() < 0.35f
        p.oscPhase = Random.nextFloat() * 6.28f
        pipes.add(p)
    }

    /** Returns true on collision. Collision circle is slightly forgiving. */
    fun collides(bird: Bird, score: Int): Boolean {
        val r = bird.radius * 0.85f
        val gap = gapSize(score)
        for (p in pipes) {
            if (bird.x + r < p.x || bird.x - r > p.x + pipeWidth) continue
            val gapTop = p.gapCenter - gap / 2f
            val gapBottom = p.gapCenter + gap / 2f
            if (bird.y - r < gapTop || bird.y + r > gapBottom) return true
        }
        return false
    }

    /**
     * Scoring result for this frame:
     * 0 = nothing, 1 = normal point, 2 = near-miss point (skimmed a pipe edge).
     */
    fun checkScore(bird: Bird, score: Int): Int {
        val gap = gapSize(score)
        for (p in pipes) {
            if (!p.scored && p.x + pipeWidth < bird.x) {
                p.scored = true
                val edgeDist = gap / 2f - abs(bird.y - p.gapCenter) - bird.radius
                return if (edgeDist < bird.radius * 0.9f) 2 else 1
            }
        }
        return 0
    }

    fun draw(canvas: Canvas, score: Int) {
        val gap = gapSize(score)
        val capH = h * 0.032f
        val capOver = pipeWidth * 0.07f
        for (p in pipes) {
            val gapTop = p.gapCenter - gap / 2f
            val gapBottom = p.gapCenter + gap / 2f

            canvas.save()
            canvas.translate(p.x, 0f)

            // Top pipe
            rect.set(0f, -h * 0.02f, pipeWidth, gapTop - capH)
            canvas.drawRect(rect, bodyPaint)
            canvas.drawRect(rect, outline)
            rect.set(-capOver, gapTop - capH, pipeWidth + capOver, gapTop)
            canvas.drawRoundRect(rect, capH * 0.2f, capH * 0.2f, capPaint)
            canvas.drawRoundRect(rect, capH * 0.2f, capH * 0.2f, outline)

            // Bottom pipe
            rect.set(0f, gapBottom + capH, pipeWidth, groundY + h * 0.02f)
            canvas.drawRect(rect, bodyPaint)
            canvas.drawRect(rect, outline)
            rect.set(-capOver, gapBottom, pipeWidth + capOver, gapBottom + capH)
            canvas.drawRoundRect(rect, capH * 0.2f, capH * 0.2f, capPaint)
            canvas.drawRoundRect(rect, capH * 0.2f, capH * 0.2f, outline)

            canvas.restore()
        }
    }
}
