package com.skyflap.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns all game state and orchestrates update/draw.
 *
 * State machine:  MENU -> READY -> PLAYING -> DYING -> GAME_OVER -> READY ...
 */
class GameEngine(context: Context, width: Int, height: Int) {

    companion object {
        /** Ground line as a fraction of screen height, shared by all systems. */
        const val GROUND_FRACTION = 0.84f
        private const val NIGHT_CYCLE = 20   // points per full day->night->day cycle
    }

    private enum class State { MENU, READY, PLAYING, DYING, GAME_OVER }

    private var state = State.MENU
    private var w = width.toFloat().coerceAtLeast(1f)
    private var h = height.toFloat().coerceAtLeast(1f)
    private var groundY = h * GROUND_FRACTION

    private val bird = Bird(width, height)
    private val pipeManager = PipeManager(width, height)
    private val background = Background(width, height)
    private val particles = ParticleSystem()
    private val sound = SoundManager()
    private val scoreStore = ScoreStore(context)

    private var score = 0
    private var isNewBest = false
    private var time = 0f
    private var stateTime = 0f

    // Juice
    private var shakeTime = 0f
    private var shakeMagnitude = 0f
    private var flashAlpha = 0f
    private var scorePop = 0f

    // Floating bonus texts
    private class FloatText {
        var text = ""; var x = 0f; var y = 0f; var life = 0f; var color = Color.WHITE
    }

    private val floatTexts = ArrayList<FloatText>()

