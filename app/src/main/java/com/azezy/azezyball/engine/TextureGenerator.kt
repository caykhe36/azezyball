package com.azezy.azezyball.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.cos
import kotlin.math.sin

object TextureGenerator {

    fun createSoccerBallTexture(): Int {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Base white leather with subtle ivory shading
        canvas.drawColor(Color.rgb(245, 245, 245))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Draw Gold and Dark Pentagons
        val goldSeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.rgb(212, 175, 55) // Gold
        }

        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(25, 25, 30) // Obsidian black
        }

        val goldPatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(255, 215, 0) // Shiny Gold
        }

        // Draw periodic soccer hexagon/pentagon patches
        val centers = listOf(
            Pair(128f, 128f), Pair(384f, 128f),
            Pair(256f, 256f),
            Pair(128f, 384f), Pair(384f, 384f),
            Pair(0f, 256f), Pair(512f, 256f),
            Pair(256f, 0f), Pair(256f, 512f)
        )

        for ((idx, center) in centers.withIndex()) {
            val (cx, cy) = center
            val radius = 54f
            val path = Path()
            val sides = 5
            for (i in 0 until sides) {
                val angle = Math.toRadians((i * (360.0 / sides) - 18.0)).toFloat()
                val x = cx + radius * cos(angle)
                val y = cy + radius * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            if (idx == 2) {
                // Central Gold Star / Azezy VIP Patch
                canvas.drawPath(path, goldPatchPaint)
                canvas.drawPath(path, goldSeamPaint)
            } else {
                canvas.drawPath(path, darkPaint)
                canvas.drawPath(path, goldSeamPaint)
            }
        }

        // Add stylish gold stripes / curves
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.rgb(200, 200, 200)
        }
        for (i in 0 until 4) {
            canvas.drawLine(0f, (i * 128).toFloat(), 512f, (i * 128).toFloat(), seamPaint)
            canvas.drawLine((i * 128).toFloat(), 0f, (i * 128).toFloat(), 512f, seamPaint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createGrassPitchTexture(): Int {
        val width = 256
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val darkGrass = Color.rgb(22, 105, 38)
        val lightGrass = Color.rgb(28, 130, 48)

        val stripeHeight = 64
        val paint = Paint()

        for (y in 0 until height step stripeHeight) {
            val isEven = (y / stripeHeight) % 2 == 0
            paint.color = if (isEven) darkGrass else lightGrass
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + stripeHeight).toFloat(), paint)
        }

        // Add turf fiber noise
        val noisePaint = Paint().apply {
            color = Color.argb(15, 255, 255, 255)
            strokeWidth = 2f
        }
        for (i in 0 until 500) {
            val rx = (Math.random() * width).toFloat()
            val ry = (Math.random() * height).toFloat()
            canvas.drawLine(rx, ry, rx + 1f, ry + 4f, noisePaint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createGoalNetTexture(): Int {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.argb(0, 0, 0, 0)) // Transparent base

        val netPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 240, 240, 255)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        val step = 16f
        for (i in -size until size * 2 step step.toInt()) {
            canvas.drawLine(i.toFloat(), 0f, (i + size).toFloat(), size.toFloat(), netPaint)
            canvas.drawLine((i + size).toFloat(), 0f, i.toFloat(), size.toFloat(), netPaint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createGoldMetalTexture(): Int {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        for (y in 0 until size) {
            val factor = 0.5f + 0.5f * sin(y.toFloat() * 0.1f)
            val r = (212 + 43 * factor).toInt().coerceIn(0, 255)
            val g = (175 + 50 * factor).toInt().coerceIn(0, 255)
            val b = (55 + 30 * factor).toInt().coerceIn(0, 255)
            paint.color = Color.rgb(r, g, b)
            canvas.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), paint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    private fun loadBitmapToGL(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }

        return textureHandle[0]
    }
}
