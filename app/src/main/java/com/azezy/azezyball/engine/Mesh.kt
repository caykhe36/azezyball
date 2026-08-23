package com.azezy.azezyball.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

open class Mesh(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val texCoordBuffer: FloatBuffer?,
    val indexBuffer: ShortBuffer?,
    val vertexCount: Int,
    val indexCount: Int
) {
    fun render(
        positionHandle: Int,
        normalHandle: Int,
        texCoordHandle: Int,
        useTexture: Boolean = false
    ) {
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        normalBuffer.position(0)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

        if (texCoordHandle >= 0 && texCoordBuffer != null) {
            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        }

        if (indexBuffer != null && indexCount > 0) {
            indexBuffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        if (texCoordHandle >= 0 && texCoordBuffer != null) {
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }
    }

    companion object {
        fun createFloatBuffer(array: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(array.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(array)
                    position(0)
                }
        }

        fun createShortBuffer(array: ShortArray): ShortBuffer {
            return ByteBuffer.allocateDirect(array.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(array)
                    position(0)
                }
        }
    }
}
