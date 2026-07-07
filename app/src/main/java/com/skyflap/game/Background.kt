package com.skyflap.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Parallax scenery with a day/night cycle.
 * The sky slowly blends from day to night and back as the score climbs
 * (a full cycle every 20 points), with stars fading in at night.
 */
class Background(screenW: Int, screenH: Int) {

    private var w = 0f
    private var h = 0f
    private var groundY = 0f

    private var cloudOffset = 0f
    private var hillOffset = 0f
    private var groundOffset = 0f
    private var starTwinkle = 0f

    private val skyPaint = Paint()
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillPaintFar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundPaint = Paint()
    private val grassPaint = Paint()
    private val stripePaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hillPath = Path()

    // Day / night palettes
    private val dayTop = Color.rgb(78, 192, 202)
    private val dayBottom = Color.rgb(178, 229, 234)
    private val nightTop = Color.rgb(18, 24, 58)
    private val nightBottom = Color.rgb(64, 74, 122)

    private data class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

    private val stars = ArrayList<Star>()

    init {
        onResize(screenW, screenH)
    }

    fun onResize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        groundY = h * GameEngine.GROUND_FRACTION
        stars.clear()
        val rnd = Random(42)
        repeat(40) {
            stars.add(
                Star(
                    rnd.nextFloat() * w,
                    rnd.nextFloat() * groundY * 0.7f,
                    h * (0.0012f + rnd.nextFloat() * 0.002f),
                    rnd.nextFloat() * 6.28f
                )
            )
        }
        setNightness(0f)
    }

    /** 0 = full day, 1 = full night. */
    private var nightness = 0f

    fun setNightness(n: Float) {
        nightness = n.coerceIn(0f, 1f)
        val top = blend(dayTop, nightTop, nightness)
        val bottom = blend(dayBottom, nightBottom, nightness)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, groundY, top, bottom, Shader.TileMode.CLAMP
        )
        hillPaintFar.color = blend(Color.rgb(148, 206, 154), Color.rgb(44, 56, 92), nightness)
        hillPaint.color = blend(Color.rgb(110, 184, 118), Color.rgb(34, 44, 76), nightness)
        cloudPaint.color = blend(Color.argb(235, 255, 255, 255), Color.argb(120, 170, 180, 215), nightness)
        groundPaint.color = blend(Color.rgb(222, 184, 121), Color.rgb(96, 82, 66), nightness)
        grassPaint.color = blend(Color.rgb(120, 200, 84), Color.rgb(52, 84, 52), nightness)
        stripePaint.color = blend(Color.rgb(104, 176, 72), Color.rgb(44, 72, 44), nightness)
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    fun update(dt: Float, scrollSpeed: Float) {
        cloudOffset = (cloudOffset + scrollSpeed * 0.15f * dt) % w
        hillOffset = (hillOffset + scrollSpeed * 0.35f * dt) % w
        groundOffset = (groundOffset + scrollSpeed * dt) % (w * 0.08f)
        starTwinkle += dt
    }

    fun drawSky(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, groundY, skyPaint)

        if (nightness > 0.05f) {
            for (s in stars) {
                val tw = 0.55f + 0.45f * sin(starTwinkle * 2f + s.phase)
                starPaint.alpha = (255 * nightness * tw).toInt().coerceIn(0, 255)
                canvas.drawCircle(s.x, s.y, s.size * (0.8f + 0.4f * tw), starPaint)
            }
        }

        // Clouds - two layers repeated across the screen
        drawCloudRow(canvas, -cloudOffset, groundY * 0.22f, h * 0.030f)
        drawCloudRow(canvas, -cloudOffset + w * 0.5f, groundY * 0.38f, h * 0.022f)

        // Far and near hills
        drawHills(canvas, -hillOffset * 0.6f, groundY, h * 0.10f, hillPaintFar)
        drawHills(canvas, -hillOffset, groundY, h * 0.062f, hillPaint)
    }

    private fun drawCloudRow(canvas: Canvas, offset: Float, y: Float, r: Float) {
        var cx = offset % w
        if (cx > 0) cx -= w
        while (cx < w + r * 4) {
            canvas.drawCircle(cx, y, r * 1.4f, cloudPaint)
            canvas.drawCircle(cx + r * 1.6f, y - r * 0.5f, r * 1.1f, cloudPaint)
            canvas.drawCircle(cx + r * 3.0f, y, r * 1.3f, cloudPaint)
            cx += w * 0.75f
        }
    }

    private fun drawHills(canvas: Canvas, offset: Float, baseY: Float, amp: Float, paint: Paint) {
        hillPath.reset()
        hillPath.moveTo(-w, baseY)
        val seg = w / 4f
        var x = (offset % (seg * 2)) - seg * 2
        while (x < w + seg * 2) {
            hillPath.lineTo(x, baseY)
            hillPath.quadTo(x + seg / 2, baseY - amp * 2f, x + seg, baseY)
            x += seg
        }
        hillPath.lineTo(w * 2, baseY)
        hillPath.close()
        canvas.drawPath(hillPath, paint)
    }

    fun drawGround(canvas: Canvas) {
        canvas.drawRect(0f, groundY, w, h, groundPaint)
        canvas.drawRect(0f, groundY, w, groundY + h * 0.014f, grassPaint)
        // Scrolling diagonal stripes sell the speed
        val stripeW = w * 0.08f
        var x = -groundOffset
        while (x < w + stripeW) {
            canvas.save()
            canvas.clipRect(0f, groundY + h * 0.014f, w, groundY + h * 0.030f)
            canvas.skew(-0.9f, 0f)
            canvas.drawRect(x + groundY, groundY, x + groundY + stripeW / 2, groundY + h * 0.04f, stripePaint)
            canvas.restore()
            x += stripeW
        }
    }
}
