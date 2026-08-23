package com.azezy.azezyball.game

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

data class ScoreEvent(
    val pointsAdded: Int,
    val streak: Int,
    val totalScore: Int,
    val title: String,
    val isTopCorner: Boolean,
    val distanceMeters: Float
)

class GameManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("azezyball_prefs", Context.MODE_PRIVATE)

    var currentScore = 0
        private set

    var currentStreak = 0
        private set

    var bestScore = 0
        private set

    var bestStreak = 0
        private set

    var currentDistance = 11.0f
        private set

    var onScoreChanged: ((score: Int, best: Int, streak: Int) -> Unit)? = null
    var onGoalCelebration: ((event: ScoreEvent) -> Unit)? = null
    var onMissBanner: (() -> Unit)? = null

    init {
        bestScore = prefs.getInt("best_score", 0)
        bestStreak = prefs.getInt("best_streak", 0)
    }

    fun getNextBallPosition(): Pair<Float, Float> {
        // Generates realistic football shoot positions (x, z)
        // z: -11m to -18m
        // x: -5m to +5m
        val distZ = -11.0f - (Random.nextFloat() * 6.5f)
        val posX = (Random.nextFloat() - 0.5f) * 7.0f
        currentDistance = hypot(posX.toDouble(), distZ.toDouble()).toFloat()
        return Pair(posX, distZ)
    }

    fun recordGoal(finalX: Float, finalY: Float): ScoreEvent {
        currentStreak++
        if (currentStreak > bestStreak) {
            bestStreak = currentStreak
            prefs.edit().putInt("best_streak", bestStreak).apply()
        }

        // Top corner detection (x near +-2.2, y near 2.0)
        val isTopCorner = (abs(finalX) >= 1.6f && finalY >= 1.4f)

        // Base points
        var basePoints = (currentDistance * 10).toInt()

        // Corner bonus
        if (isTopCorner) {
            basePoints += 150
        }

        // Streak Multiplier
        val multiplier = when {
            currentStreak >= 10 -> 5
            currentStreak >= 5 -> 3
            currentStreak >= 3 -> 2
            else -> 1
        }

        val totalEarned = basePoints * multiplier
        currentScore += totalEarned

        if (currentScore > bestScore) {
            bestScore = currentScore
            prefs.edit().putInt("best_score", bestScore).apply()
        }

        val title = when {
            isTopCorner -> "SIÊU PHẨM GÓC CHỮ A! 🎯"
            currentStreak >= 5 -> "COMBO GHI BÀN x$currentStreak! 🔥"
            currentDistance >= 15f -> "BÀN THẮNG TẦM XA! ⚡"
            else -> "VÀO! BÀN THẮNG ĐẸP MẮT! ⚽"
        }

        val event = ScoreEvent(
            pointsAdded = totalEarned,
            streak = currentStreak,
            totalScore = currentScore,
            title = title,
            isTopCorner = isTopCorner,
            distanceMeters = currentDistance
        )

        onScoreChanged?.invoke(currentScore, bestScore, currentStreak)
        onGoalCelebration?.invoke(event)
        return event
    }

    fun recordMiss() {
        currentStreak = 0
        onScoreChanged?.invoke(currentScore, bestScore, currentStreak)
        onMissBanner?.invoke()
    }

    fun resetGame() {
        currentScore = 0
        currentStreak = 0
        onScoreChanged?.invoke(currentScore, bestScore, currentStreak)
    }
}
