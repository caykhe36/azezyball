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

class SoccerRenderer(
    private val context: Context,
    val gameManager: GameManager,
    val soundEngine: SoundEngine
) : GLSurfaceView.Renderer {

    val ballPhysics = BallPhysics()
    private val particleSystem = ParticleSystem(360)
    private val trajectoryRenderer = TrajectoryRenderer(32)

    var controlMode = ControlMode.SWIPE_FLICK

    // OpenGL Programs
    private var toonProgram = 0
    private var skyProgram = 0
    private var particleProgram = 0
    private var lineProgram = 0

    // Cartoon Textures
    private var ballTextureId = 0
    private var pitchTextureId = 0
    private var netTextureId = 0
    private var goldTextureId = 0
    private var skyTextureId = 0
    private var billboardTextureId = 0

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

    // Camera (Positioned perfectly behind the ball and centered)
    private var camX = 0f
    private var camY = 1.95f
    private var camZ = -13.7f
    private var targetX = 0f
    private var targetY = 1.05f
    private var targetZ = 0f

    // Bright Cartoon Sunlight
    private val lightPosInEyeSpace = floatArrayOf(2f, 20f, -5f)

    // Shader Uniform & Attribute Handles for Toon Program
    private var uMVPMatrixHandle = 0
    private var uMVMatrixHandle = 0
    private var uLightPosHandle = 0
    private var uColorHandle = 0
    private var uTextureHandle = 0
    private var uUseTextureHandle = 0
    private var uSpecularPowerHandle = 0
    private var uMetallicHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0
    private var aTexCoordinateHandle = 0

    // Sky Shader handles
    private var sMvpMatrixHandle = 0
    private var sTextureHandle = 0
    private var sPositionHandle = 0
    private var sTexCoordinateHandle = 0

    // Particle handles
    private var pMvpMatrixHandle = 0
    private var pPositionHandle = 0
    private var pColorHandle = 0
    private var pPointSizeHandle = 0

    // Line handles
    private var lMvpMatrixHandle = 0
    private var lPositionHandle = 0
    private var lColorHandle = 0

    // Aiming state
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
            particleSystem.explode(ballPhysics.x, ballPhysics.y, ballPhysics.z, 200)
            mainHandler.post {
                gameManager.recordGoal(ballPhysics.x, ballPhysics.y)
            }
            mainHandler.postDelayed({
                val nextPos = gameManager.getNextBallPosition()
                ballPhysics.reset(nextPos.first, nextPos.second)
            }, 2100)
        }

        ballPhysics.onMiss = {
            soundEngine.playMiss()
            mainHandler.post {
                gameManager.recordMiss()
            }
            mainHandler.postDelayed({
                ballPhysics.reset(0f, -10.5f)
            }, 1700)
        }

        ballPhysics.onPostHit = {
            soundEngine.playPostHit()
        }

        ballPhysics.onGoalkeeperSave = {
            soundEngine.playPostHit()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Bright cheerful sky cyan clear color
        GLES20.glClearColor(0.22f, 0.74f, 0.97f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // 1. Compile Toon Program
        toonProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.CARTOON_VERTEX_SHADER,
            ShaderHelper.CARTOON_FRAGMENT_SHADER
        )
        uMVPMatrixHandle = GLES20.glGetUniformLocation(toonProgram, "u_MVPMatrix")
        uMVMatrixHandle = GLES20.glGetUniformLocation(toonProgram, "u_MVMatrix")
        uLightPosHandle = GLES20.glGetUniformLocation(toonProgram, "u_LightPos")
        uColorHandle = GLES20.glGetUniformLocation(toonProgram, "u_Color")
        uTextureHandle = GLES20.glGetUniformLocation(toonProgram, "u_Texture")
        uUseTextureHandle = GLES20.glGetUniformLocation(toonProgram, "u_UseTexture")
        uSpecularPowerHandle = GLES20.glGetUniformLocation(toonProgram, "u_SpecularPower")
        uMetallicHandle = GLES20.glGetUniformLocation(toonProgram, "u_Metallic")
        aPositionHandle = GLES20.glGetAttribLocation(toonProgram, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(toonProgram, "a_Normal")
        aTexCoordinateHandle = GLES20.glGetAttribLocation(toonProgram, "a_TexCoordinate")

        // 2. Compile Sky Program
        skyProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.SKY_VERTEX_SHADER,
            ShaderHelper.SKY_FRAGMENT_SHADER
        )
        sMvpMatrixHandle = GLES20.glGetUniformLocation(skyProgram, "u_MVPMatrix")
        sTextureHandle = GLES20.glGetUniformLocation(skyProgram, "u_Texture")
        sPositionHandle = GLES20.glGetAttribLocation(skyProgram, "a_Position")
        sTexCoordinateHandle = GLES20.glGetAttribLocation(skyProgram, "a_TexCoordinate")

        // 3. Compile Particle Program
        particleProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.PARTICLE_VERTEX_SHADER,
            ShaderHelper.PARTICLE_FRAGMENT_SHADER
        )
        pMvpMatrixHandle = GLES20.glGetUniformLocation(particleProgram, "u_MVPMatrix")
        pPositionHandle = GLES20.glGetAttribLocation(particleProgram, "a_Position")
        pColorHandle = GLES20.glGetAttribLocation(particleProgram, "a_Color")
        pPointSizeHandle = GLES20.glGetAttribLocation(particleProgram, "a_PointSize")

        // 4. Compile Line Program
        lineProgram = ShaderHelper.createAndLinkProgram(
            ShaderHelper.LINE_VERTEX_SHADER,
            ShaderHelper.LINE_FRAGMENT_SHADER
        )
        lMvpMatrixHandle = GLES20.glGetUniformLocation(lineProgram, "u_MVPMatrix")
        lPositionHandle = GLES20.glGetAttribLocation(lineProgram, "a_Position")
        lColorHandle = GLES20.glGetAttribLocation(lineProgram, "a_Color")

        // Generate Bright Cartoon Textures
        skyTextureId = TextureGenerator.createCartoonSkyTexture()
        ballTextureId = TextureGenerator.createCartoonSoccerBallTexture()
        pitchTextureId = TextureGenerator.createCartoonGrassTexture()
        netTextureId = TextureGenerator.createCartoonNetTexture()
        goldTextureId = TextureGenerator.createCartoonGoldTexture()
        billboardTextureId = TextureGenerator.createCartoonBillboardTexture()

        // Build 3D Meshes
        sphereMesh = SphereMesh(ballPhysics.radius, 24, 32)
        goalMesh = GoalMesh(ballPhysics.goalWidth, ballPhysics.goalHeight, ballPhysics.netDepth, ballPhysics.postRadius)
        pitchMesh = PitchMesh(36f, 50f)
        goalkeeperMesh = GoalkeeperMesh()

        lastFrameTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        // Portrait FOV 54 degrees for perfect balance of depth and centered ball visibility
        Matrix.perspectiveM(projectionMatrix, 0, 54.0f, ratio, 0.5f, 150.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        var dt = (now - lastFrameTimeNs) / 1_000_000_000.0f
        lastFrameTimeNs = now
        if (dt > 0.05f) dt = 0.05f

        // Step Physics & Particles
        ballPhysics.update(dt)
        particleSystem.update(dt)

        // Clear
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Update Camera
        updateCamera(dt)
        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f)

        // 1. Render Sky Backdrop
        renderSky()

        // 2. Render Cartoon Grass, Lines & Billboards
        renderPitch()

        // 3. Render Glossy Golden Goal Frame & White Net
        renderGoal()

        // 4. Render Mascot Goalkeeper if active
        if (ballPhysics.goalkeeperEnabled) {
            renderGoalkeeper()
        }

        // 5. Render 3D Dynamic Ball Shadow on Grass
        renderBallShadow()

        // 6. Render 3D Cartoon Soccer Ball
        renderBall()

        // 7. Render Trajectory line
        if (isAiming && (ballPhysics.state == BallState.IDLE || ballPhysics.state == BallState.AIMING)) {
            renderTrajectory()
        }

        // 8. Render Colorful Celebration Confetti
        renderParticles()
    }

    private fun updateCamera(dt: Float) {
        when (ballPhysics.state) {
            BallState.IDLE, BallState.AIMING, BallState.RESETTING -> {
                // Ball is centered at x = 0, camera directly behind it
                val desiredCamX = ballPhysics.x * 0.7f
                val desiredCamY = 1.90f
                val desiredCamZ = ballPhysics.z - 3.2f
                val desiredTargetX = ballPhysics.x * 0.2f
                val desiredTargetY = 1.05f
                val desiredTargetZ = 0.0f

                val lerpFactor = (6.0f * dt).coerceIn(0f, 1f)
                camX += (desiredCamX - camX) * lerpFactor
                camY += (desiredCamY - camY) * lerpFactor
                camZ += (desiredCamZ - camZ) * lerpFactor
                targetX += (desiredTargetX - targetX) * lerpFactor
                targetY += (desiredTargetY - targetY) * lerpFactor
                targetZ += (desiredTargetZ - targetZ) * lerpFactor
            }
            BallState.FLYING, BallState.SCORED, BallState.MISSED -> {
                val desiredCamX = ballPhysics.x * 0.5f
                val desiredCamY = 2.0f + (ballPhysics.y * 0.25f)
                val desiredCamZ = (ballPhysics.z - 3.5f).coerceAtMost(-3.5f)

                val desiredTargetX = ballPhysics.x * 0.7f
                val desiredTargetY = 1.1f + (ballPhysics.y * 0.35f)
                val desiredTargetZ = 0.0f

                val lerpFactor = (8.0f * dt).coerceIn(0f, 1f)
                camX += (desiredCamX - camX) * lerpFactor
                camY += (desiredCamY - camY) * lerpFactor
                camZ += (desiredCamZ - camZ) * lerpFactor
                targetX += (desiredTargetX - targetX) * lerpFactor
                targetY += (desiredTargetY - targetY) * lerpFactor
                targetZ += (desiredTargetZ - targetZ) * lerpFactor
            }
        }
    }

    private fun renderSky() {
        GLES20.glUseProgram(skyProgram)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(sMvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, skyTextureId)
        GLES20.glUniform1i(sTextureHandle, 0)

        pitchMesh.skyMesh.render(sPositionHandle, -1, sTexCoordinateHandle, true)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun renderPitch() {
        GLES20.glUseProgram(toonProgram)
        setupCommonUniforms()

        // 1. Vibrant Green Grass
        Matrix.setIdentityM(modelMatrix, 0)
        applyMatrices()
        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 16.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pitchTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        pitchMesh.grassMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        // 2. Bright White Pitch Lines
        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 0)
        GLES20.glUniform1f(uMetallicHandle, 0.0f)
        pitchMesh.linesMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, false)

        // 3. Colorful Stadium Billboards
        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, billboardTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        pitchMesh.boardsMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)
    }

    private fun renderGoal() {
        GLES20.glUseProgram(toonProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        applyMatrices()

        // 1. Glossy Vibrant Cartoon Golden Goal Frame
        GLES20.glUniform4f(uColorHandle, 1.0f, 0.88f, 0.20f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 64.0f)
        GLES20.glUniform1f(uMetallicHandle, 1.5f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, goldTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        goalMesh.frameMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        // 2. White Cartoon Net (Alpha Blended with Depth Mask False to prevent flickering)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false) // CRITICAL: Prevent transparent fragments from causing z-fighting/flicker

        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 0.85f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 8.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, netTextureId)
        GLES20.glUniform1i(uTextureHandle, 0)
        goalMesh.netMesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, true)

        GLES20.glDepthMask(true) // Restore depth writing
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderGoalkeeper() {
        GLES20.glUseProgram(toonProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, ballPhysics.gkX, 0f, ballPhysics.gkZ)
        applyMatrices()

        // Bright Fun Mascot Color (Vivid Cyan & Orange)
        GLES20.glUniform4f(uColorHandle, 0.06f, 0.72f, 0.98f, 1.0f) // Bright Sky Blue
        GLES20.glUniform1i(uUseTextureHandle, 0)
        GLES20.glUniform1f(uSpecularPowerHandle, 32.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.5f)
        goalkeeperMesh.mesh.render(aPositionHandle, aNormalHandle, aTexCoordinateHandle, false)
    }

    private fun renderBallShadow() {
        GLES20.glUseProgram(toonProgram)
        setupCommonUniforms()

        val heightFactor = (1.0f - (ballPhysics.y / 3.5f)).coerceIn(0.25f, 1.0f)
        val shadowScale = heightFactor * 1.15f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, ballPhysics.x, 0.015f, ballPhysics.z)
        Matrix.scaleM(modelMatrix, 0, shadowScale, 1.0f, shadowScale)
        applyMatrices()

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        val alpha = 0.55f * heightFactor
        GLES20.glUniform4f(uColorHandle, 0.05f, 0.15f, 0.08f, alpha)
        GLES20.glUniform1i(uUseTextureHandle, 0)
        GLES20.glUniform1f(uSpecularPowerHandle, 1.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.0f)
        pitchMesh.shadowMesh.render(aPositionHandle, aNormalHandle, -1, false)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderBall() {
        GLES20.glUseProgram(toonProgram)
        setupCommonUniforms()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, ballPhysics.x, ballPhysics.y, ballPhysics.z)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotY, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, ballPhysics.rotZ, 0f, 0f, 1f)
        applyMatrices()

        GLES20.glUniform4f(uColorHandle, 1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glUniform1i(uUseTextureHandle, 1)
        GLES20.glUniform1f(uSpecularPowerHandle, 48.0f)
        GLES20.glUniform1f(uMetallicHandle, 0.4f)
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
    }

    fun executeKick(vx: Float, vy: Float, vz: Float, curve: Float) {
        if (ballPhysics.state != BallState.IDLE && ballPhysics.state != BallState.AIMING) return
        soundEngine.playKick()
        ballPhysics.kick(vx, vy, vz, curve)
    }
}
