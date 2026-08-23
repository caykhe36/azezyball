package com.azezy.azezyball.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class TrajectoryRenderer(val maxPoints: Int = 32) {

    private val vertexData = FloatArray(maxPoints * 3)
    private val colorData = FloatArray(maxPoints * 4)

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val colorBuffer: FloatBuffer = ByteBuffer.allocateDirect(colorData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var pointCount = 0

    fun calculateTrajectory(
        startX: Float, startY: Float, startZ: Float,
        vx: Float, vy: Float, vz: Float,
        spinX: Float = 0f
    ) {
        var x = startX
        var y = startY
        var z = startZ
        var curVx = vx
        var curVy = vy
        var curVz = vz

        val dt = 0.04f
        var vOffset = 0
        var cOffset = 0
        pointCount = 0

        for (i in 0 until maxPoints) {
            vertexData[vOffset++] = x
            vertexData[vOffset++] = y
            vertexData[vOffset++] = z

            val alpha = (1f - (i.toFloat() / maxPoints.toFloat())).coerceIn(0.1f, 0.9f)
            colorData[cOffset++] = 1.0f // R
            colorData[cOffset++] = 0.84f // G
            colorData[cOffset++] = 0.0f  // B
            colorData[cOffset++] = alpha // A

            pointCount++

            // Physics step
            curVy -= 9.8f * dt
            curVx += spinX * 0.8f * dt
            x += curVx * dt
            y += curVy * dt
            z += curVz * dt

            // Stop if hits ground or crosses goal line
            if (y < 0.1f || z > 2.0f) {
                break
            }
        }

        vertexBuffer.position(0)
        vertexBuffer.put(vertexData, 0, vOffset)
        vertexBuffer.position(0)

        colorBuffer.position(0)
        colorBuffer.put(colorData, 0, cOffset)
        colorBuffer.position(0)
    }

    fun render(
        programId: Int,
        mvpMatrix: FloatArray,
        mvpMatrixHandle: Int,
        positionHandle: Int,
        colorHandle: Int
    ) {
        if (pointCount < 2) return

        GLES20.glUseProgram(programId)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glLineWidth(6.0f)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        colorBuffer.position(0)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, pointCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }
}
