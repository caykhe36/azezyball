package com.azezy.azezyball.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleSystem(val maxParticles: Int = 300) {

    class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var z = 0f
        var vx = 0f
        var vy = 0f
        var vz = 0f
        var r = 1f
        var g = 0.84f
        var b = 0f
        var a = 1f
        var life = 0f
        var maxLife = 1f
        var size = 20f
    }

    private val particles = Array(maxParticles) { Particle() }
    private val vertexData = FloatArray(maxParticles * 8) // x, y, z, r, g, b, a, size
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var activeCount = 0

    fun explode(originX: Float, originY: Float, originZ: Float, count: Int = 120) {
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                p.active = true
                p.x = originX + (Random.nextFloat() - 0.5f) * 0.4f
                p.y = originY + (Random.nextFloat() - 0.5f) * 0.4f
                p.z = originZ + (Random.nextFloat() - 0.5f) * 0.4f

                val speed = 2.5f + Random.nextFloat() * 4.5f
                val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
                val elevation = (Random.nextFloat() * 0.8f + 0.2f)

                p.vx = cos(angle) * speed * (1f - elevation * 0.5f)
                p.vy = speed * elevation + 1.5f
                p.vz = sin(angle) * speed * (1f - elevation * 0.5f) - 1.0f

                // Gold, Yellow, Orange, White Spark colors
                when (Random.nextInt(4)) {
                    0 -> { p.r = 1.0f; p.g = 0.84f; p.b = 0.0f } // Pure Gold
                    1 -> { p.r = 1.0f; p.g = 0.95f; p.b = 0.4f } // Bright Light Gold
                    2 -> { p.r = 1.0f; p.g = 0.55f; p.b = 0.0f } // Amber Orange
                    3 -> { p.r = 1.0f; p.g = 1.0f; p.b = 1.0f }  // White Sparkle
                }
                p.a = 1.0f
                p.maxLife = 1.5f + Random.nextFloat() * 1.2f
                p.life = p.maxLife
                p.size = 18f + Random.nextFloat() * 24f

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

                // Gravity & air resistance
                p.vy -= 9.8f * dt
                p.vx *= (1f - 0.5f * dt)
                p.vz *= (1f - 0.5f * dt)

                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt

                // Bounce off ground
                if (p.y < 0.05f) {
                    p.y = 0.05f
                    p.vy = -p.vy * 0.4f
                }

                val progress = p.life / p.maxLife
                p.a = progress.coerceIn(0f, 1f)

                // Fill vertex data
                vertexData[offset++] = p.x
                vertexData[offset++] = p.y
                vertexData[offset++] = p.z
                vertexData[offset++] = p.r
                vertexData[offset++] = p.g
                vertexData[offset++] = p.b
                vertexData[offset++] = p.a
                vertexData[offset++] = p.size * (0.5f + 0.5f * progress)

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

        // Enable alpha blending & point size
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)

        val stride = 8 * 4 // 8 floats per vertex

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
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }
}
