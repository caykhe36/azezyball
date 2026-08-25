package com.azezy.azezyball.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.azezy.azezyball.game.BallState
import com.azezy.azezyball.game.GameManager
import com.azezy.azezyball.sound.SoundEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class SoccerCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private lateinit var gameManager: GameManager
    private lateinit var soundEngine: SoundEngine

    var controlMode = ControlMode.SWIPE_FLICK
    var goalkeeperEnabled = false
    var onFirstTouch: (() -> Unit)? = null

    // 3D Physics Simulation Coordinates (Meters)
    val ballRadius = 0.22f
    var ballX = 0f
    var ballY = ballRadius
    var ballZ = 0f // 0m is penalty spot, 10.5m is the goal line

    var ballVx = 0f
    var ballVy = 0f
    var ballVz = 0f
    var ballSpinY = 0f
    var ballRotationAngle = 0f

    var ballState = BallState.IDLE
    private var flightTime = 0f
    private var postHitSoundPlayed = false

    // 3D Goal Dimensions
    val goalZ = 10.5f
    val goalHalfWidth = 2.6f
    val goalHeight = 2.4f
    val goalDepth = 1.8f

    // 3D Goalkeeper Properties
    var gkX = 0f
    var gkSpeed = 2.2f
    var gkDir = 1f
    val gkWidth = 1.4f
    val gkHeight = 1.8f
    val gkZ = 10.2f

    // 3D Cinematic Camera Tracking
    private var camFollowX = 0f
    private var camFollowY = 0f
    private var camFollowZ = 0f

    // Animated Drifting Clouds
    private var cloudDrift1 = 0f
    private var cloudDrift2 = 0f
    private var cloudDrift3 = 0f

    // Touch & Aiming
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private val touchPoints = ArrayList<PointF>()
    private var isAiming = false
    private var aimVx = 0f
    private var aimVy = 0f
    private var aimVz = 0f
    private var aimCurve = 0f

    // Dynamic Visual FX & Animation States
    private var screenShake = 0f
    private var goldShimmer = 0f
    private var targetPulse = 0f
    private var trailTimer = 0f

    // 3D Glowing Ball Trail (Vệt lửa sao băng)
    private class TrailPoint(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var alpha: Float = 0f, var radius: Float = 0f)
    private val ballTrail = Array(20) { TrailPoint() }

    // Crowd Camera Flash Sparkles
    private class FlashBulb(var x: Float = 0f, var y: Float = 0f, var alpha: Float = 0f, var size: Float = 0f)
    private val flashBulbs = Array(14) { FlashBulb() }

    // Goal Shockwave Ring
    private var shockwaveActive = false
    private var shockwaveRadius = 0f
    private var shockwaveX = 0f
    private var shockwaveY = 0f
    private var shockwaveAlpha = 255

    // Celebration Confetti & Sparkles
    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var color = Color.YELLOW
        var size = 12f
        var alpha = 255
        var life = 0f
        var maxLife = 1f
        var rotation = 0f
        var vRot = 0f
        var isStar = false
    }
    private val particles = Array(140) { Particle() }

    // Pre-allocated Paints (Zero allocations in onDraw)
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stadiumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crowdPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grassPaint1 = Paint()
    private val grassPaint2 = Paint()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldPostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldShimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val netPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val netBackFillPaint = Paint()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballBasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ball3DShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballGoldSeamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkGlovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floodlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val netPath = Path()
    private val ballPatchPath = Path()
    private val rectF = RectF()

    private var lastFrameTimeNs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(gameManager: GameManager, soundEngine: SoundEngine) {
        this.gameManager = gameManager
        this.soundEngine = soundEngine
        initPaints()
        lastFrameTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    private fun initPaints() {
        sunPaint.color = Color.rgb(253, 224, 71)
        sunGlowPaint.apply {
            color = Color.argb(75, 254, 240, 138)
            style = Paint.Style.FILL
        }
        cloudPaint.color = Color.argb(225, 255, 255, 255)
        stadiumPaint.color = Color.rgb(30, 41, 59)
        crowdPaint.color = Color.rgb(51, 65, 85)
        boardPaint.color = Color.rgb(79, 70, 229) // Indigo LED boards

        grassPaint1.color = Color.rgb(74, 222, 128) // Bright Lime Green
        grassPaint2.color = Color.rgb(34, 197, 94)  // Vivid Emerald Green

        linePaint.apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }

        goldPostPaint.apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.FILL
        }

        goldDarkPaint.apply {
            color = Color.rgb(217, 119, 6)
            style = Paint.Style.FILL
        }

        goldHighlightPaint.apply {
            color = Color.rgb(254, 240, 138)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }

        netPaint.apply {
            color = Color.argb(160, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }

        netBackFillPaint.color = Color.argb(45, 15, 23, 42)

        shadowPaint.apply {
            color = Color.argb(80, 5, 46, 22)
            style = Paint.Style.FILL
        }

        ballBasePaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        ballBlackPaint.apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.FILL
        }

        ballGoldSeamPaint.apply {
            color = Color.rgb(245, 158, 11)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        ballGlossPaint.apply {
            color = Color.argb(170, 255, 255, 255)
            style = Paint.Style.FILL
        }

        gkBodyPaint.apply {
            color = Color.rgb(14, 165, 233)
            style = Paint.Style.FILL
        }

        gkGlovePaint.apply {
            color = Color.rgb(249, 115, 22)
            style = Paint.Style.FILL
        }

        gkHeadPaint.apply {
            color = Color.rgb(254, 215, 170)
            style = Paint.Style.FILL
        }

        trajectoryPaint.apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.STROKE
            strokeWidth = 7f
            pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
        }

        particlePaint.style = Paint.Style.FILL

        ledBrandPaint.apply {
            color = Color.rgb(254, 240, 138)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            style = Paint.Style.FILL
            setShadowLayer(6f, 0f, 0f, Color.rgb(245, 158, 11))
        }

        goldShimmerPaint.apply {
            color = Color.rgb(255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(8f, 0f, 0f, Color.rgb(253, 224, 71))
        }

        trailPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        floodlightPaint.apply {
            color = Color.rgb(254, 240, 138)
            style = Paint.Style.FILL
            setShadowLayer(14f, 0f, 0f, Color.WHITE)
        }

        lightBeamPaint.style = Paint.Style.FILL

        shockwavePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.rgb(251, 191, 36)
        }

        reticlePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.rgb(245, 158, 11)
            setShadowLayer(6f, 0f, 0f, Color.rgb(253, 224, 71))
        }

        flashPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f,
            intArrayOf(
                Color.rgb(14, 165, 233),  // Azure
                Color.rgb(56, 189, 248),  // Sky Blue
                Color.rgb(186, 230, 253), // Soft Warm Cyan
                Color.rgb(254, 240, 138)  // Horizon Glow
            ),
            floatArrayOf(0f, 0.4f, 0.85f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtimeNanos()
        var dt = (now - lastFrameTimeNs) / 1_000_000_000.0f
        lastFrameTimeNs = now
        if (dt > 0.04f) dt = 0.04f

        updatePhysics(dt)
        updateParticles(dt)

        val w = width.toFloat()
        val h = height.toFloat()

        // 0. Screen Shake Impact (Rung động màn hình khi sút / trúng xà / ghi bàn)
        canvas.save()
        if (screenShake > 0.001f) {
            val ox = (Random.nextFloat() - 0.5f) * screenShake * 22f
            val oy = (Random.nextFloat() - 0.5f) * screenShake * 22f
            canvas.translate(ox, oy)
            screenShake = (screenShake - dt * 3.5f).coerceAtLeast(0f)
        }

        // 3D Cinematic Camera Tracking Interpolation
        if (ballState == BallState.FLYING || ballState == BallState.SCORED || ballState == BallState.MISSED) {
            camFollowX += (ballX * 0.35f - camFollowX) * 0.12f
            camFollowY += (ballY * 0.20f - camFollowY) * 0.12f
            camFollowZ += (ballZ * 0.15f - camFollowZ) * 0.12f
        } else {
            camFollowX += (0f - camFollowX) * 0.15f
            camFollowY += (0f - camFollowY) * 0.15f
            camFollowZ += (0f - camFollowZ) * 0.15f
        }

        val horizonY = h * 0.43f + (camFollowY * 18f)
        val pitchCenterBottomY = h * 0.84f + (camFollowY * 15f)
        val pitchCenterBottomX = w * 0.5f - (camFollowX * 22f)

        // 1. Draw Bright 3D Cartoon Sky Dome, Sun & Animated Drifting Clouds
        canvas.drawRect(0f, 0f, w, horizonY, skyPaint)

        // Sun & Glowing Corona (Clearly in full view in the open sky below HUD)
        val sunX = w * 0.82f - camFollowX * 10f
        val sunY = horizonY * 0.68f
        canvas.drawCircle(sunX, sunY, 44f, sunGlowPaint)
        canvas.drawCircle(sunX, sunY, 26f, sunPaint)

        // Fluffy Animated Drifting Clouds (Floating freely in full view below HUD)
        val c1X = ((w * 0.16f + cloudDrift1) % (w + 180f)) - 90f - camFollowX * 12f
        val c1Y = horizonY * 0.74f
        drawCloud(canvas, c1X, c1Y, 1.15f)

        val c2X = ((w * 0.58f + cloudDrift2) % (w + 180f)) - 90f - camFollowX * 12f
        val c2Y = horizonY * 0.62f
        drawCloud(canvas, c2X, c2Y, 0.90f)

        val c3X = ((w * 0.35f + cloudDrift3) % (w + 180f)) - 90f - camFollowX * 12f
        val c3Y = horizonY * 0.84f
        drawCloud(canvas, c3X, c3Y, 0.75f)

        // 2. Draw 3D Stadium Stands & Crowd Camera Flash Flares
        canvas.drawRect(0f, horizonY - 26f, w, horizonY, stadiumPaint)
        canvas.drawRect(0f, horizonY - 14f, w, horizonY, crowdPaint)

        // Crowd Camera Flash Sparkles in Grandstand
        for (fb in flashBulbs) {
            if (fb.alpha > 0.05f) {
                flashPaint.color = Color.argb((fb.alpha * 255).toInt(), 255, 255, 255)
                canvas.drawCircle(fb.x, fb.y, fb.size, flashPaint)
                canvas.drawLine(fb.x - fb.size * 2f, fb.y, fb.x + fb.size * 2f, fb.y, flashPaint)
                canvas.drawLine(fb.x, fb.y - fb.size * 2f, fb.x, fb.y + fb.size * 2f, flashPaint)
                fb.alpha -= dt * 2.8f
            } else if (Random.nextFloat() < 0.035f) {
                fb.x = Random.nextFloat() * w
                fb.y = horizonY - 24f + Random.nextFloat() * 18f
                fb.alpha = 0.85f + Random.nextFloat() * 0.15f
                fb.size = 2.5f + Random.nextFloat() * 3.5f
            }
        }

        // 3. Draw 3D Perspective Pitch (Emerald Grass Lawn Stripes)
        val numStripes = 9
        val grassH = h - horizonY
        for (i in 0 until numStripes) {
            val t1 = (i.toFloat() / numStripes)
            val t2 = ((i + 1).toFloat() / numStripes)
            val y1 = horizonY + grassH * (t1 * t1)
            val y2 = horizonY + grassH * (t2 * t2)
            val paint = if (i % 2 == 0) grassPaint1 else grassPaint2
            canvas.drawRect(0f, y1, w, y2, paint)
        }

        // 3D Perspective Projection Function: (World 3D -> Screen 2D)
        val fov = 7.2f
        fun projectX(wx: Float, wz: Float): Float {
            val scale = fov / (fov + wz + camFollowZ)
            return pitchCenterBottomX + wx * (w * 0.29f) * scale
        }
        fun projectY(wy: Float, wz: Float): Float {
            val scale = fov / (fov + wz + camFollowZ)
            val groundY = horizonY + (pitchCenterBottomY - horizonY) * scale
            return groundY - wy * (w * 0.29f) * scale
        }
        fun projectScale(wz: Float): Float {
            return fov / (fov + wz + camFollowZ)
        }

        // Volumetric Stadium Floodlight Beams (Hai chùm đèn pha sân vận động tỏa sáng)
        val flLeftX = w * 0.05f
        val flLeftY = horizonY * 0.40f
        canvas.drawCircle(flLeftX, flLeftY, 14f, floodlightPaint)
        val beamPathLeft = Path().apply {
            moveTo(flLeftX, flLeftY)
            lineTo(projectX(-4.5f, 2.0f), projectY(0f, 2.0f))
            lineTo(projectX(-1.5f, 7.5f), projectY(0f, 7.5f))
            close()
        }
        lightBeamPaint.shader = LinearGradient(flLeftX, flLeftY, projectX(-3.0f, 5.0f), projectY(0f, 5.0f), Color.argb(40, 254, 240, 138), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(beamPathLeft, lightBeamPaint)

        val flRightX = w * 0.95f
        val flRightY = horizonY * 0.40f
        canvas.drawCircle(flRightX, flRightY, 14f, floodlightPaint)
        val beamPathRight = Path().apply {
            moveTo(flRightX, flRightY)
            lineTo(projectX(4.5f, 2.0f), projectY(0f, 2.0f))
            lineTo(projectX(1.5f, 7.5f), projectY(0f, 7.5f))
            close()
        }
        lightBeamPaint.shader = LinearGradient(flRightX, flRightY, projectX(3.0f, 5.0f), projectY(0f, 5.0f), Color.argb(40, 254, 240, 138), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(beamPathRight, lightBeamPaint)

        // 4. 3D LED Stadium Advertising Boards with Official "AZEZY.COM" Branding
        val bLeftX = projectX(-6.5f, goalZ + 0.5f)
        val bRightX = projectX(6.5f, goalZ + 0.5f)
        val bBottomY = projectY(0f, goalZ + 0.5f)
        val bTopY = projectY(0.7f, goalZ + 0.5f)
        canvas.drawRect(bLeftX, bTopY, bRightX, bBottomY, boardPaint)

        // Draw Glowing Brand Text on LED Board
        val ledTextScale = projectScale(goalZ + 0.5f)
        ledBrandPaint.textSize = (w * 0.038f) * ledTextScale
        val brandY = bBottomY - (bBottomY - bTopY) * 0.32f
        canvas.drawText("★ AZEZY.COM • OFFICIAL 3D GAME ★", pitchCenterBottomX, brandY, ledBrandPaint)

        // 5. Draw 3D Pitch Lines (Goal line, Penalty area, Penalty spot)
        val goalLineY = projectY(0f, goalZ)
        linePaint.strokeWidth = 4f
        canvas.drawLine(w * 0.10f, goalLineY, w * 0.90f, goalLineY, linePaint)

        // 16m50 Penalty Box
        val pBoxLeftX = projectX(-3.8f, goalZ)
        val pBoxRightX = projectX(3.8f, goalZ)
        val pBoxFrontLeftX = projectX(-3.8f, 2.0f)
        val pBoxFrontRightX = projectX(3.8f, 2.0f)
        val pBoxFrontY = projectY(0f, 2.0f)
        canvas.drawLine(pBoxLeftX, goalLineY, pBoxFrontLeftX, pBoxFrontY, linePaint)
        canvas.drawLine(pBoxRightX, goalLineY, pBoxFrontRightX, pBoxFrontY, linePaint)
        canvas.drawLine(pBoxFrontLeftX, pBoxFrontY, pBoxFrontRightX, pBoxFrontY, linePaint)

        // Penalty Spot (z = 0m)
        val spotX = projectX(0f, 0f)
        val spotY = projectY(0f, 0f)
        canvas.drawCircle(spotX, spotY, 7.5f, linePaint)

        // 6. Draw 3D Golden Goal (Cylindrical Metallic Posts + 3D Depth Net)
        val gLeftX = projectX(-goalHalfWidth, goalZ)
        val gRightX = projectX(goalHalfWidth, goalZ)
        val gBottomY = projectY(0f, goalZ)
        val gTopY = projectY(goalHeight, goalZ)

        val gBackLeftX = projectX(-goalHalfWidth, goalZ + goalDepth)
        val gBackRightX = projectX(goalHalfWidth, goalZ + goalDepth)
        val gBackBottomY = projectY(0f, goalZ + goalDepth)
        val gBackTopY = projectY(goalHeight, goalZ + goalDepth)

        // 3D Net Back Shade Fill
        netPath.reset()
        netPath.moveTo(gBackLeftX, gBackBottomY)
        netPath.lineTo(gBackRightX, gBackBottomY)
        netPath.lineTo(gBackRightX, gBackTopY)
        netPath.lineTo(gBackLeftX, gBackTopY)
        netPath.close()
        canvas.drawPath(netPath, netBackFillPaint)

        // 3D Net Lattice
        val netCols = 10
        val netRows = 6
        for (c in 0..netCols) {
            val t = c.toFloat() / netCols
            val nx1 = gBackLeftX + (gBackRightX - gBackLeftX) * t
            val nx2 = gLeftX + (gRightX - gLeftX) * t
            canvas.drawLine(nx1, gBackBottomY, nx1, gBackTopY, netPaint)
            canvas.drawLine(nx1, gBackTopY, nx2, gTopY, netPaint)
        }
        for (r in 0..netRows) {
            val t = r.toFloat() / netRows
            val nyBack = gBackBottomY + (gBackTopY - gBackBottomY) * t
            canvas.drawLine(gBackLeftX, nyBack, gBackRightX, nyBack, netPaint)
        }

        // Side 3D Net Walls
        netPath.reset()
        netPath.moveTo(gLeftX, gBottomY)
        netPath.lineTo(gBackLeftX, gBackBottomY)
        netPath.lineTo(gBackLeftX, gBackTopY)
        netPath.lineTo(gLeftX, gTopY)
        netPath.close()
        canvas.drawPath(netPath, netPaint)

        netPath.reset()
        netPath.moveTo(gRightX, gBottomY)
        netPath.lineTo(gBackRightX, gBackBottomY)
        netPath.lineTo(gBackRightX, gBackTopY)
        netPath.lineTo(gRightX, gTopY)
        netPath.close()
        canvas.drawPath(netPath, netPaint)

        // 3D Depth Support Bars (From front posts to back ground)
        canvas.drawLine(gLeftX, gTopY, gBackLeftX, gBackTopY, goldDarkPaint)
        canvas.drawLine(gRightX, gTopY, gBackRightX, gBackTopY, goldDarkPaint)
        canvas.drawLine(gLeftX, gBottomY, gBackLeftX, gBackBottomY, goldDarkPaint)
        canvas.drawLine(gRightX, gBottomY, gBackRightX, gBackBottomY, goldDarkPaint)

        // 3D Cylindrical Golden Posts & Crossbar with Metallic Highlights
        val postThickness = 15f * projectScale(goalZ)

        // Left Post
        rectF.set(gLeftX - postThickness / 2f, gTopY - postThickness / 2f, gLeftX + postThickness / 2f, gBottomY)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gLeftX - postThickness / 4f, gTopY, gLeftX - postThickness / 4f, gBottomY, goldHighlightPaint)
        canvas.drawLine(gLeftX + postThickness / 3f, gTopY, gLeftX + postThickness / 3f, gBottomY, goldDarkPaint)

        // Right Post
        rectF.set(gRightX - postThickness / 2f, gTopY - postThickness / 2f, gRightX + postThickness / 2f, gBottomY)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gRightX - postThickness / 4f, gTopY, gRightX - postThickness / 4f, gBottomY, goldHighlightPaint)
        canvas.drawLine(gRightX + postThickness / 3f, gTopY, gRightX + postThickness / 3f, gBottomY, goldDarkPaint)

        // Top Crossbar
        rectF.set(gLeftX - postThickness / 2f, gTopY - postThickness / 2f, gRightX + postThickness / 2f, gTopY + postThickness / 2f)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gLeftX, gTopY - postThickness / 4f, gRightX, gTopY - postThickness / 4f, goldHighlightPaint)

        // Golden Shimmer Light Wave on Crossbar (Ánh sáng kim loại chạy dọc xà ngang)
        goldShimmer = (goldShimmer + dt * 1.3f) % 1.0f
        val shimmerX = gLeftX + (gRightX - gLeftX) * goldShimmer
        canvas.drawLine(shimmerX - 26f, gTopY, shimmerX + 26f, gTopY, goldShimmerPaint)

        // 3D Elbow Joints at Corners
        canvas.drawCircle(gLeftX, gTopY, postThickness * 0.65f, goldDarkPaint)
        canvas.drawCircle(gRightX, gTopY, postThickness * 0.65f, goldDarkPaint)

        // 7. Draw Mascot Goalkeeper (If active)
        if (goalkeeperEnabled) {
            val gkScreenX = projectX(gkX, gkZ)
            val gkScreenY = projectY(0f, gkZ)
            val gkScale = projectScale(gkZ)
            val gkW = (w * 0.16f) * gkScale
            val gkH = (w * 0.22f) * gkScale

            // Shadow
            rectF.set(gkScreenX - gkW * 0.4f, gkScreenY - 6f, gkScreenX + gkW * 0.4f, gkScreenY + 6f)
            canvas.drawOval(rectF, shadowPaint)

            // 3D Shaded Mascot Body
            rectF.set(gkScreenX - gkW * 0.35f, gkScreenY - gkH * 0.75f, gkScreenX + gkW * 0.35f, gkScreenY - gkH * 0.15f)
            canvas.drawRoundRect(rectF, 12f, 12f, gkBodyPaint)

            // Head
            canvas.drawCircle(gkScreenX, gkScreenY - gkH * 0.85f, gkW * 0.26f, gkHeadPaint)

            // Big Animated Gloves
            canvas.drawCircle(gkScreenX - gkW * 0.52f, gkScreenY - gkH * 0.55f, gkW * 0.20f, gkGlovePaint)
            canvas.drawCircle(gkScreenX + gkW * 0.52f, gkScreenY - gkH * 0.55f, gkW * 0.20f, gkGlovePaint)
        }

        // 8. Draw 3D Trajectory Aiming Line & Pulsing 3D Bullseye Reticle
        if (isAiming && (ballState == BallState.IDLE || ballState == BallState.AIMING)) {
            var simX = ballX
            var simY = ballY
            var simZ = ballZ
            var simVx = aimVx
            var simVy = aimVy
            var simVz = aimVz
            val path = Path()
            path.moveTo(projectX(simX, simZ), projectY(simY, simZ))
            var targetX = 0f
            var targetY = 1.2f
            for (i in 0 until 22) {
                simVy -= 9.8f * 0.04f
                simVx += aimCurve * 0.08f * 0.04f
                simX += simVx * 0.04f
                simY += simVy * 0.04f
                simZ += simVz * 0.04f
                if (simZ >= goalZ && simZ <= goalZ + 0.6f) {
                    targetX = simX
                    targetY = simY
                }
                if (simY < 0.1f || simZ > goalZ + 1.2f) break
                path.lineTo(projectX(simX, simZ), projectY(simY, simZ))
            }
            canvas.drawPath(path, trajectoryPaint)

            // 3D Concentric Glowing Bullseye Target Reticle at Impact Point
            targetPulse = (targetPulse + dt * 6.0f) % (Math.PI * 2).toFloat()
            val targetScreenX = projectX(targetX, goalZ)
            val targetScreenY = projectY(targetY.coerceIn(0.25f, goalHeight), goalZ)
            val pulseScale = 1.0f + 0.16f * sin(targetPulse)
            val reticleR = 24f * projectScale(goalZ) * pulseScale

            canvas.drawCircle(targetScreenX, targetScreenY, reticleR, reticlePaint)
            canvas.drawCircle(targetScreenX, targetScreenY, reticleR * 0.5f, reticlePaint)
            canvas.drawCircle(targetScreenX, targetScreenY, 4f, reticlePaint)
            canvas.drawLine(targetScreenX - reticleR * 1.3f, targetScreenY, targetScreenX - reticleR * 0.7f, targetScreenY, reticlePaint)
            canvas.drawLine(targetScreenX + reticleR * 0.7f, targetScreenY, targetScreenX + reticleR * 1.3f, targetScreenY, reticlePaint)
            canvas.drawLine(targetScreenX, targetScreenY - reticleR * 1.3f, targetScreenX, targetScreenY - reticleR * 0.7f, reticlePaint)
            canvas.drawLine(targetScreenX, targetScreenY + reticleR * 0.7f, targetScreenX, targetScreenY + reticleR * 1.3f, reticlePaint)
        }

        // 9. Draw Fiery 3D Glowing Ball Ribbon Trail (Vệt lửa sao băng khi bóng bay)
        for (i in 1 until ballTrail.size) {
            val p1 = ballTrail[i - 1]
            val p2 = ballTrail[i]
            if (p1.alpha > 0.04f && p2.alpha > 0.04f) {
                val s1x = projectX(p1.x, p1.z)
                val s1y = projectY(p1.y, p1.z)
                val s2x = projectX(p2.x, p2.z)
                val s2y = projectY(p2.y, p2.z)
                val tAlpha = ((p1.alpha) * 220).toInt().coerceIn(0, 255)

                // Outer fiery orange glow
                trailPaint.strokeWidth = (p1.radius * 1.15f).coerceAtLeast(3f)
                trailPaint.color = Color.argb(tAlpha, 251, 146, 60)
                canvas.drawLine(s1x, s1y, s2x, s2y, trailPaint)

                // Inner blazing golden core
                trailPaint.strokeWidth = (p1.radius * 0.55f).coerceAtLeast(1.5f)
                trailPaint.color = Color.argb(tAlpha, 254, 240, 138)
                canvas.drawLine(s1x, s1y, s2x, s2y, trailPaint)
            }
        }

        // 10. Draw Volumetric 3D Soccer Ball & Dynamic Ground Shadow
        val bScale = projectScale(ballZ)
        val bScreenX = projectX(ballX, ballZ)
        val bScreenY = projectY(ballY, ballZ)
        val bGroundY = projectY(0f, ballZ)
        val bRadius = (w * 0.095f) * bScale

        if (bRadius > 1.0f) {
            // Dynamic 3D Ground Shadow (Shrinks and softens with height)
            val shadowHeightFactor = (1f - (ballY / 3.8f)).coerceIn(0.25f, 1.0f)
            val shadowRadiusX = bRadius * 1.18f * shadowHeightFactor
            val shadowRadiusY = bRadius * 0.38f * shadowHeightFactor
            rectF.set(bScreenX - shadowRadiusX, bGroundY - shadowRadiusY, bScreenX + shadowRadiusX, bGroundY + shadowRadiusY)
            shadowPaint.alpha = (75 * shadowHeightFactor).toInt()
            canvas.drawOval(rectF, shadowPaint)

            // 3D Sphere Radial Shading
            try {
                ball3DShadePaint.shader = RadialGradient(
                    bScreenX - bRadius * 0.32f, bScreenY - bRadius * 0.32f, (bRadius * 1.28f).coerceAtLeast(1.0f),
                    intArrayOf(
                        Color.rgb(255, 255, 255), // Sun highlight
                        Color.rgb(241, 245, 249), // Mid white leather
                        Color.rgb(203, 213, 225), // Ambient occlusion shadow
                        Color.rgb(148, 163, 184)  // Rim shadow
                    ),
                    floatArrayOf(0f, 0.45f, 0.85f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            } catch (_: Exception) {
            }

            // Draw Base Sphere
            canvas.drawCircle(bScreenX, bScreenY, bRadius, ball3DShadePaint)

            // Rotating 3D Pentagons (Telstar Geometric Masterpiece)
            canvas.save()
            canvas.translate(bScreenX, bScreenY)
            canvas.rotate(ballRotationAngle)

            // Central Black Pentagon
            val centerPatchR = bRadius * 0.38f
            drawPentagon(canvas, 0f, 0f, centerPatchR, ballBlackPaint, ballGoldSeamPaint)

            // 5 Surrounding Radial Pentagons with Golden Seams
            val surroundR = bRadius * 0.32f
            val surroundDist = bRadius * 0.62f
            for (i in 0 until 5) {
                val angleRad = (i * 72.0 - 18.0) * (PI / 180.0)
                val px = (surroundDist * cos(angleRad)).toFloat()
                val py = (surroundDist * sin(angleRad)).toFloat()
                drawPentagon(canvas, px, py, surroundR, ballBlackPaint, ballGoldSeamPaint)

                // Golden connecting seam line to center pentagon
                val centerCornerAngle = (i * 72.0 - 18.0) * (PI / 180.0)
                val cx = (centerPatchR * cos(centerCornerAngle)).toFloat()
                val cy = (centerPatchR * sin(centerCornerAngle)).toFloat()
                canvas.drawLine(cx, cy, px, py, ballGoldSeamPaint)
            }

            // Center Gold Star Emblem
            ballPatchPath.reset()
            for (p in 0 until 5) {
                val pAngle = (p * 72.0 - 18.0) * (PI / 180.0)
                val x = (centerPatchR * 0.55f * cos(pAngle)).toFloat()
                val y = (centerPatchR * 0.55f * sin(pAngle)).toFloat()
                if (p == 0) ballPatchPath.moveTo(x, y) else ballPatchPath.lineTo(x, y)
            }
            ballPatchPath.close()
            canvas.drawPath(ballPatchPath, goldPostPaint)

            // Dual Glossy Specular 3D Highlights
            canvas.drawCircle(-bRadius * 0.35f, -bRadius * 0.35f, bRadius * 0.22f, ballGlossPaint)
            canvas.restore()
        }

        // 11. Goal Shockwave Blast Ring (Sóng kích nổ khi ghi bàn)
        if (shockwaveActive) {
            shockwaveRadius += 520f * dt
            shockwaveAlpha = (255 * (1f - shockwaveRadius / 260f)).toInt().coerceIn(0, 255)
            if (shockwaveRadius >= 260f || shockwaveAlpha <= 0) {
                shockwaveActive = false
            } else {
                shockwavePaint.color = Color.argb(shockwaveAlpha, 251, 191, 36)
                shockwavePaint.strokeWidth = 8f * (1f - shockwaveRadius / 260f)
                canvas.drawCircle(shockwaveX, shockwaveY, shockwaveRadius, shockwavePaint)

                shockwavePaint.color = Color.argb(shockwaveAlpha / 2, 254, 240, 138)
                shockwavePaint.strokeWidth = 4f
                canvas.drawCircle(shockwaveX, shockwaveY, shockwaveRadius * 0.70f, shockwavePaint)
            }
        }

        // 12. Draw Celebration Confetti, Diamonds & Golden Stars
        for (p in particles) {
            if (p.active) {
                particlePaint.color = p.color
                particlePaint.alpha = p.alpha
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)
                if (p.isStar) {
                    // Star shape
                    canvas.drawRect(-p.size * 0.6f, -p.size * 0.6f, p.size * 0.6f, p.size * 0.6f, particlePaint)
                } else {
                    // Confetti ribbon
                    canvas.drawRoundRect(RectF(-p.size, -p.size * 0.4f, p.size, p.size * 0.4f), 3f, 3f, particlePaint)
                }
                canvas.restore()
            }
        }

        canvas.restore() // Restore screen shake
        invalidate()
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        canvas.drawCircle(cx, cy, 26f * scale, cloudPaint)
        canvas.drawCircle(cx - 24f * scale, cy + 3f * scale, 20f * scale, cloudPaint)
        canvas.drawCircle(cx + 24f * scale, cy + 3f * scale, 20f * scale, cloudPaint)
        canvas.drawCircle(cx - 12f * scale, cy - 12f * scale, 18f * scale, cloudPaint)
        canvas.drawCircle(cx + 12f * scale, cy - 12f * scale, 18f * scale, cloudPaint)
    }

    private fun drawPentagon(canvas: Canvas, cx: Float, cy: Float, radius: Float, fillPaint: Paint, strokePaint: Paint) {
        val path = Path()
        for (i in 0 until 5) {
            val angle = (i * 72.0 - 18.0) * (PI / 180.0)
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    private fun updatePhysics(dt: Float) {
        // Gentle animated cloud drifting across sky
        cloudDrift1 += 5.5f * dt
        cloudDrift2 += 3.8f * dt
        cloudDrift3 += 2.6f * dt

        // Goalkeeper movement
        if (goalkeeperEnabled) {
            gkX += gkDir * gkSpeed * dt
            val maxGkX = goalHalfWidth - 0.7f
            if (gkX > maxGkX) {
                gkX = maxGkX
                gkDir = -1f
            } else if (gkX < -maxGkX) {
                gkX = -maxGkX
                gkDir = 1f
            }
        }

        if (ballState != BallState.FLYING && ballState != BallState.SCORED && ballState != BallState.MISSED) {
            return
        }

        flightTime += dt

        // Gentle realistic Magnus curve force
        ballVx += ballSpinY * ballVz * 0.08f * dt

        // Gravity
        ballVy -= 9.81f * dt

        // Air drag
        ballVx *= (1f - 0.05f * dt)
        ballVy *= (1f - 0.05f * dt)
        ballVz *= (1f - 0.05f * dt)

        // Integrate 3D position
        ballX += ballVx * dt
        ballY += ballVy * dt
        ballZ += ballVz * dt

        // 3D Visual Rotation
        val speed = hypot(ballVx.toDouble(), ballVz.toDouble()).toFloat()
        ballRotationAngle += speed * 35f * dt

        // Ground Collision & Bounce
        if (ballY <= ballRadius) {
            ballY = ballRadius
            if (abs(ballVy) > 0.4f) {
                ballVy = -ballVy * 0.60f
                ballVx *= 0.85f
                ballVz *= 0.85f
            } else {
                ballVy = 0f
                ballVx *= (1f - 2.5f * dt)
                ballVz *= (1f - 2.5f * dt)
            }
        }

        // Goalkeeper 3D Collision
        if (goalkeeperEnabled && ballState == BallState.FLYING && ballZ >= (gkZ - 0.3f) && ballZ <= (gkZ + 0.3f)) {
            val inGkX = abs(ballX - gkX) < (gkWidth / 2f + ballRadius)
            val inGkY = ballY >= 0f && ballY <= (gkHeight + ballRadius)
            if (inGkX && inGkY) {
                ballVx = (ballX - gkX) * 4f + (if (ballVx > 0) -2f else 2f)
                ballVy = abs(ballVy) * 0.5f + 2f
                ballVz = -ballVz * 0.4f
                ballState = BallState.MISSED
                soundEngine.playPostHit()
                soundEngine.playMiss()
                mainHandler.post { gameManager.recordMiss() }
                mainHandler.postDelayed({ resetBall() }, 1800)
                return
            }
        }

        // Goal Posts & Crossbar 3D Collision
        if (abs(ballZ - goalZ) < (0.15f + ballRadius) && ballY <= (goalHeight + ballRadius + 0.1f)) {
            // Left Post: x = -goalHalfWidth
            if (abs(ballX - (-goalHalfWidth)) < (0.12f + ballRadius) && ballY <= goalHeight) {
                ballVx = abs(ballVx) * 0.75f + 1.5f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
            // Right Post: x = goalHalfWidth
            if (abs(ballX - goalHalfWidth) < (0.12f + ballRadius) && ballY <= goalHeight) {
                ballVx = -abs(ballVx) * 0.75f - 1.5f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
            // Top Crossbar: y = goalHeight
            if (abs(ballX) <= goalHalfWidth + 0.1f && abs(ballY - goalHeight) < (0.12f + ballRadius)) {
                ballVy = -abs(ballVy) * 0.70f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
        }

        // 3D Goal Detection
        if (ballState == BallState.FLYING) {
            if (ballZ >= goalZ) {
                val insideWidth = abs(ballX) < (goalHalfWidth - 0.15f)
                val insideHeight = ballY > 0.05f && ballY < (goalHeight - 0.15f)
                if (insideWidth && insideHeight) {
                    ballState = BallState.SCORED
                    soundEngine.playGoal()
                    spawnConfetti()
                    mainHandler.post { gameManager.recordGoal(ballX, ballY) }
                    mainHandler.postDelayed({ resetBall() }, 2200)
                } else if (ballZ > goalZ + 0.4f) {
                    ballState = BallState.MISSED
                    soundEngine.playMiss()
                    mainHandler.post { gameManager.recordMiss() }
                    mainHandler.postDelayed({ resetBall() }, 1800)
                }
            }
        }

        // Settle ball inside 3D net
        if (ballState == BallState.SCORED) {
            if (ballZ >= (goalZ + goalDepth - ballRadius)) {
                ballZ = goalZ + goalDepth - ballRadius
                ballVz = -abs(ballVz) * 0.15f
            }
            ballVx *= (1f - 4f * dt)
            ballVy *= (1f - 4f * dt)
            ballVz *= (1f - 4f * dt)
        }

        // Miss timeout
        if (ballState == BallState.FLYING) {
            if (flightTime > 3.0f || ballZ > (goalZ + 3.0f) || (ballY <= ballRadius + 0.01f && abs(ballVz) < 0.2f && ballZ < goalZ)) {
                ballState = BallState.MISSED
                soundEngine.playMiss()
                mainHandler.post { gameManager.recordMiss() }
                mainHandler.postDelayed({ resetBall() }, 1800)
            }
        }
    }

    private fun spawnConfetti() {
        val w = width.toFloat()
        val h = height.toFloat()
        screenShake = 1.0f
        shockwaveActive = true
        shockwaveRadius = 0f
        val fov = 7.2f
        val scale = fov / (fov + goalZ + camFollowZ)
        shockwaveX = (w * 0.5f - (camFollowX * 22f)) + ballX * (w * 0.29f) * scale
        val horizonY = h * 0.43f + (camFollowY * 18f)
        val pitchCenterBottomY = h * 0.84f + (camFollowY * 15f)
        val groundY = horizonY + (pitchCenterBottomY - horizonY) * scale
        shockwaveY = groundY - ballY * (w * 0.29f) * scale

        val colors = intArrayOf(
            Color.rgb(251, 191, 36),  // Gold
            Color.rgb(244, 63, 94),   // Crimson Pink
            Color.rgb(56, 189, 248),  // Cyan
            Color.rgb(74, 222, 128),  // Lime
            Color.rgb(251, 146, 60),  // Orange
            Color.rgb(253, 224, 71),  // Sunlight Yellow
            Color.rgb(255, 255, 255)  // Star White
        )
        for (p in particles) {
            p.active = true
            p.x = shockwaveX + (Random.nextFloat() - 0.5f) * 60f
            p.y = shockwaveY + (Random.nextFloat() - 0.5f) * 40f
            val speed = 280f + Random.nextFloat() * 520f
            val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - 180f
            p.color = colors[Random.nextInt(colors.size)]
            p.size = 8f + Random.nextFloat() * 15f
            p.maxLife = 1.6f + Random.nextFloat() * 0.8f
            p.life = p.maxLife
            p.alpha = 255
            p.rotation = Random.nextFloat() * 360f
            p.vRot = (Random.nextFloat() - 0.5f) * 400f
            p.isStar = Random.nextBoolean()
        }
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (p.active) {
                p.life -= dt
                if (p.life <= 0f) {
                    p.active = false
                    continue
                }
                p.vy += 420f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rotation += p.vRot * dt
                p.alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            }
        }
    }

    fun resetBall() {
        val nextPos = gameManager.getNextBallPosition()
        ballX = nextPos.first
        ballY = ballRadius
        ballZ = 0f
        ballVx = 0f
        ballVy = 0f
        ballVz = 0f
        ballSpinY = 0f
        ballState = BallState.IDLE
        flightTime = 0f
        postHitSoundPlayed = false
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ballState != BallState.IDLE && ballState != BallState.AIMING) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onFirstTouch?.invoke()
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = System.currentTimeMillis()
                touchPoints.clear()
                touchPoints.add(PointF(event.x, event.y))

                if (controlMode == ControlMode.SLINGSHOT_AIM) {
                    isAiming = true
                    ballState = BallState.AIMING
                    updateAiming(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchPoints.add(PointF(event.x, event.y))
                if (controlMode == ControlMode.SLINGSHOT_AIM) {
                    updateAiming(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchPoints.add(PointF(event.x, event.y))
                if (controlMode == ControlMode.SWIPE_FLICK) {
                    handleSwipeKick(event.x, event.y)
                } else {
                    handleSlingshotRelease(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isAiming = false
                if (ballState == BallState.AIMING) ballState = BallState.IDLE
                touchPoints.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleSwipeKick(endX: Float, endY: Float) {
        val dx = endX - touchDownX
        val dy = endY - touchDownY
        val dt = (System.currentTimeMillis() - touchDownTime).coerceIn(40L, 450L).toFloat() / 1000f

        // Sút bóng khi vuốt lên (dy < -25px)
        if (dy < -25f) {
            val screenH = height.toFloat()
            val screenW = width.toFloat()

            val forwardSpeedNorm = (-dy / screenH) / dt
            val vz = (13.5f + forwardSpeedNorm * 2.8f).coerceIn(13.5f, 25.0f)
            val vy = (4.2f + forwardSpeedNorm * 1.5f).coerceIn(4.0f, 8.5f)

            // ĐỒNG BỘ 1-1 CHUẨN XÁC: Vuốt trái sang TRÁI (vx < 0), vuốt phải sang PHẢI (vx > 0)
            val horizontalRatio = dx / (screenW * 0.45f)
            val vx = (horizontalRatio * 6.0f).coerceIn(-6.0f, 6.0f)

            executeKick(vx, vy, vz, 0f)
        }
        touchPoints.clear()
    }

    private fun updateAiming(curX: Float, curY: Float) {
        val dx = curX - touchDownX
        val dy = curY - touchDownY
        val screenH = height.toFloat()
        val screenW = width.toFloat()

        // KÉO NGẮM TRỰC QUAN: Kéo bóng lùi xuống và hướng sang trái -> Sút sang TRÁI
        // Kéo bóng lùi xuống và hướng sang phải -> Sút sang PHẢI
        val pullY = dy.coerceAtLeast(0f) / screenH
        val pullX = dx / (screenW * 0.45f)

        val power = (pullY * 3.0f).coerceIn(0.2f, 1.2f)
        aimVz = 13.0f + power * 12.0f
        aimVy = 4.2f + power * 4.2f
        aimVx = (pullX * 6.0f).coerceIn(-6.0f, 6.0f)
        aimCurve = 0f
        invalidate()
    }

    private fun handleSlingshotRelease(endX: Float, endY: Float) {
        isAiming = false
        val dy = endY - touchDownY
        if (dy > 25f) {
            updateAiming(endX, endY)
            executeKick(aimVx, aimVy, aimVz, aimCurve)
        } else {
            ballState = BallState.IDLE
        }
        touchPoints.clear()
    }

    private fun executeKick(vx: Float, vy: Float, vz: Float, curve: Float) {
        if (ballState != BallState.IDLE && ballState != BallState.AIMING) return
        soundEngine.playKick()
        ballVx = vx
        ballVy = vy
        ballVz = vz
        ballSpinY = curve
        ballState = BallState.FLYING
        flightTime = 0f
        postHitSoundPlayed = false
    }
}