    // ---- Paints ----
    private val scorePaint = textPaint(Color.WHITE)
    private val bigPaint = textPaint(Color.WHITE)
    private val smallPaint = textPaint(Color.WHITE)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(222, 216, 149) }
    private val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(84, 56, 12); style = Paint.Style.STROKE
    }
    private val panelLabel = textPaint(Color.rgb(226, 106, 47))
    private val panelValue = textPaint(Color.rgb(84, 56, 12))
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 106, 47) }
    private val buttonText = textPaint(Color.WHITE)
    private val medalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medalRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val flashPaint = Paint()
    private val overlayPaint = Paint().apply { color = Color.argb(90, 0, 0, 0) }
    private val floatPaint = textPaint(Color.WHITE)

    private val restartButton = RectF()
    private val tmpRect = RectF()

    init {
        onResize(width, height)
    }

    private fun textPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 4f, Color.argb(140, 0, 0, 0))
    }

    fun onResize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        w = width.toFloat()
        h = height.toFloat()
        groundY = h * GROUND_FRACTION
        bird.onResize(width, height)
        pipeManager.onResize(width, height)
        background.onResize(width, height)

        scorePaint.textSize = h * 0.065f
        bigPaint.textSize = h * 0.052f
        smallPaint.textSize = h * 0.022f
        panelLabel.textSize = h * 0.020f
        panelValue.textSize = h * 0.040f
        buttonText.textSize = h * 0.026f
        floatPaint.textSize = h * 0.028f
        panelBorder.strokeWidth = h * 0.006f
        medalRim.strokeWidth = h * 0.005f
    }

    // ------------------------------------------------------------------ input

    fun onTap(x: Float, y: Float) {
        when (state) {
            State.MENU -> setState(State.READY)
            State.READY -> {
                setState(State.PLAYING)
                doFlap()
            }
            State.PLAYING -> doFlap()
            State.DYING -> Unit
            State.GAME_OVER -> {
                // Small delay stops an accidental double-tap restart
                if (stateTime > 0.5f && restartButton.contains(x, y)) {
                    setState(State.READY)
                } else if (stateTime > 1.2f) {
                    setState(State.READY)
                }
            }
        }
    }

    private fun doFlap() {
        bird.flap()
        sound.playFlap()
        particles.burstFeathers(bird.x - bird.radius, bird.y + bird.radius * 0.5f, h * 0.18f, 3)
    }

    private fun setState(next: State) {
        state = next
        stateTime = 0f
        when (next) {
            State.READY -> {
                score = 0
                isNewBest = false
                bird.reset()
                pipeManager.reset()
                particles.clear()
                floatTexts.clear()
                background.setNightness(0f)
            }
            State.DYING -> {
                sound.playHit()
                shake(0.45f, h * 0.02f)
                flashAlpha = 0.9f
            }
            State.GAME_OVER -> {
                isNewBest = scoreStore.submit(score)
                if (isNewBest && score > 0) {
                    sound.playNewBest()
                    particles.burstConfetti(w / 2f, h * 0.30f, h * 0.16f)
                }
            }
            else -> Unit
        }
    }

    fun onAppPaused() {
        // Losing focus mid-run would be unfair; end the run cleanly instead.
        if (state == State.PLAYING) setState(State.DYING)
    }

    // ----------------------------------------------------------------- update

    fun update(dt: Float) {
        time += dt
        stateTime += dt
        particles.update(dt)
        flashAlpha = (flashAlpha - dt * 3f).coerceAtLeast(0f)
        scorePop = (scorePop - dt * 5f).coerceAtLeast(0f)
        if (shakeTime > 0f) shakeTime -= dt

        val it = floatTexts.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.life -= dt
            f.y -= dt * h * 0.06f
            if (f.life <= 0f) it.remove()
        }

        val scroll = h * 0.34f
        when (state) {
            State.MENU, State.READY -> {
                background.update(dt, scroll)
                bird.updateIdle(dt, time)
            }
            State.PLAYING -> {
                background.update(dt, scroll * (1f + 0.02f * min(score, 40)))
                bird.update(dt)
                pipeManager.update(dt, score)
                updateNightCycle()

                when (pipeManager.checkScore(bird, score)) {
                    1 -> onScored(1, false)
                    2 -> onScored(2, true)
                }

                val hitCeiling = bird.y - bird.radius < -h * 0.06f
                val hitGround = bird.y + bird.radius >= groundY
                if (hitGround || hitCeiling || pipeManager.collides(bird, score)) {
                    particles.burstFeathers(bird.x, bird.y, h * 0.25f, 12)
                    setState(State.DYING)
                }
            }
            State.DYING -> {
                bird.updateDead(dt, groundY)
                if (stateTime > 0.9f) setState(State.GAME_OVER)
            }
            State.GAME_OVER -> {
                bird.updateDead(dt, groundY)
            }
        }
    }

    private fun onScored(points: Int, closeCall: Boolean) {
        score += points
        scorePop = 1f
        if (closeCall) {
            sound.playCloseCall()
            addFloatText("CLOSE ONE! +2", Color.rgb(255, 214, 64))
            particles.burstScore(bird.x + bird.radius * 2, bird.y, h * 0.14f, golden = true)
        } else {
            sound.playScore()
            particles.burstScore(bird.x + bird.radius * 2, bird.y, h * 0.10f, golden = false)
        }
        if (score > scoreStore.bestScore && scoreStore.bestScore > 0 && !isNewBest) {
            isNewBest = true
            addFloatText("NEW BEST!", Color.rgb(120, 255, 160))
            sound.playNewBest()
        }
    }

    private fun addFloatText(text: String, color: Int) {
        val f = FloatText()
        f.text = text
        f.x = w / 2f
        f.y = h * 0.30f
        f.life = 1.1f
        f.color = color
        floatTexts.add(f)
    }

    private fun updateNightCycle() {
        val cycle = (score % (NIGHT_CYCLE * 2)).toFloat() / NIGHT_CYCLE
        val n = if (cycle <= 1f) cycle else 2f - cycle
        background.setNightness(smooth(n))
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun shake(duration: Float, magnitude: Float) {
        shakeTime = duration
        shakeMagnitude = magnitude
    }

    // ------------------------------------------------------------------- draw

    fun draw(canvas: Canvas) {
        canvas.save()
        if (shakeTime > 0f) {
            canvas.translate(
                (Random.nextFloat() - 0.5f) * 2 * shakeMagnitude,
                (Random.nextFloat() - 0.5f) * 2 * shakeMagnitude
            )
        }

        background.drawSky(canvas)
        pipeManager.draw(canvas, score)
        background.drawGround(canvas)
        particles.draw(canvas)
        bird.draw(canvas)

        when (state) {
            State.MENU -> drawMenu(canvas)
            State.READY -> drawReady(canvas)
            State.PLAYING -> drawScore(canvas)
            State.DYING -> drawScore(canvas)
            State.GAME_OVER -> drawGameOver(canvas)
        }

        for (f in floatTexts) {
            floatPaint.color = f.color
            floatPaint.alpha = (255 * (f.life / 1.1f)).toInt().coerceIn(0, 255)
            canvas.drawText(f.text, f.x, f.y, floatPaint)
        }

        canvas.restore()

        if (flashAlpha > 0f) {
            flashPaint.color = Color.argb((flashAlpha * 255).toInt(), 255, 255, 255)
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }
    }

    private fun drawScore(canvas: Canvas) {
        val pop = 1f + scorePop * 0.25f
        canvas.save()
        canvas.scale(pop, pop, w / 2f, h * 0.12f)
        canvas.drawText(score.toString(), w / 2f, h * 0.14f, scorePaint)
        canvas.restore()
    }

    private fun drawMenu(canvas: Canvas) {
        val bob = sin(time * 3f) * h * 0.006f
        canvas.drawText("SKY FLAP", w / 2f, h * 0.22f + bob, bigPaint)
        if ((time * 2).toInt() % 2 == 0) {
            canvas.drawText("TAP TO START", w / 2f, h * 0.62f, smallPaint)
        }
        canvas.drawText("BEST: ${scoreStore.bestScore}", w / 2f, h * 0.68f, smallPaint)
    }

    private fun drawReady(canvas: Canvas) {
        canvas.drawText("GET READY", w / 2f, h * 0.22f, bigPaint)
        canvas.drawText("TAP TO FLAP", w / 2f, h * 0.62f, smallPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        // Panel slides down with a little ease-out
        val t = (stateTime * 2.2f).coerceAtMost(1f)
        val ease = 1f - (1f - t) * (1f - t)
        val panelW = w * 0.78f
        val panelH = h * 0.30f
        val px = (w - panelW) / 2f
        val py = h * 0.18f * ease + (h * 0.18f - h * 0.30f) * (1f - ease)

        canvas.drawText("GAME OVER", w / 2f, py - h * 0.03f, bigPaint)

        tmpRect.set(px, py, px + panelW, py + panelH)
        canvas.drawRoundRect(tmpRect, h * 0.02f, h * 0.02f, panelPaint)
        canvas.drawRoundRect(tmpRect, h * 0.02f, h * 0.02f, panelBorder)

        // Medal
        val medalCx = px + panelW * 0.24f
        val medalCy = py + panelH * 0.42f
        drawMedal(canvas, medalCx, medalCy, h * 0.045f)
        panelLabel.textAlign = Paint.Align.CENTER
        canvas.drawText("MEDAL", medalCx, py + panelH * 0.14f, panelLabel)

        // Scores
        val colX = px + panelW * 0.72f
        canvas.drawText("SCORE", colX, py + panelH * 0.14f, panelLabel)
        canvas.drawText(score.toString(), colX, py + panelH * 0.34f, panelValue)
        canvas.drawText("BEST", colX, py + panelH * 0.56f, panelLabel)
        canvas.drawText(scoreStore.bestScore.toString(), colX, py + panelH * 0.78f, panelValue)

        if (isNewBest && score > 0 && (time * 3).toInt() % 2 == 0) {
            floatPaint.color = Color.rgb(226, 106, 47)
            floatPaint.alpha = 255
            canvas.drawText("NEW!", colX + panelW * 0.16f, py + panelH * 0.34f, floatPaint)
        }

        // Stats line
        val avg = if (scoreStore.gamesPlayed > 0) scoreStore.totalScore / scoreStore.gamesPlayed else 0
        canvas.drawText(
            "GAMES ${scoreStore.gamesPlayed}   AVG $avg",
            px + panelW / 2f, py + panelH * 0.94f, panelLabel
        )

        // Restart button
        val bw = w * 0.44f
        val bh = h * 0.065f
        restartButton.set((w - bw) / 2f, py + panelH + h * 0.04f, (w + bw) / 2f, py + panelH + h * 0.04f + bh)
        canvas.drawRoundRect(restartButton, bh * 0.3f, bh * 0.3f, buttonPaint)
        canvas.drawText(
            "PLAY AGAIN",
            restartButton.centerX(),
            restartButton.centerY() + buttonText.textSize * 0.35f,
            buttonText
        )
        if (stateTime > 1.2f) {
            canvas.drawText("or tap anywhere", w / 2f, restartButton.bottom + h * 0.035f, smallPaint)
        }
    }

    private fun drawMedal(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val colors = when {
            score >= 40 -> intArrayOf(Color.rgb(229, 228, 226), Color.rgb(180, 180, 185)) // platinum
            score >= 30 -> intArrayOf(Color.rgb(255, 215, 0), Color.rgb(200, 160, 0))     // gold
            score >= 20 -> intArrayOf(Color.rgb(192, 192, 192), Color.rgb(140, 140, 140)) // silver
            score >= 10 -> intArrayOf(Color.rgb(205, 127, 50), Color.rgb(150, 90, 35))    // bronze
            else -> intArrayOf(Color.rgb(200, 195, 160), Color.rgb(160, 155, 120))        // none
        }
        medalPaint.color = colors[0]
        medalRim.color = colors[1]
        canvas.drawCircle(cx, cy, r, medalPaint)
        canvas.drawCircle(cx, cy, r, medalRim)
        canvas.drawCircle(cx, cy, r * 0.68f, medalRim)
        if (score >= 10) {
            // Simple star
            panelValue.textAlign = Paint.Align.CENTER
            val starPaint = Paint(medalRim).apply { style = Paint.Style.FILL }
            canvas.drawCircle(cx, cy, r * 0.2f, starPaint)
        }
    }
}
