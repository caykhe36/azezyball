package com.azezy.azezyball.engine

class GoalkeeperMesh {
    val mesh: Mesh

    init {
        mesh = buildCartoonGoalkeeperMesh()
    }

    private fun buildCartoonGoalkeeperMesh(): Mesh {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texCoords = ArrayList<Float>()
        val indices = ArrayList<Short>()

        fun addBox(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float) {
            val p = arrayOf(
                floatArrayOf(minX, minY, minZ),
                floatArrayOf(maxX, minY, minZ),
                floatArrayOf(maxX, maxY, minZ),
                floatArrayOf(minX, maxY, minZ),
                floatArrayOf(minX, minY, maxZ),
                floatArrayOf(maxX, minY, maxZ),
                floatArrayOf(maxX, maxY, maxZ),
                floatArrayOf(minX, maxY, maxZ)
            )

            val faceIndices = arrayOf(
                intArrayOf(0, 1, 2, 3), // Front
                intArrayOf(5, 4, 7, 6), // Back
                intArrayOf(4, 0, 3, 7), // Left
                intArrayOf(1, 5, 6, 2), // Right
                intArrayOf(3, 2, 6, 7), // Top
                intArrayOf(4, 5, 1, 0)  // Bottom
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
                    texCoords.add(0.5f); texCoords.add(0.5f)
                }
                indices.add(fStart)
                indices.add((fStart + 1).toShort())
                indices.add((fStart + 2).toShort())
                indices.add(fStart)
                indices.add((fStart + 2).toShort())
                indices.add((fStart + 3).toShort())
            }
        }

        // Chunky Cartoon Mascot Goalkeeper
        // Torso (Bright Jersey)
        addBox(-0.35f, 0.75f, -0.2f, 0.35f, 1.45f, 0.2f)

        // Head (Chunky Cute Mascot Head)
        addBox(-0.25f, 1.48f, -0.22f, 0.25f, 1.88f, 0.22f)

        // Left Arm (Outstretched)
        addBox(-0.65f, 1.12f, -0.15f, -0.35f, 1.35f, 0.15f)
        // Huge Left Glove (Cartoon Jumbo Mitt)
        addBox(-0.92f, 1.05f, -0.20f, -0.65f, 1.42f, 0.20f)

        // Right Arm (Outstretched)
        addBox(0.35f, 1.12f, -0.15f, 0.65f, 1.35f, 0.15f)
        // Huge Right Glove (Cartoon Jumbo Mitt)
        addBox(0.65f, 1.05f, -0.20f, 0.92f, 1.42f, 0.20f)

        // Left Leg & Boot
        addBox(-0.30f, 0f, -0.15f, -0.08f, 0.75f, 0.15f)
        // Right Leg & Boot
        addBox(0.08f, 0f, -0.15f, 0.30f, 0.75f, 0.15f)

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
