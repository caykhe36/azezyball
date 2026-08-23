package com.azezy.azezyball.view

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.azezy.azezyball.engine.GoalMesh
import com.azezy.azezyball.engine.GoalkeeperMesh
import com.azezy.azezyball.engine.ParticleSystem
import com.azezy.azezyball.engine.PitchMesh
import com.azezy.azezyball.engine.ShaderHelper
import com.azezy.azezyball.engine.SphereMesh
import com.azezy.azezyball.engine.TextureGenerator
import com.azezy.azezyball.engine.TrajectoryRenderer
import com.azezy.azezyball.game.BallPhysics
import com.azezy.azezyball.game.BallState
import com.azezy.azezyball.game.GameManager
import com.azezy.azezyball.sound.SoundEngine
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot

enum class ControlMode {
    SWIPE_FLICK,
    SLINGSHOT_AIM
}

class SoccerRenderer(
    private val context: Context,
    val gameManager: GameManager,
    val soundEngine: SoundEngine
) : GLSurfaceView.Renderer {

    val ballPhysics = BallPhysics()
    private val particleSystem = ParticleSystem(300)
    private val trajectoryRenderer = TrajectoryRenderer(30)

    var controlMode = ControlMode.SWIPE_FLICK

    // OpenGL Programs
    private var standardProgram = 0
    private var particleProgram = 0
    private var lineProgram = 0

    // Textures
    private var ballTextureId = 0
    private var pitchTextureId = 0
    private var netTextureId = 0
    private var goldTextureId = 0

    // 3D Meshes
    private lateinit var sphereMesh: SphereMesh
    private lateinit var goalMesh: GoalMesh
    private lateinit var pitchMesh: PitchMesh
    private lateinit var goalkeeperMesh: GoalkeeperMesh

    // Transformation Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Camera properties
    private var camX = 0f
    private var camY = 1.8f
    private var camZ = -15.5f
    private var targetX = 0f
    private var targetY = 1.1f
    private var targetZ = 0f

    // Light position (Stadium night floodlight)
    private val lightPosInEyeSpace = floatArrayOf(0f, 15f, -5f)

    // Shader Uniform & Attribute Handles
    private var uMVPMatrixHandle = 0
    private var uMVMatrixHandle = 0
    private var uNormalMatrixHandle = 0
    private var uLightPosHandle = 0
    private var uColorHandle = 0
    private var uTextureHandle = 0
    private var uUseTextureHandle = 0
    private var uSpecularPowerHandle = 0
    private var uMetallicHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0
    private var aTexCoordinateHandle = 0

    // Particle handles
    private var pMvpMatrixHandle = 0
    private var pPositionHandle = 0
    private var pColorHandle = 0
    private var pPointSizeHandle = 0

    // Line handles
    private var lMvpMatrixHandle = 0
    private var lPositionHandle = 0
    private var lColorHandle = 0

    // Aiming state for Slingshot mode
    var isAiming = false
    var aimVx = 0f
    var aimVy = 0f
    var aimVz = 0f
    var aimCurve = 0f

    private var lastFrameTimeNs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setupPhysicsCallbacks()
    }

    private fun setupPhysicsCallbacks() {
        ballPhysics.onGoal = {
            soundEngine.playGoal()
            particleSystem.explode(ballPhysics.x, ballPhysics.y, ballPhysics.z, 140)
            mainHandler.post {
                gameManager.recordGoal(ballPhysics.x, ballPhysics.y)
            }
            // Auto schedule next ball after 2.2 seconds
            mainHandler.postDelayed({
                val nextPos = gameManager.getNextBallPosition()
                ballPhysics.reset(nextPos.first, nextPos.second)
            }, 2200)
        }

        ballPhysics.onMiss = {
            soundEngine.playMiss()
            mainHandler.post {
                gameManager.recordMiss()
            }
            // Auto schedule next ball after 1.8 seconds
            mainHandler.postDelayed({
                ballPhysics.reset(ballPhysics.x * 0.5f, -12f)
            }, 1800)
        }

        ballPhysics.onPostHit = {
            soundEngine.playPostHit()
        }

        ballPhysics.onGoalkeeperSave = {
            soundEngine.playPostHit()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.06f, 0.10f, 1.0f) // Night Obsidian Dark Blue
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Compile and Link Standard Program
        standardProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.STANDARD_VERTEX_SHADER,
            ShaderHelper.STANDARD_FRAGMENT_SHADER
        )
        uMVPMatrixHandle = GLES20.glGetUniformLocation(standardProgram, "u_MVPMatrix")
        uMVMatrixHandle = GLES20.glGetUniformLocation(standardProgram, "u_MVMatrix")
        uNormalMatrixHandle = GLES20.glGetUniformLocation(standardProgram, "u_NormalMatrix")
        uLightPosHandle = GLES20.glGetUniformLocation(standardProgram, "u_LightPos")
        uColorHandle = GLES20.glGetUniformLocation(standardProgram, "u_Color")
        uTextureHandle = GLES20.glGetUniformLocation(standardProgram, "u_Texture")
        uUseTextureHandle = GLES20.glGetUniformLocation(standardProgram, "u_UseTexture")
        uSpecularPowerHandle = GLES20.glGetUniformLocation(standardProgram, "u_SpecularPower")
        uMetallicHandle = GLES20.glGetUniformLocation(standardProgram, "u_Metallic")
        aPositionHandle = GLES20.glGetAttribLocation(standardProgram, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(standardProgram, "a_Normal")
        aTexCoordinateHandle = GLES20.glGetAttribLocation(standardProgram, "a_TexCoordinate")

        // Compile Particle Program
        particleProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.PARTICLE_VERTEX_SHADER,
            ShaderHelper.PARTICLE_FRAGMENT_SHADER
        )
        pMvpMatrixHandle = GLES20.glGetUniformLocation(particleProgram, "u_MVPMatrix")
        pPositionHandle = GLES20.glGetAttribLocation(particleProgram, "a_Position")
        pColorHandle = GLES20.glGetAttribLocation(particleProgram, "a_Color")
        pPointSizeHandle = GLES20.glGetAttribLocation(particleProgram, "a_PointSize")

        // Compile Line Program
        lineProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.LINE_VERTEX_SHADER,
            ShaderHelper.LINE_FRAGMENT_SHADER
        )
        lMvpMatrixHandle = GLES20.glGetUniformLocation(lineProgram, "u_MVPMatrix")
        lPositionHandle = GLES20.glGetAttribLocation(lineProgram, "a_Position")
        lColorHandle = GLES20.glGetAttribLocation(lineProgram, "a_Color")

        // Generate Procedural 3D Textures
        ballTextureId = TextureGenerator.createSoccerBallTexture()
        pitchTextureId = TextureGenerator.createGrassPitchTexture()
        netTextureId = TextureGenerator.createGoalNetTexture()
        goldTextureId = TextureGenerator.createGoldMetalTexture()

        // Create Meshes
        sphereMesh = SphereMesh(ballPhysics.radius, 24, 32)
        goalMesh = GoalMesh(ballPhysics.goalWidth, ballPhysics.goalHeight, ballPhysics.netDepth, ballPhysics.postRadius)
        pitchMesh = PitchMesh(36f, 50f)
        goalkeeperMesh = GoalkeeperMesh()

        lastFrameTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        // Perspective projection: 55 degree FOV
        Matrix.perspectiveM(projectionMatrix, 0, 55.0f, ratio, 0.5f, 120.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        var dt = (now - lastFrameTimeNs) / 1_000_000_000.0f
        lastFrameTimeNs = now
        if (dt > 0.05f) dt = 0.05f

        // Physics Step
        ballPhysics.update(dt)
        particleSystem.update(dt)

        // Clear Color & Depth Buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Smooth Camera Follow
        updateCamera(dt)

        // Set View Matrix
        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f)

        // 1. Render Pitch (Grass, lines, stadium walls)
        renderPitch()

        // 2. Render 3D Golden Goal (Posts, crossbar, net)
        renderGoal()

        // 3. Render 3D Goalkeeper if active
        if (ballPhysics.goalkeeperEnabled) {
            renderGoalkeeper()
        }

        // 4. Render 3D Soccer Ball
        renderBall()

        // 5. Render Trajectory if aiming
        if (isAiming && (ballPhysics.state == BallState.IDLE || ballPhysics.state == BallState.AIMING)) {
            renderTrajectory()
        }

        // 6. Render 3D Celebration Particles
        renderParticles()
    }

    private fun updateCamera(dt: Float) {
        when (ballPhysics.state) {
            BallState.IDLE, BallState.AIMING, BallState.RESETTING -> {
                val desiredCamX = ballPhysics.x * 0.65f
                val desiredCamY = 1.7f
                val desiredCamZ = ballPhysics.z - 3.4f
                val desiredTargetX = ballPhysics.x * 0.2f
                val desiredTargetY = 1.1f
                val desiredTargetZ = 0.0f

                val lerpFactor = (5.0f * dt).coerceIn(0f, 1f)
                camX += (desiredCamX - camX) * lerpFactor
                camY += (desiredCamY - camY) * lerpFactor
                camZ += (desiredCamZ - camZ) * lerpFactor
                targetX += (desiredTargetX - targetX) * lerpFactor
                targetY += (desiredTargetY - targetY) * lerpFactor
                targetZ += (desiredTargetZ - targetZ) * lerpFactor
            }
            BallState.FLYING, BallState.SCORED, BallState.MISSED -> {
                val desiredCamX = ballPhysics.x * 0.5f
                val desiredCamY = 1.9f + (ballPhysics.y * 0.2f)
                val desiredCamZ = (ballPhysics.z - 3.8f).coerceAtMost(-4.0f)

                val desiredTargetX = ballPhysics.x * 0.6f
                val desiredTargetY = 1.1f + (ballPhysics.y * 0.4f)
                val desiredTargetZ = 0.0f

                val lerpFactor = (7.0f * dt).coerceIn(0f, 1f)
                camX += (desiredCamX - camX) * lerpFactor
                camY += (desiredCamY - camY) * lerpFactor
                camZ += (desiredCamZ - camZ) * lerpFactor
                targetX += (desiredTargetX - targetX) * lerpFactor
                targetY += (desiredTargetY - targetY) * lerpFactor
                targetZ += (desiredTargetZ - targetZ) * lerpFactor
            }
        }
    }

    private fun renderPitch() {
        GLES20.glUseProgram(standardProgram)
        setupCommonUniforms()

        // Render Grass
        Matrix.setIdentityM(modelMatrix, 0)
        applyMatrices()
        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 16.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.05f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pitchTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        pitchMesh.grassMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        // Render White Lines
        GLES20.glUniform4f(uColorHandle, 0.95f, 0.95f, 0.95f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 0)
        GLES20.glUniform1f(uSpecularPowerHandle, 32.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.1f)
        pitchMesh.linesMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, false)

        // Render LED Perimeter Boards
        GLES20.glUniform4f(uColorHandle, 0.12f, 0.16f, 0.24f, 1.0f)
        pitchMesh.boardsMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, false)
    }

    private fun renderGoal() {
        GLES20.glUseProgram(standardProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        applyMatrices()

        // 1. Golden Frame (Specular Metallic Gold)
        GLES20.glUniform4f(uColorHandle, 1.0f, 0.84f, 0.0f, 1.0f) // Gold RGB
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 48.0f)
        GLES20.glUniform1f(uMetallicHandle, 1.2f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, goldTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        goalMesh.frameMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        // 2. Goal Net (Alpha Blended)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE) // Double sided net

        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 0.85f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 8.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.1f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, netTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        goalMesh.netMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderGoalkeeper() {
        GLES20.glUseProgram(standardProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, ballPhysics.gkX, 0f, ballPhysics.gkZ)
        applyMatrices()

        // Vibrant Red/Gold Goalkeeper Kit
        GLES20.glUniform4f(uColorHandle, 0.9f, 0.2f, 0.2f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 0)
        GLES20.glUniform1f(uSpecularPowerHandle, 24.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.4f)
        goalkeeperMesh.mesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, false)
    }

    private fun renderBall() {
        GLES20.glUseProgram(standardProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, ballPhysics.x, ballPhysics.y, ballPhysics.z)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotY, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotZ, 0f, 0f, 1f)
        applyMatrices()

        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 36.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.6f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ballTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        sphereMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)
    }

    private fun renderTrajectory() {
        trajectoryRenderer.calculateTrajectory(
            ballPhysics.x, ballPhysics.y, ballPhysics.z,
            aimVx, aimVy, aimVz, aimCurve
        )
        trajectoryRenderer.render(
            lineProgram,
            projectionMatrix,
            lMvpMatrixHandle,
            lPositionHandle,
            lColorHandle
        )
    }

    private fun renderParticles() {
        particleSystem.render(
            particleProgram,
            projectionMatrix,
            pMvpMatrixHandle,
            pPositionHandle,
            pColorHandle,
            pPointSizeHandle
        )
    }

    private fun setupCommonUniforms() {
        GLES20.glUniform3f(uLightPosHandle, lightPosInEyeSpace[0], lightPosInEyeSpace[1], lightPosInEyeSpace[2])
    }

    private fun applyMatrices() {
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)

        // Calculate Normal Matrix (Transpose of inverse of MV)
        val invMV = FloatArray(16)
        Matrix.invertM(invMV, 0, mvMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, invMV, 0)
        GLES20.glUniformMatrix4fv(uNormalMatrixHandle, 1, false, normalMatrix, 0)
    }

    fun executeKick(vx: Float, vy: Float, vz: Float, curve: Float) {
        if (ballPhysics.state != BallState.IDLE && ballPhysics.state != BallState.AIMING) return
        soundEngine.playKick()
        ballPhysics.kick(vx, vy, vz, curve)
    }
}
