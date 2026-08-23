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
    private var vboIds = IntArray(4) // 0: vertex, 1: normal, 2: texCoord, 3: index
    private var isVboInitialized = false

    fun initVBO() {
        if (isVboInitialized) return

        GLES20.glGenBuffers(4, vboIds, 0)

        // 1. Vertex Buffer
        vertexBuffer.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GLES20.GL_STATIC_DRAW)

        // 2. Normal Buffer
        normalBuffer.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, normalBuffer.capacity() * 4, normalBuffer, GLES20.GL_STATIC_DRAW)

        // 3. TexCoord Buffer
        if (texCoordBuffer != null) {
            texCoordBuffer.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[2])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoordBuffer.capacity() * 4, texCoordBuffer, GLES20.GL_STATIC_DRAW)
        }

        // 4. Index Buffer
        if (indexBuffer != null && indexCount > 0) {
            indexBuffer.position(0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vboIds[3])
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.capacity() * 2, indexBuffer, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        isVboInitialized = true
    }

    fun render(
        positionHandle: Int,
        normalHandle: Int,
        texCoordHandle: Int,
        useTexture: Boolean = false
    ) {
        if (!isVboInitialized) {
            initVBO()
        }

        // Bind and point Positions from GPU VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[0])
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        // Bind and point Normals from GPU VBO
        if (normalHandle >= 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[1])
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
        }

        // Bind and point TexCoords from GPU VBO
        if (texCoordHandle >= 0 && texCoordBuffer != null) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[2])
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0)
        }

        // Draw Call from GPU memory
        if (indexBuffer != null && indexCount > 0) {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vboIds[3])
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        }

        // Unbind
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisableVertexAttribArray(positionHandle)
        if (normalHandle >= 0) {
            GLES20.glDisableVertexAttribArray(normalHandle)
        }
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
