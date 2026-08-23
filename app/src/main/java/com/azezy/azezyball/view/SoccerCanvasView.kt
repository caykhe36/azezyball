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

    // 3D Physics Simulation Coordinates
    val ballRadius = 0.22f // meters
    var ballX = 0f
    var ballY = ballRadius
    var ballZ = 0f // 0m is starting spot, 10.5m is the goal line!

    var ballVx = 0f
    var ballVy = 0f
    var ballVz = 0f
    var ballSpinY = 0f
    var ballRotationAngle = 0f

    var ballState = BallState.IDLE
    private var flightTime = 0f
    private var postHitSoundPlayed = false

    // Goal Dimensions in 3D
    val goalZ = 10.5f
    val goalHalfWidth = 2.6f
    val goalHeight = 2.4f
    val goalDepth = 1.8f

    // Goalkeeper 3D Properties
    var gkX = 0f
    var gkSpeed = 2.2f
    var gkDir = 1f
    val gkWidth = 1.4f
    val gkHeight = 1.8f
    val gkZ = 10.2f

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

    // Celebration Confetti Particles
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
    }
    private val particles = Array(120) { Particle() }

    // Pre-allocated Paints to guarantee ZERO Garbage Collection during onDraw
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grassPaint1 = Paint()
    private val grassPaint2 = Paint()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldPostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val netPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val netBackFillPaint = Paint()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballGoldSeamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkGlovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gkHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        cloudPaint.color = Color.argb(220, 255, 255, 255)

        grassPaint1.color = Color.rgb(74, 222, 128) // Bright Lime Green
        grassPaint2.color = Color.rgb(34, 197, 94)  // Vivid Emerald Green

        linePaint.apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        goldPostPaint.apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.FILL
        }

        goldHighlightPaint.apply {
            color = Color.rgb(254, 240, 138)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        netPaint.apply {
            color = Color.argb(160, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        netBackFillPaint.color = Color.argb(45, 15, 23, 42)

        shadowPaint.apply {
            color = Color.argb(70, 0, 0, 0)
            style = Paint.Style.FILL
        }

        ballWhitePaint.apply {
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
            color = Color.argb(140, 255, 255, 255)
            style = Paint.Style.FILL
        }

        gkBodyPaint.apply {
            color = Color.rgb(14, 165, 233) // Bright Sky Cyan
            style = Paint.Style.FILL
        }

        gkGlovePaint.apply {
            color = Color.rgb(249, 115, 22) // Bright Orange
            style = Paint.Style.FILL
        }

        gkHeadPaint.apply {
            color = Color.rgb(254, 215, 170)
            style = Paint.Style.FILL
        }

        trajectoryPaint.apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            pathEffect = DashPathEffect(floatArrayOf(16f, 12f), 0f)
        }

        particlePaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Sky gradient
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f,
            intArrayOf(
                Color.rgb(14, 165, 233),  // Azure
                Color.rgb(56, 189, 248),  // Sky Blue
                Color.rgb(186, 230, 253), // Soft Cyan
                Color.rgb(254, 240, 138)  // Warm Golden Horizon
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
        val horizonY = h * 0.40f
        val pitchCenterBottomY = h * 0.82f
        val pitchCenterBottomX = w * 0.5f

        // 1. Draw Bright Cartoon Sky
        canvas.drawRect(0f, 0f, w, horizonY, skyPaint)
        // Sun
        canvas.drawCircle(w * 0.82f, horizonY * 0.35f, 32f, sunPaint)
        // Clouds
        drawCloud(canvas, w * 0.2f, horizonY * 0.4f, 1.0f)
        drawCloud(canvas, w * 0.65f, horizonY * 0.3f, 0.85f)

        // 2. Draw 3D Cartoon Perspective Pitch (Emerald Grass Lawn Stripes)
        val numStripes = 8
        val grassH = h - horizonY
        for (i in 0 until numStripes) {
            val t1 = (i.toFloat() / numStripes)
            val t2 = ((i + 1).toFloat() / numStripes)
            val y1 = horizonY + grassH * (t1 * t1)
            val y2 = horizonY + grassH * (t2 * t2)
            val paint = if (i % 2 == 0) grassPaint1 else grassPaint2
            canvas.drawRect(0f, y1, w, y2, paint)
        }

        // Perspective Conversion Helper: (3D World -> 2D Screen)
        val fov = 7.5f
        fun projectX(wx: Float, wz: Float): Float {
            val scale = fov / (fov + wz)
            return pitchCenterBottomX + wx * (w * 0.28f) * scale
        }
        fun projectY(wy: Float, wz: Float): Float {
            val scale = fov / (fov + wz)
            val groundY = horizonY + (pitchCenterBottomY - horizonY) * scale
            return groundY - wy * (w * 0.28f) * scale
        }
        fun projectScale(wz: Float): Float {
            return fov / (fov + wz)
        }

        // 3. Draw Pitch Markings (Penalty Box, Penalty Spot, Goal Line)
        val goalLineY = projectY(0f, goalZ)
        linePaint.strokeWidth = 4f
        canvas.drawLine(w * 0.12f, goalLineY, w * 0.88f, goalLineY, linePaint)

        // 16m50 Penalty Box
        val pBoxLeftX = projectX(-3.8f, goalZ)
        val pBoxRightX = projectX(3.8f, goalZ)
        val pBoxFrontLeftX = projectX(-3.8f, 2.0f)
        val pBoxFrontRightX = projectX(3.8f, 2.0f)
        val pBoxFrontY = projectY(0f, 2.0f)
        canvas.drawLine(pBoxLeftX, goalLineY, pBoxFrontLeftX, pBoxFrontY, linePaint)
        canvas.drawLine(pBoxRightX, goalLineY, pBoxFrontRightX, pBoxFrontY, linePaint)
        canvas.drawLine(pBoxFrontLeftX, pBoxFrontY, pBoxFrontRightX, pBoxFrontY, linePaint)

        // Penalty Spot (Where ball starts at z = 0m)
        val spotX = projectX(0f, 0f)
        val spotY = projectY(0f, 0f)
        canvas.drawCircle(spotX, spotY, 7f, linePaint)

        // 4. Draw 3D Golden Goal (Back Net, Frame, Net Lattice)
        val gLeftX = projectX(-goalHalfWidth, goalZ)
        val gRightX = projectX(goalHalfWidth, goalZ)
        val gBottomY = projectY(0f, goalZ)
        val gTopY = projectY(goalHeight, goalZ)

        val gBackLeftX = projectX(-goalHalfWidth, goalZ + goalDepth)
        val gBackRightX = projectX(goalHalfWidth, goalZ + goalDepth)
        val gBackBottomY = projectY(0f, goalZ + goalDepth)
        val gBackTopY = projectY(goalHeight, goalZ + goalDepth)

        // Net Back Plane Fill
        netPath.reset()
        netPath.moveTo(gBackLeftX, gBackBottomY)
        netPath.lineTo(gBackRightX, gBackBottomY)
        netPath.lineTo(gBackRightX, gBackTopY)
        netPath.lineTo(gBackLeftX, gBackTopY)
        netPath.close()
        canvas.drawPath(netPath, netBackFillPaint)

        // Net Grid Lines
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

        // Side Net Panels
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

        // Glossy Golden Goal Frame Posts & Crossbar
        val postThickness = 14f * projectScale(goalZ)
        // Left Post
        rectF.set(gLeftX - postThickness / 2f, gTopY - postThickness / 2f, gLeftX + postThickness / 2f, gBottomY)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gLeftX - postThickness / 4f, gTopY, gLeftX - postThickness / 4f, gBottomY, goldHighlightPaint)

        // Right Post
        rectF.set(gRightX - postThickness / 2f, gTopY - postThickness / 2f, gRightX + postThickness / 2f, gBottomY)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gRightX - postThickness / 4f, gTopY, gRightX - postThickness / 4f, gBottomY, goldHighlightPaint)

        // Top Crossbar
        rectF.set(gLeftX - postThickness / 2f, gTopY - postThickness / 2f, gRightX + postThickness / 2f, gTopY + postThickness / 2f)
        canvas.drawRoundRect(rectF, 6f, 6f, goldPostPaint)
        canvas.drawLine(gLeftX, gTopY - postThickness / 4f, gRightX, gTopY - postThickness / 4f, goldHighlightPaint)

        // 5. Draw Mascot Goalkeeper (If active)
        if (goalkeeperEnabled) {
            val gkScreenX = projectX(gkX, gkZ)
            val gkScreenY = projectY(0f, gkZ)
            val gkScale = projectScale(gkZ)
            val gkW = (w * 0.16f) * gkScale
            val gkH = (w * 0.22f) * gkScale

            // Shadow
            rectF.set(gkScreenX - gkW * 0.4f, gkScreenY - 6f, gkScreenX + gkW * 0.4f, gkScreenY + 6f)
            canvas.drawOval(rectF, shadowPaint)

            // Body
            rectF.set(gkScreenX - gkW * 0.35f, gkScreenY - gkH * 0.75f, gkScreenX + gkW * 0.35f, gkScreenY - gkH * 0.15f)
            canvas.drawRoundRect(rectF, 12f, 12f, gkBodyPaint)

            // Head
            canvas.drawCircle(gkScreenX, gkScreenY - gkH * 0.85f, gkW * 0.26f, gkHeadPaint)

            // Big Gloves
            canvas.drawCircle(gkScreenX - gkW * 0.52f, gkScreenY - gkH * 0.55f, gkW * 0.20f, gkGlovePaint)
            canvas.drawCircle(gkScreenX + gkW * 0.52f, gkScreenY - gkH * 0.55f, gkW * 0.20f, gkGlovePaint)
        }

        // 6. Draw Trajectory Line when Aiming
        if (isAiming && (ballState == BallState.IDLE || ballState == BallState.AIMING)) {
            var simX = ballX
            var simY = ballY
            var simZ = ballZ
            var simVx = aimVx
            var simVy = aimVy
            var simVz = aimVz
            val path = Path()
            path.moveTo(projectX(simX, simZ), projectY(simY, simZ))
            for (i in 0 until 18) {
                simVy -= 9.8f * 0.04f
                simVx += aimCurve * 0.8f * 0.04f
                simX += simVx * 0.04f
                simY += simVy * 0.04f
                simZ += simVz * 0.04f
                if (simY < 0.1f || simZ > goalZ + 1.0f) break
                path.lineTo(projectX(simX, simZ), projectY(simY, simZ))
            }
            canvas.drawPath(path, trajectoryPaint)
        }

        // 7. Draw 3D Cartoon Soccer Ball & Dynamic Shadow
        val bScale = projectScale(ballZ)
        val bScreenX = projectX(ballX, ballZ)
        val bScreenY = projectY(ballY, ballZ)
        val bGroundY = projectY(0f, ballZ)
        val bRadius = (w * 0.09f) * bScale

        // Dynamic Shadow on the grass
        val shadowHeightFactor = (1f - (ballY / 4.0f)).coerceIn(0.2f, 1.0f)
        val shadowRadiusX = bRadius * 1.1f * shadowHeightFactor
        val shadowRadiusY = bRadius * 0.35f * shadowHeightFactor
        rectF.set(bScreenX - shadowRadiusX, bGroundY - shadowRadiusY, bScreenX + shadowRadiusX, bGroundY + shadowRadiusY)
        shadowPaint.alpha = (75 * shadowHeightFactor).toInt()
        canvas.drawOval(rectF, shadowPaint)

        // Ball Body
        canvas.drawCircle(bScreenX, bScreenY, bRadius, ballWhitePaint)

        // Comic Pentagons rotating with ball
        canvas.save()
        canvas.translate(bScreenX, bScreenY)
        canvas.rotate(ballRotationAngle)

        val patchRadius = bRadius * 0.36f
        for (i in 0 until 5) {
            val angle = (i * (360.0 / 5) - 18.0) * (PI / 180.0)
            val px = (bRadius * 0.50f) * cos(angle).toFloat()
            val py = (bRadius * 0.50f) * sin(angle).toFloat()

            ballPatchPath.reset()
            for (p in 0 until 5) {
                val pAngle = (p * (360.0 / 5) - 18.0) * (PI / 180.0)
                val x = px + patchRadius * cos(pAngle).toFloat()
                val y = py + patchRadius * sin(pAngle).toFloat()
                if (p == 0) ballPatchPath.moveTo(x, y) else ballPatchPath.lineTo(x, y)
            }
            ballPatchPath.close()
            canvas.drawPath(ballPatchPath, ballBlackPaint)
            canvas.drawPath(ballPatchPath, ballGoldSeamPaint)
        }

        // Center Gold Star Patch
        ballPatchPath.reset()
        for (p in 0 until 5) {
            val pAngle = (p * (360.0 / 5) - 18.0) * (PI / 180.0)
            val x = (patchRadius * 0.9f) * cos(pAngle).toFloat()
            val y = (patchRadius * 0.9f) * sin(pAngle).toFloat()
            if (p == 0) ballPatchPath.moveTo(x, y) else ballPatchPath.lineTo(x, y)
        }
        ballPatchPath.close()
        canvas.drawPath(ballPatchPath, goldPostPaint)
        canvas.drawPath(ballPatchPath, ballGoldSeamPaint)

        // Glossy Specular Highlight
        canvas.drawCircle(-bRadius * 0.35f, -bRadius * 0.35f, bRadius * 0.24f, ballGlossPaint)
        canvas.restore()

        // 8. Draw Celebration Confetti Particles
        for (p in particles) {
            if (p.active) {
                particlePaint.color = p.color
                particlePaint.alpha = p.alpha
                canvas.drawCircle(p.x, p.y, p.size, particlePaint)
            }
        }

        // Request continuous smooth hardware frame rate
        invalidate()
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        canvas.drawCircle(cx, cy, 26f * scale, cloudPaint)
        canvas.drawCircle(cx - 24f * scale, cy + 3f * scale, 20f * scale, cloudPaint)
        canvas.drawCircle(cx + 24f * scale, cy + 3f * scale, 20f * scale, cloudPaint)
        canvas.drawCircle(cx - 12f * scale, cy - 12f * scale, 18f * scale, cloudPaint)
        canvas.drawCircle(cx + 12f * scale, cy - 12f * scale, 18f * scale, cloudPaint)
    }

    private fun updatePhysics(dt: Float) {
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

        // Magnus effect curve force
        ballVx += ballSpinY * ballVz * 0.45f * dt

        // Gravity
        ballVy -= 9.8f * dt

        // Air drag
        ballVx *= (1f - 0.05f * dt)
        ballVy *= (1f - 0.05f * dt)
        ballVz *= (1f - 0.05f * dt)

        // Integrate position
        ballX += ballVx * dt
        ballY += ballVy * dt
        ballZ += ballVz * dt

        // Rotation
        val speed = hypot(ballVx.toDouble(), ballVz.toDouble()).toFloat()
        ballRotationAngle += speed * 35f * dt

        // Ground Collision
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

        // Goalkeeper Collision
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

        // Goal Posts & Crossbar Collision
        if (abs(ballZ - goalZ) < (0.15f + ballRadius) && ballY <= (goalHeight + ballRadius + 0.1f)) {
            // Left Post: x = -goalHalfWidth, y in [0, goalHeight]
            if (abs(ballX - (-goalHalfWidth)) < (0.12f + ballRadius) && ballY <= goalHeight) {
                ballVx = abs(ballVx) * 0.75f + 1.5f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
            // Right Post: x = goalHalfWidth, y in [0, goalHeight]
            if (abs(ballX - goalHalfWidth) < (0.12f + ballRadius) && ballY <= goalHeight) {
                ballVx = -abs(ballVx) * 0.75f - 1.5f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
            // Crossbar: y = goalHeight, x in [-goalHalfWidth, goalHalfWidth]
            if (abs(ballX) <= goalHalfWidth + 0.1f && abs(ballY - goalHeight) < (0.12f + ballRadius)) {
                ballVy = -abs(ballVy) * 0.70f
                ballVz = -ballVz * 0.65f
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    soundEngine.playPostHit()
                }
            }
        }

        // Goal Detection
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

        // Settle ball inside net
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
        val colors = intArrayOf(
            Color.rgb(251, 191, 36),  // Gold
            Color.rgb(244, 63, 94),   // Pink
            Color.rgb(56, 189, 248),  // Cyan
            Color.rgb(74, 222, 128),  // Lime
            Color.rgb(251, 146, 60),  // Orange
            Color.rgb(253, 224, 71)   // Yellow
        )
        for (p in particles) {
            p.active = true
            p.x = w * (0.35f + Random.nextFloat() * 0.3f)
            p.y = h * (0.30f + Random.nextFloat() * 0.2f)
            val speed = 250f + Random.nextFloat() * 450f
            val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - 150f
            p.color = colors[Random.nextInt(colors.size)]
            p.size = 10f + Random.nextFloat() * 14f
            p.maxLife = 1.8f + Random.nextFloat() * 0.8f
            p.life = p.maxLife
            p.alpha = 255
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
                p.vy += 450f * dt // Gravity
                p.x += p.vx * dt
                p.y += p.vy * dt
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
        val dt = (System.currentTimeMillis() - touchDownTime).coerceAtLeast(40).toFloat() / 1000f

        if (dy < -35f) {
            val screenH = height.toFloat()
            val screenW = width.toFloat()

            val vyNorm = (-dy / screenH) / dt
            val vxNorm = (dx / screenW) / dt

            val vz = (13.5f + vyNorm * 3.2f).coerceIn(14f, 26f)
            val vy = (4.5f + vyNorm * 1.6f).coerceIn(4.2f, 8.8f)
            val vx = (vxNorm * 5.5f + (dx / screenW) * 7.5f).coerceIn(-6.5f, 6.5f)

            var curve = 0f
            if (touchPoints.size >= 4) {
                val midIdx = touchPoints.size / 2
                val midPoint = touchPoints[midIdx]
                val t = if (abs(dy) > 1f) (midPoint.y - touchDownY) / dy else 0.5f
                val expectedX = touchDownX + t * dx
                val deviation = midPoint.x - expectedX
                curve = (deviation / screenW) * 30.0f
                curve = curve.coerceIn(-5.5f, 5.5f)
            }

            executeKick(vx, vy, vz, curve)
        }
        touchPoints.clear()
    }

    private fun updateAiming(curX: Float, curY: Float) {
        val dx = curX - touchDownX
        val dy = curY - touchDownY
        val screenH = height.toFloat()
        val screenW = width.toFloat()

        val pullY = dy.coerceAtLeast(0f) / screenH
        val pullX = -dx / screenW

        val power = (pullY * 3.2f).coerceIn(0.2f, 1.2f)
        aimVz = 13f + power * 12f
        aimVy = 4.2f + power * 4.2f
        aimVx = pullX * 11.0f
        aimCurve = (pullX * 3.5f).coerceIn(-3.5f, 3.5f)
        invalidate()
    }

    private fun handleSlingshotRelease(endX: Float, endY: Float) {
        isAiming = false
        val dy = endY - touchDownY
        if (dy > 30f) {
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
