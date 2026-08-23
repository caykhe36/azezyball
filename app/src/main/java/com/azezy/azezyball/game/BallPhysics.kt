package com.azezy.azezyball.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class BallState {
    IDLE,
    AIMING,
    FLYING,
    SCORED,
    MISSED,
    RESETTING
}

class BallPhysics {

    val radius = 0.22f
    val goalWidth = 5.2f
    val goalHeight = 2.3f
    val goalZ = 0.0f
    val postRadius = 0.09f
    val netDepth = 2.0f

    var state = BallState.IDLE

    // Ball 3D transforms (Centered in lower middle view)
    var x = 0f
    var y = radius
    var z = -10.5f

    var vx = 0f
    var vy = 0f
    var vz = 0f

    var spinX = 0f
    var spinY = 0f
    var spinZ = 0f

    var rotX = 0f
    var rotY = 0f
    var rotZ = 0f

    // Goalkeeper properties
    var goalkeeperEnabled = false
    var gkX = 0f
    var gkY = 0.9f
    var gkZ = 0.1f
    var gkSpeed = 1.8f
    var gkDir = 1f
    val gkWidth = 1.4f
    val gkHeight = 1.8f

    // Events listeners
    var onGoal: (() -> Unit)? = null
    var onMiss: (() -> Unit)? = null
    var onPostHit: (() -> Unit)? = null
    var onGoalkeeperSave: (() -> Unit)? = null

    private var flightTime = 0f
    private var postHitSoundPlayed = false

    fun reset(newX: Float = 0f, newZ: Float = -10.5f) {
        x = newX
        y = radius
        z = newZ
        vx = 0f
        vy = 0f
        vz = 0f
        spinX = 0f
        spinY = 0f
        spinZ = 0f
        state = BallState.IDLE
        flightTime = 0f
        postHitSoundPlayed = false
    }

    fun kick(kickedVx: Float, kickedVy: Float, kickedVz: Float, curveSpin: Float = 0f) {
        if (state != BallState.IDLE && state != BallState.AIMING) return
        vx = kickedVx
        vy = kickedVy
        vz = kickedVz
        spinY = curveSpin
        spinX = kickedVz * 2.5f // Forward top/back spin
        state = BallState.FLYING
        flightTime = 0f
        postHitSoundPlayed = false
    }

