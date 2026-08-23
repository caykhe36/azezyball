package com.azezy.azezyball.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SphereMesh(radius: Float = 0.22f, rings: Int = 24, sectors: Int = 32) : Mesh(
    vertexBuffer = createFloatBuffer(generateVertices(radius, rings, sectors)),
    normalBuffer = createFloatBuffer(generateNormals(rings, sectors)),
    texCoordBuffer = createFloatBuffer(generateTexCoords(rings, sectors)),
    indexBuffer = createShortBuffer(generateIndices(rings, sectors)),
    vertexCount = (rings + 1) * (sectors + 1),
    indexCount = rings * sectors * 6
) {
    companion object {
        private fun generateVertices(radius: Float, rings: Int, sectors: Int): FloatArray {
            val vertices = ArrayList<Float>()
            val R = 1.0f / rings.toFloat()
            val S = 1.0f / sectors.toFloat()

            for (r in 0..rings) {
                val phi = (r * R) * PI.toFloat()
                val y = cos(phi)
                val sinPhi = sin(phi)

                for (s in 0..sectors) {
                    val theta = (s * S) * (2.0f * PI.toFloat())
                    val x = sinPhi * sin(theta)
                    val z = sinPhi * cos(theta)

                    vertices.add(x * radius)
                    vertices.add(y * radius)
                    vertices.add(z * radius)
                }
            }
            return vertices.toFloatArray()
        }

        private fun generateNormals(rings: Int, sectors: Int): FloatArray {
            val normals = ArrayList<Float>()
            val R = 1.0f / rings.toFloat()
            val S = 1.0f / sectors.toFloat()

            for (r in 0..rings) {
                val phi = (r * R) * PI.toFloat()
                val y = cos(phi)
                val sinPhi = sin(phi)

                for (s in 0..sectors) {
                    val theta = (s * S) * (2.0f * PI.toFloat())
                    val x = sinPhi * sin(theta)
                    val z = sinPhi * cos(theta)

                    normals.add(x)
                    normals.add(y)
                    normals.add(z)
                }
            }
            return normals.toFloatArray()
        }

        private fun generateTexCoords(rings: Int, sectors: Int): FloatArray {
            val texCoords = ArrayList<Float>()
            val R = 1.0f / rings.toFloat()
            val S = 1.0f / sectors.toFloat()

            for (r in 0..rings) {
                for (s in 0..sectors) {
                    val u = s * S
                    val v = r * R
                    texCoords.add(u)
                    texCoords.add(v)
                }
            }
            return texCoords.toFloatArray()
        }

        private fun generateIndices(rings: Int, sectors: Int): ShortArray {
            val indices = ArrayList<Short>()
            val stride = (sectors + 1).toShort()

            for (r in 0 until rings) {
                for (s in 0 until sectors) {
                    val r0 = (r * stride).toShort()
                    val r1 = ((r + 1) * stride).toShort()

                    val cur = (r0 + s).toShort()
                    val next = (cur + 1).toShort()
                    val below = (r1 + s).toShort()
                    val belowNext = (below + 1).toShort()

                    indices.add(cur)
                    indices.add(below)
                    indices.add(next)

                    indices.add(next)
                    indices.add(below)
                    indices.add(belowNext)
                }
            }
            return indices.toShortArray()
        }
    }
}
