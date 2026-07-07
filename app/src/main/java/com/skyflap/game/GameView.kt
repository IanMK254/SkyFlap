package com.skyflap.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView that owns the game loop thread.
 * Runs a fixed-ish timestep: dt is measured per frame and clamped so a
 * long GC pause never teleports the bird through a pipe.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: GameThread? = null
    private var engine: GameEngine? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (engine == null) {
            engine = GameEngine(context, width, height)
        } else {
            engine?.onResize(width, height)
        }
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine?.onResize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            engine?.onTap(event.x, event.y)
        }
        return true
    }

    fun resumeGame() {
        if (holder.surface.isValid) startThread()
    }

    fun pauseGame() {
        engine?.onAppPaused()
        stopThread()
    }

    private fun startThread() {
        if (thread?.running == true) return
        thread = GameThread(holder).also { it.start() }
    }

    private fun stopThread() {
        thread?.let {
            it.running = false
            try {
                it.join(500)
            } catch (_: InterruptedException) {
            }
        }
        thread = null
    }

    private inner class GameThread(private val surfaceHolder: SurfaceHolder) : Thread("GameLoop") {
        @Volatile
        var running = true

        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                // Clamp dt to 33ms so pauses do not cause physics tunneling.
                val dt = ((now - last) / 1_000_000_000.0).coerceAtMost(0.033).toFloat()
                last = now

                val eng = engine ?: continue
                eng.update(dt)

                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(surfaceHolder) { eng.draw(canvas) }
                    }
                } finally {
                    if (canvas != null) {
                        try {
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        } catch (_: IllegalStateException) {
                        }
                    }
                }
            }
        }
    }
}
