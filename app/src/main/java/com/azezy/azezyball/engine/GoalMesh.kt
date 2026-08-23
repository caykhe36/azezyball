package com.azezy.azezyball.engine

class GoalMesh(
    val width: Float = 5.0f,
    val height: Float = 2.2f,
    val depth: Float = 1.8f,
    val postRadius: Float = 0.07f
) {
    val frameMesh: Mesh
    val netMesh: Mesh

    init {
        frameMesh = buildGoalFrame()
        netMesh = buildGoalNet()
    }

    private fun buildGoalFrame(): Mesh {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Short>()

        val halfW = width / 2f
        val r = postRadius

        // Helper to add a 3D box bar
        fun addBoxBar(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float) {
            val startIdx = (vertices.size / 3).toShort()

            // 8 corners
            val p = arrayOf(
                floatArrayOf(minX, minY, minZ), // 0
                floatArrayOf(maxX, minY, minZ), // 1
                floatArrayOf(maxX, maxY, minZ), // 2
                floatArrayOf(minX, maxY, minZ), // 3
                floatArrayOf(minX, minY, maxZ), // 4
                floatArrayOf(maxX, minY, maxZ), // 5
                floatArrayOf(maxX, maxY, maxZ), // 6
                floatArrayOf(minX, maxY, maxZ)  // 7
            )

            // 6 faces: (4 verts per face = 24 verts)
            val faceIndices = arrayOf(
                intArrayOf(0, 1, 2, 3), // Front (Z-)
                intArrayOf(5, 4, 7, 6), // Back (Z+)
                intArrayOf(4, 0, 3, 7), // Left (X-)
                intArrayOf(1, 5, 6, 2), // Right (X+)
                intArrayOf(3, 2, 6, 7), // Top (Y+)
                intArrayOf(4, 5, 1, 0)  // Bottom (Y-)
            )

            val faceNormals = arrayOf(
                floatArrayOf(0f, 0f, -1f),
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(-1f, 0f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, -1f, 0f)
            )

            for (f in 0 until 6) {
                val fStart = (vertices.size / 3).toShort()
                val fn = faceNormals[f]
                for (v in 0 until 4) {
                    val pt = p[faceIndices[f][v]]
                    vertices.add(pt[0]); vertices.add(pt[1]); vertices.add(pt[2])
                    normals.add(fn[0]); normals.add(fn[1]); normals.add(fn[2])
                    texCoords.add(if (v == 0 || v == 3) 0f else 1f)
                    texCoords.add(if (v == 0 || v == 1) 0f else 1f)
                }
                indices.add(fStart)
                indices.add((fStart + 1).toShort())
                indices.add((fStart + 2).toShort())
                indices.add(fStart)
                indices.add((fStart + 2).toShort())
                indices.add((fStart + 3).toShort())
            }
        }

        // Left Post
        addBoxBar(-halfW - r, 0f, -r, -halfW + r, height + r, r)
        // Right Post
        addBoxBar(halfW - r, 0f, -r, halfW + r, height + r, r)
        // Crossbar
        addBoxBar(-halfW - r, height - r, -r, halfW + r, height + r, r)

        // Bottom Rear Left Bar
        addBoxBar(-halfW - r, 0f, 0f, -halfW + r, r * 2, depth)
        // Bottom Rear Right Bar
        addBoxBar(halfW - r, 0f, 0f, halfW + r, r * 2, depth)
        // Bottom Back Bar
        addBoxBar(-halfW - r, 0f, depth - r, halfW + r, r * 2, depth + r)

        // Top Rear Left Bar
        addBoxBar(-halfW - r, height - r, 0f, -halfW + r, height + r, depth)
        // Top Rear Right Bar
        addBoxBar(halfW - r, height - r, 0f, halfW + r, height + r, depth)
        // Top Back Bar
        addBoxBar(-halfW - r, height - r, depth - r, halfW + r, height + r, depth + r)

        // Vertical Back Left Bar
        addBoxBar(-halfW - r, 0f, depth - r, -halfW + r, height + r, depth + r)
        // Vertical Back Right Bar
        addBoxBar(halfW - r, 0f, depth - r, halfW + r, height + r, depth + r)

        return Mesh(
            vertexBuffer = Mesh.createFloatBuffer(vertices.toFloatArray()),
            normalBuffer = Mesh.createFloatBuffer(normals.toFloatArray()),
            texCoordBuffer = Mesh.createFloatBuffer(texCoords.toFloatArray()),
            indexBuffer = Mesh.createShortBuffer(indices.toShortArray()),
            vertexCount = vertices.size / 3,
            indexCount = indices.size
        )
    }

    private fun buildGoalNet(): Mesh {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Short>()

        val halfW = width / 2f

        // Helper to add double-sided net quad
        fun addNetQuad(
            p1: FloatArray, p2: FloatArray, p3: FloatArray, p4: FloatArray,
            norm: FloatArray, uMax: Float, vMax: Float
        ) {
            val startIdx = (vertices.size / 3).toShort()

            val pts = arrayOf(p1, p2, p3, p4)
            val uvs = arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(uMax, 0f),
                floatArrayOf(uMax, vMax),
                floatArrayOf(0f, vMax)
            )

            for (i in 0 until 4) {
                vertices.add(pts[i][0]); vertices.add(pts[i][1]); vertices.add(pts[i][2])
                normals.add(norm[0]); normals.add(norm[1]); normals.add(norm[2])
                texCoords.add(uvs[i][0]); texCoords.add(uvs[i][1])
            }

            // Front face
            indices.add(startIdx)
            indices.add((startIdx + 1).toShort())
            indices.add((startIdx + 2).toShort())
            indices.add(startIdx)
            indices.add((startIdx + 2).toShort())
            indices.add((startIdx + 3).toShort())

            // Back face for double-sided visibility
            indices.add(startIdx)
            indices.add((startIdx + 2).toShort())
            indices.add((startIdx + 1).toShort())
            indices.add(startIdx)
            indices.add((startIdx + 3).toShort())
            indices.add((startIdx + 2).toShort())
        }

        // Back net
        addNetQuad(
            floatArrayOf(-halfW, 0f, depth),
            floatArrayOf(halfW, 0f, depth),
            floatArrayOf(halfW, height, depth),
            floatArrayOf(-halfW, height, depth),
            floatArrayOf(0f, 0f, -1f),
            width * 4f, height * 4f
        )

        // Top net
        addNetQuad(
            floatArrayOf(-halfW, height, 0f),
            floatArrayOf(halfW, height, 0f),
            floatArrayOf(halfW, height, depth),
            floatArrayOf(-halfW, height, depth),
            floatArrayOf(0f, -1f, 0f),
            width * 4f, depth * 4f
        )

        // Left net
        addNetQuad(
            floatArrayOf(-halfW, 0f, depth),
            floatArrayOf(-halfW, 0f, 0f),
            floatArrayOf(-halfW, height, 0f),
            floatArrayOf(-halfW, height, depth),
            floatArrayOf(1f, 0f, 0f),
            depth * 4f, height * 4f
        )

        // Right net
        addNetQuad(
            floatArrayOf(halfW, 0f, 0f),
            floatArrayOf(halfW, 0f, depth),
            floatArrayOf(halfW, height, depth),
            floatArrayOf(halfW, height, 0f),
            floatArrayOf(-1f, 0f, 0f),
            depth * 4f, height * 4f
        )

        return Mesh(
            vertexBuffer = Mesh.createFloatBuffer(vertices.toFloatArray()),
            normalBuffer = Mesh.createFloatBuffer(normals.toFloatArray()),
            texCoordBuffer = Mesh.createFloatBuffer(texCoords.toFloatArray()),
            indexBuffer = Mesh.createShortBuffer(indices.toShortArray()),
            vertexCount = vertices.size / 3,
            indexCount = indices.size
        )
    }
}
