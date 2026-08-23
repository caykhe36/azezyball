package com.azezy.azezyball.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
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
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val sampleRate = 44100

    // Synthesized Audio Buffers pre-generated for zero-latency instant playback
    private var kickAudio: ShortArray? = null
    private var postAudio: ShortArray? = null
    private var goalAudio: ShortArray? = null
    private var missAudio: ShortArray? = null
    private var whistleAudio: ShortArray? = null

    init {
        executor.execute {
            kickAudio = generateKickSound()
            postAudio = generatePostSound()
            goalAudio = generateGoalFanfareSound()
            missAudio = generateMissSound()
            whistleAudio = generateWhistleSound()
        }
    }

    fun playKick() {
        if (!isSoundEnabled) return
        executor.execute {
            kickAudio?.let { playBuffer(it) }
        }
        vibrate(35)
    }

    fun playPostHit() {
        if (!isSoundEnabled) return
        executor.execute {
            postAudio?.let { playBuffer(it) }
        }
        vibrate(60)
    }

    fun playGoal() {
        if (!isSoundEnabled) return
        executor.execute {
            whistleAudio?.let { playBuffer(it) }
            goalAudio?.let { playBuffer(it) }
        }
        vibratePattern(longArrayOf(0, 50, 50, 100, 50, 150))
    }

    fun playMiss() {
        if (!isSoundEnabled) return
        executor.execute {
            missAudio?.let { playBuffer(it) }
        }
        vibrate(40)
    }

    private fun playBuffer(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
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

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            
            // Release after playing
            val durationMs = (buffer.size * 1000L) / sampleRate
            Thread.sleep(durationMs + 20)
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Exception) {
        }
    }

    private fun generateKickSound(): ShortArray {
        val duration = 0.22
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            // Pitch drop 180Hz -> 35Hz
            val freq = 180.0 * (1.0 - progress * 0.8)
            val envelope = exp(- progress * 14.0)
            val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.15 * exp(- progress * 25.0)

            val sample = (sin(2.0 * PI * freq * t) + noise) * envelope
            buffer[i] = (sample * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generatePostSound(): ShortArray {
        val duration = 0.65
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            // Resonant metallic ping: 1200Hz + 2400Hz + 3600Hz
            val env = exp(- progress * 7.0)
            val s1 = sin(2.0 * PI * 1250.0 * t) * 0.6
            val s2 = sin(2.0 * PI * 2600.0 * t) * 0.35
            val s3 = sin(2.0 * PI * 3950.0 * t) * 0.15

            val sample = (s1 + s2 + s3) * env
            buffer[i] = (sample * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateGoalFanfareSound(): ShortArray {
        val duration = 1.2
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration

            // Harmonic chord progression (C5 -> E5 -> G5 -> C6)
            val currentFreq = when {
                progress < 0.25 -> 523.25 // C5
                progress < 0.50 -> 659.25 // E5
                progress < 0.75 -> 783.99 // G5
                else -> 1046.50           // C6
            }

            val noteProgress = (progress % 0.25) / 0.25
            val noteEnv = exp(- noteProgress * 3.0)

            // Stadium cheer noise layer
            val cheer = (Random.nextDouble() * 2.0 - 1.0) * 0.35 * (0.3 + 0.7 * sin(PI * progress))

            val synth = sin(2.0 * PI * currentFreq * t) * 0.65 + sin(2.0 * PI * (currentFreq * 2.0) * t) * 0.25
            val sample = synth * noteEnv * 0.7 + cheer * 0.3
            buffer[i] = (sample * 27000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateMissSound(): ShortArray {
        val duration = 0.5
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            val freq = 320.0 - progress * 160.0
            val env = exp(- progress * 4.0)

            val sample = sin(2.0 * PI * freq * t) * env
            buffer[i] = (sample * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun generateWhistleSound(): ShortArray {
        val duration = 0.28
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration
            val trill = 2600.0 + 150.0 * sin(2.0 * PI * 40.0 * t)
            val attack = if (progress < 0.05) progress / 0.05 else 1.0
            val env = (1.0 - progress * 0.3) * attack

            val sample = sin(2.0 * PI * trill * t) * env
            buffer[i] = (sample * 25000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    private fun vibrate(ms: Long) {
        if (!isHapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(pattern: LongArray) {
        if (!isHapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        executor.shutdown()
    }
}
