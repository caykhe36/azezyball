package com.azezy.azezyball.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleSystem(val maxParticles: Int = 360) {

    class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f
        var life = 0f
        var maxLife = 1f
        var size = 24f
    }

    private val particles = Array(maxParticles) { Particle() }
    private val vertexData = FloatArray(maxParticles * 8)
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var activeCount = 0

    fun explode(originX: Float, originY: Float, originZ: Float, count: Int = 180) {
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                p.active = true
                p.x = originX + (Random.nextFloat() - 0.5f) * 0.5f
                p.y = originY + (Random.nextFloat() - 0.5f) * 0.5f
                p.z = originZ + (Random.nextFloat() - 0.5f) * 0.5f

                val speed = 3.5f + Random.nextFloat() * 5.0f
                val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
                val elevation = (Random.nextFloat() * 0.85f + 0.15f)

                p.vx = cos(angle) * speed * (1f - elevation * 0.4f)
                p.vy = speed * elevation + 2.0f
                p.vz = sin(angle) * speed * (1f - elevation * 0.4f) - 0.8f

                // Bright cartoon rainbow confetti colors
                when (Random.nextInt(6)) {
                    0 -> { p.r = 0.99f; p.g = 0.75f; p.b = 0.14f } // Gold
                    1 -> { p.r = 0.96f; p.g = 0.25f; p.b = 0.37f } // Bright Pink/Magenta
                    2 -> { p.r = 0.22f; p.g = 0.74f; p.b = 0.97f } // Sky Cyan
                    3 -> { p.r = 0.29f; p.g = 0.87f; p.b = 0.50f } // Bright Lime
                    4 -> { p.r = 0.98f; p.g = 0.57f; p.b = 0.24f } // Vivid Orange
                    5 -> { p.r = 0.99f; p.g = 0.88f; p.b = 0.28f } // Sun Yellow
                }
                p.a = 1.0f
                p.maxLife = 1.8f + Random.nextFloat() * 1.0f
                p.life = p.maxLife
                p.size = 24f + Random.nextFloat() * 26f

                spawned++
                if (spawned >= count) break
            }
        }
    }

    fun update(dt: Float) {
        activeCount = 0
        var offset = 0

        for (p in particles) {
            if (p.active) {
                p.life -= dt
                if (p.life <= 0f) {
                    p.active = false
                    continue
                }

                p.vy -= 9.8f * dt
                p.vx *= (1f - 0.4f * dt)
                p.vz *= (1f - 0.4f * dt)

                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt

                if (p.y < 0.08f) {
                    p.y = 0.08f
                    p.vy = -p.vy * 0.45f
                }

                val progress = p.life / p.maxLife
                p.a = (progress * 1.2f).coerceIn(0f, 1f)

                vertexData[offset++] = p.x
                vertexData[offset++] = p.y
                vertexData[offset++] = p.z
                vertexData[offset++] = p.r
                vertexData[offset++] = p.g
                vertexData[offset++] = p.b
                vertexData[offset++] = p.a
                vertexData[offset++] = p.size * (0.6f + 0.4f * progress)

                activeCount++
            }
        }

        vertexBuffer.position(0)
        vertexBuffer.put(vertexData, 0, offset)
        vertexBuffer.position(0)
    }

    fun render(
        programId: Int,
        mvpMatrix: FloatArray,
        mvpMatrixHandle: Int,
        positionHandle: Int,
        colorHandle: Int,
        sizeHandle: Int
    ) {
        if (activeCount == 0) return

        GLES20.glUseProgram(programId)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        val stride = 8 * 4

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(7)
        GLES20.glEnableVertexAttribArray(sizeHandle)
        GLES20.glVertexAttribPointer(sizeHandle, 1, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, activeCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glDisableVertexAttribArray(sizeHandle)

        GLES20.glDepthMask(true)
    }
}
