package com.azezy.azezyball.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine(private val context: Context) {

    var isSoundEnabled = true
    var isHapticsEnabled = true

    private val executor = Executors.newSingleThreadExecutor()
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val sampleRate = 44100

    private var kickAudio: ShortArray? = null
    private var postAudio: ShortArray? = null
    private var goalAudio: ShortArray? = null
    private var missAudio: ShortArray? = null
    private var whistleAudio: ShortArray? = null

    init {
        executor.execute {
            try {
                kickAudio = generateKickSound()
                postAudio = generatePostSound()
                goalAudio = generateGoalFanfareSound()
                missAudio = generateMissSound()
                whistleAudio = generateWhistleSound()
            } catch (_: Exception) {
            }
        }
    }

    fun playKick() {
        if (!isSoundEnabled) return
        executor.execute {
            kickAudio?.let { playBufferSafe(it) }
        }
        vibrate(35)
    }

    fun playPostHit() {
        if (!isSoundEnabled) return
        executor.execute {
            postAudio?.let { playBufferSafe(it) }
        }
        vibrate(60)
    }

    fun playGoal() {
        if (!isSoundEnabled) return
        executor.execute {
            whistleAudio?.let { playBufferSafe(it) }
            goalAudio?.let { playBufferSafe(it) }
        }
        vibratePattern(longArrayOf(0, 50, 50, 100, 50, 150))
    }

    fun playMiss() {
        if (!isSoundEnabled) return
        executor.execute {
            missAudio?.let { playBufferSafe(it) }
        }
        vibrate(40)
    }

    private fun playBufferSafe(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()

            val durationMs = (buffer.size * 1000L) / sampleRate
            Thread.sleep(durationMs.coerceAtMost(1500L) + 15)
        } catch (_: Throwable) {
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun generateKickSound(): ShortArray {
        val duration = 0.18
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            val freq = 175.0 * (1.0 - progress * 0.78)
            val envelope = exp(- progress * 15.0)
            val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.12 * exp(- progress * 26.0)

            val sample = (sin(2.0 * PI * freq * t) + noise) * envelope
            buffer[i] = (sample * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generatePostSound(): ShortArray {
        val duration = 0.55
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration

            val f1 = 820.0
            val f2 = 1640.0
            val f3 = 2480.0

            val env1 = exp(- progress * 7.0)
            val env2 = exp(- progress * 14.0)
            val env3 = exp(- progress * 22.0)

            val sample = 0.55 * sin(2.0 * PI * f1 * t) * env1 +
                         0.30 * sin(2.0 * PI * f2 * t) * env2 +
                         0.15 * sin(2.0 * PI * f3 * t) * env3

            buffer[i] = (sample * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateGoalFanfareSound(): ShortArray {
        val duration = 1.1
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val noteDur = duration / notes.size

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val noteIdx = (t / noteDur).toInt().coerceIn(0, notes.size - 1)
            val noteT = t - noteIdx * noteDur
            val noteProgress = noteT / noteDur

            val freq = notes[noteIdx]
            val env = sin(PI * noteProgress.coerceIn(0.0, 1.0)) * (1.0 - noteProgress * 0.25)
            val sample = (sin(2.0 * PI * freq * noteT) + 0.3 * sin(4.0 * PI * freq * noteT)) * env

            buffer[i] = (sample * 24000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateMissSound(): ShortArray {
        val duration = 0.35
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            val freq = 260.0 * (1.0 - progress * 0.55)
            val envelope = exp(- progress * 6.5)

            val sample = sin(2.0 * PI * freq * t) * envelope
            buffer[i] = (sample * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateWhistleSound(): ShortArray {
        val duration = 0.32
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration

            val f1 = 2800.0 + 45.0 * sin(2.0 * PI * 35.0 * t)
            val f2 = 2860.0
            val env = sin(PI * progress.coerceIn(0.0, 1.0))

            val sample = 0.5 * (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t)) * env
            buffer[i] = (sample * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun vibrate(durationMs: Long) {
        if (!isHapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        if (!isHapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }
    }
}
