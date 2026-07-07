package com.skyflap.game

import android.content.Context

/** Persists best score and lifetime stats in SharedPreferences. */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences("skyflap", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt("best", 0)
        private set(value) = prefs.edit().putInt("best", value).apply()

    val gamesPlayed: Int get() = prefs.getInt("games", 0)
    val totalScore: Int get() = prefs.getInt("total", 0)

    /** Records a finished run. Returns true if it is a new best. */
    fun submit(score: Int): Boolean {
        prefs.edit()
            .putInt("games", gamesPlayed + 1)
            .putInt("total", totalScore + score)
            .apply()
        if (score > bestScore) {
            bestScore = score
            return true
        }
        return false
    }
}
