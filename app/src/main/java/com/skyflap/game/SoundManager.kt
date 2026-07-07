package com.skyflap.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * All sound effects are synthesized at startup as raw PCM - the game ships
 * with zero audio assets. Each play spawns a short static AudioTrack that is
 * released when done, so effects can overlap freely.
 */
class SoundManager {

    private val sampleRate = 22050
    private val handler = Handler(Looper.getMainLooper())

    private val flap = swoosh()
    private val score = ding()
    private val closeCall = dingHigh()
    private val hit = thud()
    private val newBest = fanfare()

    @Volatile
    var muted = false

    fun playFlap() = play(flap)
    fun playScore() = play(score)
    fun playCloseCall() = play(closeCall)
    fun playHit() = play(hit)
    fun playNewBest() = play(newBest)

    private fun play(data: ShortArray) {
        if (muted) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val durationMs = data.size * 1000L / sampleRate + 60
            handler.postDelayed({
                try {
                    track.release()
                } catch (_: Exception) {
                }
            }, durationMs)
        } catch (_: Exception) {
            // Sound is never worth crashing over.
        }
    }

    private fun buffer(seconds: Float): ShortArray = ShortArray((sampleRate * seconds).toInt())

    /** Flap: quick airy sweep down. */
    private fun swoosh(): ShortArray {
        val b = buffer(0.09f)
        var phase = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / b.size
            val freq = 700.0 - 450.0 * t
            phase += 2 * PI * freq / sampleRate
            val noise = (Random.nextFloat() - 0.5f) * 0.5f
            val env = exp(-4.0 * t) * (1 - t)
            b[i] = ((sin(phase) * 0.5 + noise) * env * 9000).toInt().toShort()
        }
        return b
    }

    /** Score: cheerful two-note ding. */
    private fun ding(): ShortArray = notes(floatArrayOf(880f, 1318.5f), 0.085f)

    /** Near-miss bonus: higher, sparklier. */
    private fun dingHigh(): ShortArray = notes(floatArrayOf(1046.5f, 1568f, 2093f), 0.07f)

    /** New best: little arpeggio fanfare. */
    private fun fanfare(): ShortArray = notes(floatArrayOf(659f, 784f, 988f, 1319f), 0.11f)

    private fun notes(freqs: FloatArray, noteLen: Float): ShortArray {
        val noteSamples = (sampleRate * noteLen).toInt()
        val b = ShortArray(noteSamples * freqs.size)
        for ((n, f) in freqs.withIndex()) {
            for (i in 0 until noteSamples) {
                val t = i.toFloat() / noteSamples
                val env = exp(-3.0 * t) * (if (t < 0.05f) t / 0.05f else 1f)
                val v = sin(2 * PI * f * i / sampleRate) * env * 8500
                b[n * noteSamples + i] = v.toInt().toShort()
            }
        }
        return b
    }

    /** Hit: low thump plus noise burst. */
    private fun thud(): ShortArray {
        val b = buffer(0.22f)
        for (i in b.indices) {
            val t = i.toFloat() / b.size
            val env = exp(-7.0 * t)
            val boom = sin(2 * PI * (110.0 - 60.0 * t) * i / sampleRate)
            val noise = (Random.nextFloat() - 0.5f) * exp(-14.0 * t)
            b[i] = ((boom * 0.7 + noise) * env * 12000).toInt().toShort()
        }
        return b
    }
}
