package com.azezy.azezyball.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PitchMesh(
    val pitchWidth: Float = 36.0f,
    val pitchLength: Float = 50.0f
) {
    val grassMesh: Mesh
    val linesMesh: Mesh
    val boardsMesh: Mesh

    init {
        grassMesh = buildGrass()
        linesMesh = buildLines()
        boardsMesh = buildBoards()
    }

    private fun buildGrass(): Mesh {
        val halfW = pitchWidth / 2f
        val zMin = -35.0f
        val zMax = 8.0f

        val vertices = floatArrayOf(
            -halfW, 0f, zMin,
            halfW, 0f, zMin,
            halfW, 0f, zMax,
            -halfW, 0f, zMax
        )

        val normals = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )

        val texCoords = floatArrayOf(
            0f, 0f,
            pitchWidth / 4f, 0f,
            pitchWidth / 4f, (zMax - zMin) / 4f,
            0f, (zMax - zMin) / 4f
        )

        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

        return Mesh(
            Mesh.createFloatBuffer(vertices),
            Mesh.createFloatBuffer(normals),
            Mesh.createFloatBuffer(texCoords),
            Mesh.createShortBuffer(indices),
            4, 6
        )
    }

    private fun buildLines(): Mesh {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Short>()

        val y = 0.005f // Slightly above grass to avoid z-fighting
        val lw = 0.06f // Line half-width

        fun addQuad(x1: Float, z1: Float, x2: Float, z2: Float) {
            val startIdx = (vertices.size / 3).toShort()
            val dx = x2 - x1
            val dz = z2 - z1
            val len = Math.hypot(dx.toDouble(), dz.toDouble()).toFloat()
            if (len <= 0.0001f) return

            val nx = (-dz / len) * lw
            val nz = (dx / len) * lw

            val p1 = floatArrayOf(x1 + nx, y, z1 + nz)
            val p2 = floatArrayOf(x2 + nx, y, z2 + nz)
            val p3 = floatArrayOf(x2 - nx, y, z2 - nz)
            val p4 = floatArrayOf(x1 - nx, y, z1 - nz)

            val pts = arrayOf(p1, p2, p3, p4)
            for (i in 0 until 4) {
                vertices.add(pts[i][0]); vertices.add(pts[i][1]); vertices.add(pts[i][2])
                normals.add(0f); normals.add(1f); normals.add(0f)
                texCoords.add(0.5f); texCoords.add(0.5f)
            }

            indices.add(startIdx)
            indices.add((startIdx + 1).toShort())
            indices.add((startIdx + 2).toShort())
            indices.add(startIdx)
            indices.add((startIdx + 2).toShort())
            indices.add((startIdx + 3).toShort())
        }

        // Goal Line (z = 0)
        addQuad(-12f, 0f, 12f, 0f)

        // 16m50 Penalty Box
        val boxW = 8.5f
        val boxZ = -16.5f
        addQuad(-boxW, 0f, -boxW, boxZ)
        addQuad(boxW, 0f, boxW, boxZ)
        addQuad(-boxW, boxZ, boxW, boxZ)

        // 5m50 Goal Area
        val goalAreaW = 4.5f
        val goalAreaZ = -5.5f
        addQuad(-goalAreaW, 0f, -goalAreaW, goalAreaZ)
        addQuad(goalAreaW, 0f, goalAreaW, goalAreaZ)
        addQuad(-goalAreaW, goalAreaZ, goalAreaW, goalAreaZ)

        // Penalty spot at (0, y, -11)
        val spotRadius = 0.15f
        val segs = 16
        val spotCenter = floatArrayOf(0f, y, -11f)
        val spotStartIdx = (vertices.size / 3).toShort()
        vertices.add(spotCenter[0]); vertices.add(spotCenter[1]); vertices.add(spotCenter[2])
        normals.add(0f); normals.add(1f); normals.add(0f)
        texCoords.add(0.5f); texCoords.add(0.5f)

        for (i in 0..segs) {
            val angle = (i * 2.0 * PI / segs).toFloat()
            val sx = spotRadius * cos(angle)
            val sz = spotRadius * sin(angle) - 11f
            vertices.add(sx); vertices.add(y); vertices.add(sz)
            normals.add(0f); normals.add(1f); normals.add(0f)
            texCoords.add(0.5f); texCoords.add(0.5f)
        }

        for (i in 1..segs) {
            indices.add(spotStartIdx)
            indices.add((spotStartIdx + i).toShort())
            indices.add((spotStartIdx + i + 1).toShort())
        }

        // Penalty Arc
        val arcRadius = 5.0f
        val arcSegs = 20
        var prevX = 0f
        var prevZ = 0f
        var first = true
        for (i in 0..arcSegs) {
            val angle = PI.toFloat() * 0.7f + (i * PI.toFloat() * 0.6f / arcSegs)
            val ax = arcRadius * cos(angle)
            val az = -11f + arcRadius * sin(angle)
            if (az < boxZ) {
                if (!first) {
                    addQuad(prevX, prevZ, ax, az)
                }
                prevX = ax
                prevZ = az
                first = false
            }
        }

        return Mesh(
            Mesh.createFloatBuffer(vertices.toFloatArray()),
            Mesh.createFloatBuffer(normals.toFloatArray()),
            Mesh.createFloatBuffer(texCoords.toFloatArray()),
            Mesh.createShortBuffer(indices.toShortArray()),
            vertices.size / 3,
            indices.size
        )
    }

    private fun buildBoards(): Mesh {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Short>()

        val halfW = pitchWidth / 2f
        val zBack = 4.5f
        val boardH = 1.0f

        fun addBoardQuad(p1: FloatArray, p2: FloatArray, p3: FloatArray, p4: FloatArray, norm: FloatArray) {
            val startIdx = (vertices.size / 3).toShort()
            val pts = arrayOf(p1, p2, p3, p4)
            for (i in 0 until 4) {
                vertices.add(pts[i][0]); vertices.add(pts[i][1]); vertices.add(pts[i][2])
                normals.add(norm[0]); normals.add(norm[1]); normals.add(norm[2])
                texCoords.add(if (i == 1 || i == 2) 1f else 0f)
                texCoords.add(if (i == 2 || i == 3) 1f else 0f)
            }
            indices.add(startIdx)
            indices.add((startIdx + 1).toShort())
            indices.add((startIdx + 2).toShort())
            indices.add(startIdx)
            indices.add((startIdx + 2).toShort())
            indices.add((startIdx + 3).toShort())
        }

        // Back LED Billboard Wall behind goal
        addBoardQuad(
            floatArrayOf(-halfW, 0f, zBack),
            floatArrayOf(halfW, 0f, zBack),
            floatArrayOf(halfW, boardH, zBack),
            floatArrayOf(-halfW, boardH, zBack),
            floatArrayOf(0f, 0f, -1f)
        )

        // Left LED Billboard Wall
        addBoardQuad(
            floatArrayOf(-halfW, 0f, -30f),
            floatArrayOf(-halfW, 0f, zBack),
            floatArrayOf(-halfW, boardH, zBack),
            floatArrayOf(-halfW, boardH, -30f),
            floatArrayOf(1f, 0f, 0f)
        )

        // Right LED Billboard Wall
        addBoardQuad(
            floatArrayOf(halfW, 0f, zBack),
            floatArrayOf(halfW, 0f, -30f),
            floatArrayOf(halfW, boardH, -30f),
            floatArrayOf(halfW, boardH, zBack),
            floatArrayOf(-1f, 0f, 0f)
        )

        return Mesh(
            Mesh.createFloatBuffer(vertices.toFloatArray()),
            Mesh.createFloatBuffer(normals.toFloatArray()),
            Mesh.createFloatBuffer(texCoords.toFloatArray()),
            Mesh.createShortBuffer(indices.toShortArray()),
            vertices.size / 3,
            indices.size
        )
    }
}
