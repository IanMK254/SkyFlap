package com.skyflap.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

/**
 * The player. All physics constants are expressed as fractions of screen
 * height so the game feels identical on every device.
 */
class Bird(screenW: Int, screenH: Int) {

    var x = 0f
    var y = 0f
    var velocity = 0f
    var radius = 0f
        private set

    private var gravity = 0f
    private var flapImpulse = 0f
    private var maxFall = 0f
    private var rotation = 0f
    private var wingPhase = 0f
    private var flapAnim = 0f
    private var screenH = 0f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 211, 78) }
    private val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 236, 160) }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 185, 33) }
    private val eyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val eyeBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 99, 58) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 60, 0)
        style = Paint.Style.STROKE
    }
    private val beakPath = Path()
    private val wingRect = RectF()

    init {
        onResize(screenW, screenH)
        reset()
    }

    fun onResize(w: Int, h: Int) {
        screenH = h.toFloat()
        radius = h * 0.026f
        gravity = h * 2.0f
        flapImpulse = -h * 0.72f
        maxFall = h * 1.35f
        x = w * 0.30f
        outline.strokeWidth = radius * 0.12f
    }

    fun reset() {
        y = screenH * 0.45f
        velocity = 0f
        rotation = 0f
        flapAnim = 0f
    }

    fun flap() {
        velocity = flapImpulse
        flapAnim = 1f
    }

    /** Physics while playing. */
    fun update(dt: Float) {
        velocity = (velocity + gravity * dt).coerceAtMost(maxFall)
        y += velocity * dt
        // Nose up right after a flap, nose dive when falling fast.
        val target = when {
            velocity < 0 -> -25f
            else -> (velocity / maxFall * 90f).coerceAtMost(90f)
        }
        rotation += (target - rotation) * (8f * dt).coerceAtMost(1f)
        wingPhase += dt * 14f
        flapAnim = (flapAnim - dt * 4f).coerceAtLeast(0f)
    }

    /** Gentle bobbing on the menu / get-ready screens. */
    fun updateIdle(dt: Float, time: Float) {
        y = screenH * 0.45f + sin(time * 3f) * screenH * 0.012f
        rotation = 0f
        wingPhase += dt * 8f
    }

    /** Dead bird tumbles to the ground. */
    fun updateDead(dt: Float, groundY: Float) {
        velocity = (velocity + gravity * dt).coerceAtMost(maxFall)
        y = (y + velocity * dt).coerceAtMost(groundY - radius)
        if (y < groundY - radius) rotation += 480f * dt
        else rotation = 90f
    }

    fun draw(canvas: Canvas) {
        canvas.save()
        canvas.rotate(rotation, x, y)

        // Body
        canvas.drawCircle(x, y, radius, bodyPaint)
        canvas.drawCircle(x - radius * 0.15f, y + radius * 0.35f, radius * 0.62f, bellyPaint)
        canvas.drawCircle(x, y, radius, outline)

        // Wing flaps faster right after a tap
        val flap = sin(wingPhase * (1f + flapAnim * 1.5f))
        val wingY = y + radius * 0.15f + flap * radius * 0.35f
        wingRect.set(x - radius * 0.95f, wingY - radius * 0.38f, x + radius * 0.1f, wingY + radius * 0.38f)
        canvas.drawOval(wingRect, wingPaint)

        // Eye
        canvas.drawCircle(x + radius * 0.42f, y - radius * 0.34f, radius * 0.36f, eyeWhite)
        canvas.drawCircle(x + radius * 0.55f, y - radius * 0.34f, radius * 0.16f, eyeBlack)

        // Beak
        beakPath.reset()
        beakPath.moveTo(x + radius * 0.85f, y + radius * 0.02f)
        beakPath.lineTo(x + radius * 1.55f, y + radius * 0.22f)
        beakPath.lineTo(x + radius * 0.85f, y + radius * 0.48f)
        beakPath.close()
        canvas.drawPath(beakPath, beakPaint)

        canvas.restore()
    }
}
