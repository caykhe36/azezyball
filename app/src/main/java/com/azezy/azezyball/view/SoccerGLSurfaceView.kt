package com.azezy.azezyball.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import com.azezy.azezyball.game.BallState
import com.azezy.azezyball.game.GameManager
import com.azezy.azezyball.sound.SoundEngine
import kotlin.math.abs
import kotlin.math.hypot

class SoccerGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    lateinit var renderer: SoccerRenderer
        private set

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L

    private val touchPoints = ArrayList<PointF>()

    fun init(gameManager: GameManager, soundEngine: SoundEngine) {
        setEGLContextClientVersion(2)
        renderer = SoccerRenderer(context, gameManager, soundEngine)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    var onFirstTouch: (() -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::renderer.isInitialized) return super.onTouchEvent(event)

        val ballState = renderer.ballPhysics.state
        if (ballState != BallState.IDLE && ballState != BallState.AIMING) {
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onFirstTouch?.invoke()
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = System.currentTimeMillis()
                touchPoints.clear()
                touchPoints.add(PointF(event.x, event.y))

                if (renderer.controlMode == ControlMode.SLINGSHOT_AIM) {
                    renderer.isAiming = true
                    renderer.ballPhysics.state = BallState.AIMING
                    updateAiming(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                touchPoints.add(PointF(event.x, event.y))

                if (renderer.controlMode == ControlMode.SLINGSHOT_AIM) {
                    updateAiming(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                touchPoints.add(PointF(event.x, event.y))

                if (renderer.controlMode == ControlMode.SWIPE_FLICK) {
                    handleSwipeKick(event.x, event.y)
                } else {
                    handleSlingshotRelease(event.x, event.y)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                renderer.isAiming = false
                if (renderer.ballPhysics.state == BallState.AIMING) {
                    renderer.ballPhysics.state = BallState.IDLE
                }
                touchPoints.clear()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun handleSwipeKick(endX: Float, endY: Float) {
        val dx = endX - touchDownX
        val dy = endY - touchDownY // Upward swipe is negative dy
        val dt = (System.currentTimeMillis() - touchDownTime).coerceAtLeast(40).toFloat() / 1000f

        // Only kick if swiping upwards towards goal
        if (dy < -40f) {
            val screenH = height.toFloat()
            val screenW = width.toFloat()

            // Normalized swipe speeds
            val vyNorm = (-dy / screenH) / dt // Speed factor
            val vxNorm = (dx / screenW) / dt

            // Forward speed: 14 to 28 m/s
            val vz = (14f + vyNorm * 3.5f).coerceIn(14f, 27f)

            // Vertical launch angle speed: 4.5 to 9.5 m/s
            val vy = (4.8f + vyNorm * 1.8f).coerceIn(4.5f, 9.2f)

            // Horizontal directional velocity
            val vx = (vxNorm * 6.5f + (dx / screenW) * 8.0f).coerceIn(-7.0f, 7.0f)

            // Calculate Curve (Magnus effect) from swipe curve inflection
            var curve = 0f
            if (touchPoints.size >= 4) {
                val midIdx = touchPoints.size / 2
                val midPoint = touchPoints[midIdx]
                // Chord linear interpolation at midPoint Y
                val t = if (abs(dy) > 1f) (midPoint.y - touchDownY) / dy else 0.5f
                val expectedX = touchDownX + t * dx
                val deviation = midPoint.x - expectedX
                curve = (deviation / screenW) * 35.0f
                curve = curve.coerceIn(-6.5f, 6.5f)
            }

            renderer.executeKick(vx, vy, vz, curve)
        }
        touchPoints.clear()
    }

    private fun updateAiming(currentX: Float, currentY: Float) {
        val dx = currentX - touchDownX
        val dy = currentY - touchDownY

        val screenH = height.toFloat()
        val screenW = width.toFloat()

        // Slingshot: drag down to shoot forward
        val pullY = dy.coerceAtLeast(0f) / screenH
        val pullX = -dx / screenW

        val power = (pullY * 3.0f).coerceIn(0.2f, 1.2f)
        val vz = 13f + power * 13f
        val vy = 4.2f + power * 4.5f
        val vx = pullX * 12.0f
        val curve = (pullX * 4.0f).coerceIn(-4.0f, 4.0f)

        renderer.aimVx = vx
        renderer.aimVy = vy
        renderer.aimVz = vz
        renderer.aimCurve = curve
    }

    private fun handleSlingshotRelease(endX: Float, endY: Float) {
        renderer.isAiming = false
        val dy = endY - touchDownY
        if (dy > 30f) {
            updateAiming(endX, endY)
            renderer.executeKick(renderer.aimVx, renderer.aimVy, renderer.aimVz, renderer.aimCurve)
        } else {
            renderer.ballPhysics.state = BallState.IDLE
        }
        touchPoints.clear()
    }
}
