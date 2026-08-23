package com.azezy.azezyball.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.cos
import kotlin.math.sin

object TextureGenerator {

    fun createCartoonSkyTexture(): Int {
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Bright cartoon sky gradient (Azure Blue to Sunlit Horizon)
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.rgb(14, 165, 233),  // Bright Sky Blue
                    Color.rgb(56, 189, 248),  // Azure
                    Color.rgb(186, 230, 253), // Soft Warm Cyan Horizon
                    Color.rgb(254, 240, 138)  // Gentle Golden Sunlight Glow
                ),
                floatArrayOf(0f, 0.45f, 0.85f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Cartoon Fluffy Clouds
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val cloudShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 147, 197, 253)
            style = Paint.Style.FILL
        }

        fun drawCartoonCloud(cx: Float, cy: Float, scale: Float) {
            // Shadow base
            canvas.drawCircle(cx, cy + 4f * scale, 32f * scale, cloudShadowPaint)
            canvas.drawCircle(cx - 30f * scale, cy + 8f * scale, 24f * scale, cloudShadowPaint)
            canvas.drawCircle(cx + 30f * scale, cy + 8f * scale, 24f * scale, cloudShadowPaint)
            // Main fluffy puff
            canvas.drawCircle(cx, cy, 32f * scale, cloudPaint)
            canvas.drawCircle(cx - 28f * scale, cy + 4f * scale, 24f * scale, cloudPaint)
            canvas.drawCircle(cx + 28f * scale, cy + 4f * scale, 24f * scale, cloudPaint)
            canvas.drawCircle(cx - 15f * scale, cy - 14f * scale, 20f * scale, cloudPaint)
            canvas.drawCircle(cx + 15f * scale, cy - 14f * scale, 20f * scale, cloudPaint)
        }

        drawCartoonCloud(120f, 140f, 1.2f)
        drawCartoonCloud(390f, 100f, 1.0f)
        drawCartoonCloud(250f, 220f, 0.85f)
        drawCartoonCloud(480f, 200f, 0.95f)
        drawCartoonCloud(40f, 260f, 0.75f)

        // Golden Cartoon Sun in top right
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(254, 240, 138)
        }
        canvas.drawCircle(440f, 70f, 48f, sunPaint)
        val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(253, 224, 71)
        }
        canvas.drawCircle(440f, 70f, 36f, sunCorePaint)

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createCartoonSoccerBallTexture(): Int {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Bright Pure White leather
        canvas.drawColor(Color.rgb(255, 255, 255))

        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(30, 41, 59) // Bold Comic Dark Slate
        }

        val goldPatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(251, 191, 36) // Shiny Cartoon Gold
        }

        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.rgb(245, 158, 11) // Golden Seam
        }

        val centers = listOf(
            Pair(128f, 128f), Pair(384f, 128f),
            Pair(256f, 256f),
            Pair(128f, 384f), Pair(384f, 384f),
            Pair(0f, 256f), Pair(512f, 256f),
            Pair(256f, 0f), Pair(256f, 512f)
        )

        for ((idx, center) in centers.withIndex()) {
            val (cx, cy) = center
            val radius = 56f
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
                // Central Gold Star Patch
                canvas.drawPath(path, goldPatchPaint)
                canvas.drawPath(path, seamPaint)
            } else {
                canvas.drawPath(path, darkPaint)
                canvas.drawPath(path, seamPaint)
            }
        }

        // Cartoon Glossy Highlight
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(220f, 220f, 25f, glossPaint)

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createCartoonGrassTexture(): Int {
        val width = 256
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Vibrant Lime & Emerald Cartoon Lawn
        val color1 = Color.rgb(74, 222, 128) // Bright Lime Green
        val color2 = Color.rgb(34, 197, 94)  // Vivid Emerald Green

        val stripeHeight = 64
        val paint = Paint()

        for (y in 0 until height step stripeHeight) {
            val isEven = (y / stripeHeight) % 2 == 0
            paint.color = if (isEven) color1 else color2
            canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + stripeHeight).toFloat(), paint)
        }

        // Add playful cartoon clover / grass specks
        val speckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
        }
        for (i in 0 until 80) {
            val rx = (Math.random() * width).toFloat()
            val ry = (Math.random() * height).toFloat()
            canvas.drawCircle(rx, ry, 3.5f, speckPaint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createCartoonGoldTexture(): Int {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Glossy vibrant cartoon gold gradient
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(
                    Color.rgb(254, 240, 138), // Pale Highlight Gold
                    Color.rgb(251, 191, 36),  // Vivid Yellow Gold
                    Color.rgb(245, 158, 11),  // Warm Amber
                    Color.rgb(254, 240, 138)  // Specular band
                ),
                floatArrayOf(0f, 0.4f, 0.75f, 1.0f),
                Shader.TileMode.MIRROR
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createCartoonNetTexture(): Int {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.argb(0, 0, 0, 0))

        val netPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255) // Soft clean cartoon white net
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val step = 48f
        for (i in -size until size * 2 step step.toInt()) {
            canvas.drawLine(i.toFloat(), 0f, (i + size).toFloat(), size.toFloat(), netPaint)
            canvas.drawLine((i + size).toFloat(), 0f, i.toFloat(), size.toFloat(), netPaint)
        }

        val textureId = loadBitmapToGL(bitmap)
        bitmap.recycle()
        return textureId
    }

    fun createCartoonBillboardTexture(): Int {
        val width = 512
        val height = 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Vibrant Purple/Blue Arcade banner
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(
                    Color.rgb(99, 102, 241),  // Indigo
                    Color.rgb(168, 85, 247),  // Purple
                    Color.rgb(236, 72, 153),  // Pink
                    Color.rgb(99, 102, 241)
                ),
                null,
                Shader.TileMode.REPEAT
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Cartoon Gold Stars and Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(254, 240, 138)
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.rgb(30, 27, 75))
        }

        canvas.drawText("⭐ AZEZY BALL 3D ⭐ SUPER GOAL ⭐", width / 2f, height / 2f + 12f, textPaint)

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
