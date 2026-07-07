package com.skyflap.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

/** Lightweight pooled particle system: feathers, sparkles and confetti. */
class ParticleSystem {

    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f
        var size = 0f
        var color = Color.WHITE
        var gravity = 0f
        var alive = false
    }

    private val particles = Array(160) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun obtain(): Particle? = particles.firstOrNull { !it.alive }

    fun burstFeathers(x: Float, y: Float, scale: Float, count: Int = 5) {
        repeat(count) {
            val p = obtain() ?: return
            p.alive = true
            p.x = x; p.y = y
            val ang = Random.nextFloat() * 6.283f
            val spd = scale * (0.4f + Random.nextFloat() * 1.2f)
            p.vx = kotlin.math.cos(ang) * spd - scale * 0.8f
            p.vy = kotlin.math.sin(ang) * spd
            p.gravity = scale * 1.6f
            p.maxLife = 0.5f + Random.nextFloat() * 0.4f
            p.life = p.maxLife
            p.size = scale * (0.05f + Random.nextFloat() * 0.05f)
            p.color = if (Random.nextBoolean()) Color.rgb(255, 211, 78) else Color.rgb(245, 185, 33)
        }
    }

    fun burstScore(x: Float, y: Float, scale: Float, golden: Boolean) {
        repeat(if (golden) 18 else 10) {
            val p = obtain() ?: return
            p.alive = true
            p.x = x; p.y = y
            val ang = Random.nextFloat() * 6.283f
            val spd = scale * (0.8f + Random.nextFloat() * 1.6f)
            p.vx = kotlin.math.cos(ang) * spd
            p.vy = kotlin.math.sin(ang) * spd
            p.gravity = 0f
            p.maxLife = 0.35f + Random.nextFloat() * 0.3f
            p.life = p.maxLife
            p.size = scale * (0.03f + Random.nextFloat() * 0.04f)
            p.color = if (golden) Color.rgb(255, 200, 40) else Color.WHITE
        }
    }

    fun burstConfetti(x: Float, y: Float, scale: Float) {
        val colors = intArrayOf(
            Color.rgb(255, 90, 90), Color.rgb(90, 200, 255),
            Color.rgb(120, 255, 120), Color.rgb(255, 220, 80), Color.rgb(220, 130, 255)
        )
        repeat(40) {
            val p = obtain() ?: return
            p.alive = true
            p.x = x; p.y = y
            val ang = -1.57f + (Random.nextFloat() - 0.5f) * 2.2f
            val spd = scale * (1.5f + Random.nextFloat() * 2.5f)
            p.vx = kotlin.math.cos(ang) * spd
            p.vy = kotlin.math.sin(ang) * spd
            p.gravity = scale * 3f
            p.maxLife = 0.8f + Random.nextFloat() * 0.8f
            p.life = p.maxLife
            p.size = scale * (0.04f + Random.nextFloat() * 0.05f)
            p.color = colors[Random.nextInt(colors.size)]
        }
    }

    fun update(dt: Float) {
        for (p in particles) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    fun draw(canvas: Canvas) {
        for (p in particles) {
            if (!p.alive) continue
            paint.color = p.color
            paint.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
    }

    fun clear() {
        for (p in particles) p.alive = false
    }
}
