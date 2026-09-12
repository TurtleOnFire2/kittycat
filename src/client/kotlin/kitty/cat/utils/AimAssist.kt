package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object AimAssist {
    data class Candidate(
        val yawDifference: Float,
        val pitchDifference: Float,
        val strength: Double,
        val distance: Double
    )

    fun candidate(target: Vec3, fov: Double, strength: Double): Candidate? {
        val player = mc.player ?: return null
        val delta = target.subtract(player.eyePosition)
        val horizontalDistance = sqrt(delta.x * delta.x + delta.z * delta.z)
        val targetYaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val targetPitch = Math.toDegrees(atan2(-delta.y, horizontalDistance)).toFloat()
        return candidate(targetYaw, targetPitch, fov, strength)
    }

    fun candidate(targetYaw: Float, targetPitch: Float, fov: Double, strength: Double): Candidate? {
        val player = mc.player ?: return null
        val yawDifference = angleDifference(targetYaw, player.yRot)
        val pitchDifference = targetPitch - player.xRot
        val halfFov = fov / 2.0
        if (abs(yawDifference) > halfFov || abs(pitchDifference) > halfFov) return null

        return Candidate(
            yawDifference,
            pitchDifference,
            strength,
            sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference.toDouble())
        )
    }

    fun adjustMouse(
        accumulatedDX: Double,
        accumulatedDY: Double,
        candidate: Candidate
    ): DoubleArray? {
        if (abs(accumulatedDX) < 0.001 && abs(accumulatedDY) < 0.001) return null

        val scale = rotationGcd() / 0.15
        val neededX = candidate.yawDifference / scale
        val neededY = candidate.pitchDifference / scale
        val neededMagnitude = sqrt(neededX * neededX + neededY * neededY)
        if (neededMagnitude < 1e-6) return null

        val userMagnitude = sqrt(accumulatedDX * accumulatedDX + accumulatedDY * accumulatedDY)
        val pull = (userMagnitude * candidate.strength).coerceAtMost(neededMagnitude)
        val assistX = neededX / neededMagnitude * pull
        val assistY = neededY / neededMagnitude * pull

        return doubleArrayOf(
            accumulatedDX * (1.0 - candidate.strength) + assistX,
            accumulatedDY * (1.0 - candidate.strength) + assistY,
        )
    }

    fun angleDifference(target: Float, current: Float): Float {
        var difference = (target - current) % 360f
        if (difference > 180f) difference -= 360f
        if (difference < -180f) difference += 360f
        return difference
    }

    private fun rotationGcd(): Double {
        val sensitivity = mc.options.sensitivity().get()
        val base = sensitivity * 0.6 + 0.2
        return (base * base * base * 8.0 * 0.15).coerceAtLeast(0.0001)
    }
}