    fun update(dt: Float) {
        // Update Goalkeeper if enabled
        if (goalkeeperEnabled) {
            gkX += gkDir * gkSpeed * dt
            val maxGkX = (goalWidth / 2f) - 0.9f
            if (gkX > maxGkX) {
                gkX = maxGkX
                gkDir = -1f
            } else if (gkX < -maxGkX) {
                gkX = -maxGkX
                gkDir = 1f
            }
        }

        if (state != BallState.FLYING && state != BallState.SCORED && state != BallState.MISSED) {
            return
        }

        flightTime += dt

        // Magnus effect curve force
        val magnusForce = spinY * vz * 0.35f
        vx += magnusForce * dt

        // Gravity
        vy -= 9.81f * dt

        // Air drag
        val drag = 0.08f * dt
        vx *= (1f - drag)
        vy *= (1f - drag)
        vz *= (1f - drag)

        // Spin decay
        spinY *= (1f - 0.15f * dt)

        // Integrate Position
        x += vx * dt
        y += vy * dt
        z += vz * dt

        // Integrate Ball Visual Rotation
        rotX += spinX * dt * 50f
        rotY += spinY * dt * 50f
        val horizontalSpeed = hypot(vx.toDouble(), vz.toDouble()).toFloat()
        rotZ += (vx * 30f) * dt

        // 1. Ground collision
        if (y <= radius) {
            y = radius
            if (abs(vy) > 0.4f) {
                vy = -vy * 0.65f // Bounce damping
                vx *= 0.85f
                vz *= 0.85f
            } else {
                vy = 0f
                vx *= (1f - 2.5f * dt)
                vz *= (1f - 2.5f * dt)
            }
        }

        // 2. Goalkeeper Save Collision
        if (goalkeeperEnabled && state == BallState.FLYING && z >= (gkZ - 0.3f) && z <= (gkZ + 0.3f)) {
            val inGkX = abs(x - gkX) < (gkWidth / 2f + radius)
            val inGkY = y >= 0f && y <= (gkHeight + radius)
            if (inGkX && inGkY) {
                // Ball blocked by Goalkeeper!
                vx = (x - gkX) * 4f + (if (vx > 0) -2f else 2f)
                vy = abs(vy) * 0.5f + 2f
                vz = -vz * 0.45f
                state = BallState.MISSED
                onGoalkeeperSave?.invoke()
                onMiss?.invoke()
                return
            }
        }

        // 3. Goal Posts and Crossbar Collision
        val halfW = goalWidth / 2f
        if (abs(z - goalZ) < (postRadius + radius + 0.1f) && y <= (goalHeight + radius + 0.1f)) {
            // Left Post Cylinder: x = -halfW, z = goalZ, y in [0, goalHeight]
            val leftDist = hypot((x - (-halfW)).toDouble(), (z - goalZ).toDouble()).toFloat()
            if (leftDist < (postRadius + radius) && y <= goalHeight) {
                val nx = (x - (-halfW)) / leftDist
                val nz = (z - goalZ) / leftDist
                val dot = vx * nx + vz * nz
                vx = (vx - 2 * dot * nx) * 0.75f
                vz = (vz - 2 * dot * nz) * 0.75f
                x = -halfW + nx * (postRadius + radius + 0.02f)
                z = goalZ + nz * (postRadius + radius + 0.02f)
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    onPostHit?.invoke()
                }
            }

            // Right Post Cylinder: x = +halfW, z = goalZ, y in [0, goalHeight]
            val rightDist = hypot((x - halfW).toDouble(), (z - goalZ).toDouble()).toFloat()
            if (rightDist < (postRadius + radius) && y <= goalHeight) {
                val nx = (x - halfW) / rightDist
                val nz = (z - goalZ) / rightDist
                val dot = vx * nx + vz * nz
                vx = (vx - 2 * dot * nx) * 0.75f
                vz = (vz - 2 * dot * nz) * 0.75f
                x = halfW + nx * (postRadius + radius + 0.02f)
                z = goalZ + nz * (postRadius + radius + 0.02f)
                if (!postHitSoundPlayed) {
                    postHitSoundPlayed = true
                    onPostHit?.invoke()
                }
            }

            // Crossbar Cylinder: y = goalHeight, z = goalZ, x in [-halfW, halfW]
            if (abs(x) <= halfW + postRadius) {
                val barDist = hypot((y - goalHeight).toDouble(), (z - goalZ).toDouble()).toFloat()
                if (barDist < (postRadius + radius)) {
                    val ny = (y - goalHeight) / barDist
                    val nz = (z - goalZ) / barDist
                    val dot = vy * ny + vz * nz
                    vy = (vy - 2 * dot * ny) * 0.75f
                    vz = (vz - 2 * dot * nz) * 0.75f
                    y = goalHeight + ny * (postRadius + radius + 0.02f)
                    z = goalZ + nz * (postRadius + radius + 0.02f)
                    if (!postHitSoundPlayed) {
                        postHitSoundPlayed = true
                        onPostHit?.invoke()
                    }
                }
            }
        }

        // 4. Goal Detection & Net Interaction
        if (state == BallState.FLYING) {
            if (z >= goalZ) {
                val insideWidth = abs(x) < (halfW - postRadius)
                val insideHeight = y > 0.05f && y < (goalHeight - postRadius)

                if (insideWidth && insideHeight) {
                    // GOAL! Scored cleanly!
                    state = BallState.SCORED
                    onGoal?.invoke()
                } else if (z > 0.3f) {
                    // Missed the frame
                    state = BallState.MISSED
                    onMiss?.invoke()
                }
            }
        }

        // 5. Net Collision & Dampening inside goal
        if (state == BallState.SCORED) {
            // Ball inside net volume: x in [-halfW, halfW], y in [0, goalHeight], z in [goalZ, netDepth]
            if (z >= (netDepth - radius)) {
                z = netDepth - radius
                vz = -abs(vz) * 0.15f // Soft net rebound
                vx *= 0.5f
                vy *= 0.5f
            }
            if (abs(x) >= (halfW - radius)) {
                x = (halfW - radius) * (if (x > 0) 1f else -1f)
                vx = -vx * 0.2f
            }
            if (y >= (goalHeight - radius)) {
                y = goalHeight - radius
                vy = -abs(vy) * 0.2f
            }
            // Rapidly settle ball to the grass
            vx *= (1f - 5f * dt)
            vy *= (1f - 4f * dt)
            vz *= (1f - 5f * dt)
        }

        // 6. Miss Timeout / Stop check
        if (state == BallState.FLYING) {
            if (flightTime > 3.0f || (z > 3.0f) || (y <= radius + 0.01f && abs(vz) < 0.2f && z < goalZ)) {
                state = BallState.MISSED
                onMiss?.invoke()
            }
        }
    }
}
