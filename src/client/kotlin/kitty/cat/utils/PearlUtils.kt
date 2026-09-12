package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object TrajectorySolver {

    private const val SPEED = 1.5
    private const val DRAG = 0.99

    private const val EPS = 1.0E-12
    private const val SEARCH_RADIUS = 4
    private const val NEWTON_ITERATIONS = 20
    private const val MAX_SIMULATION_TICKS = 125

    fun solve(
        sky: Boolean,
        start: Vec3,
        target: Vec3,
        trajectoryOffset: Float = 0f
    ): PearlSolution? {
        val dx = target.x - start.x
        val dy = target.y + trajectoryOffset - start.y
        val dz = target.z - start.z

        val horizontalDistance = hypot(dx, dz)

        if (horizontalDistance < EPS) {
            return null
        }

        val yawRad = atan2(-dx, dz)
        val yaw = wrapDegrees(Math.toDegrees(yawRad).toFloat())

        val ticks = if (sky) {
            solveHighestAngleTick(horizontalDistance, dy)
        } else {
            solveBestIntegerTick(horizontalDistance, dy)
        }

        if (ticks <= 0) {
            return null
        }

        val factor = horizontalFactor(ticks.toDouble())
        val gravityCorrection = gravityCorrection(ticks.toDouble())

        val cosPitch = (
                horizontalDistance / (SPEED * factor)
                ).coerceIn(-1.0, 1.0)

        val sinPitch = (
                -(dy + gravityCorrection) / (SPEED * factor)
                ).coerceIn(-1.0, 1.0)

        val pitchRad = atan2(sinPitch, cosPitch)
        val pitch = Math.toDegrees(pitchRad).toFloat()

        return PearlSolution(
            flightTime = ticks * 50L,
            yaw = yaw,
            pitch = pitch
        )
    }

    private fun solveBestIntegerTick(
        horizontalDistance: Double,
        dy: Double
    ): Int {
        val seed = seedTicksFromHorizontal(horizontalDistance)
        var t = max(1.0, seed)

        repeat(NEWTON_ITERATIONS) {
            val f = flightEquation(
                t,
                horizontalDistance,
                dy
            )

            val derivative = flightEquationDerivative(
                t,
                dy
            )

            if (abs(derivative) < EPS) {
                return@repeat
            }

            var next = t - f / derivative

            if (!next.isFinite()) {
                return@repeat
            }

            next = max(1.0, next)

            if (abs(next - t) < 1.0E-10) {
                t = next
                return@repeat
            }

            t = next
        }

        val center = max(1, t.roundToInt())

        var bestTick = -1
        var bestError = Double.POSITIVE_INFINITY

        val minTick = max(1, center - SEARCH_RADIUS)
        val maxTick = center + SEARCH_RADIUS

        for (tick in minTick..maxTick) {
            val factor = horizontalFactor(tick.toDouble())

            if (factor <= 0.0) {
                continue
            }

            val cosPitch =
                horizontalDistance / (SPEED * factor)

            if (abs(cosPitch) > 1.000000001) {
                continue
            }

            val sinPitch =
                -(dy + gravityCorrection(tick.toDouble())) /
                        (SPEED * factor)

            if (abs(sinPitch) > 1.000000001) {
                continue
            }

            val error = abs(
                flightEquation(
                    tick.toDouble(),
                    horizontalDistance,
                    dy
                )
            )

            if (error < bestError) {
                bestError = error
                bestTick = tick
            }
        }

        return bestTick
    }

    private fun solveHighestAngleTick(
        horizontalDistance: Double,
        dy: Double
    ): Int {
        var bestTick = -1
        var bestError = Double.POSITIVE_INFINITY

        for (tick in 1..MAX_SIMULATION_TICKS) {
            val t = tick.toDouble()

            val factor = horizontalFactor(t)

            if (factor <= EPS) {
                continue
            }

            val cosPitch =
                horizontalDistance / (SPEED * factor)

            val sinPitch =
                -(dy + gravityCorrection(t)) /
                        (SPEED * factor)

            if (abs(cosPitch) > 1.05) {
                continue
            }

            if (abs(sinPitch) > 1.05) {
                continue
            }

            val clampedCos = cosPitch.coerceIn(-1.0, 1.0)
            val clampedSin = sinPitch.coerceIn(-1.0, 1.0)

            val error = abs(
                clampedCos * clampedCos +
                        clampedSin * clampedSin -
                        1.0
            )

            if (error > 0.02) {
                continue
            }

            if (
                tick > bestTick ||
                (tick == bestTick && error < bestError)
            ) {
                bestTick = tick
                bestError = error
            }
        }

        return bestTick
    }

    private fun seedTicksFromHorizontal(
        horizontalDistance: Double
    ): Double {
        val requiredFactor =
            horizontalDistance / SPEED

        /*
         * DRAG / (1 - DRAG)
         *
         * 0.99 / 0.01 ~= 99
         */
        val maxFactor = 98.99999999999991

        val ratio = (
                requiredFactor / maxFactor
                ).coerceIn(
                0.0,
                0.999999999999
            )

        val remaining = 1.0 - ratio

        val ticks =
            ln(remaining) / ln(DRAG)

        return max(1.0, ticks)
    }

    private fun flightEquation(
        t: Double,
        horizontalDistance: Double,
        dy: Double
    ): Double {
        val terms = computeTerms(t)

        val speedTerm =
            SPEED * terms.a

        val verticalTerm =
            dy + terms.c

        return horizontalDistance * horizontalDistance +
                verticalTerm * verticalTerm -
                speedTerm * speedTerm
    }

    private fun flightEquationDerivative(
        t: Double,
        dy: Double
    ): Double {
        val terms = computeTerms(t)

        return 2.0 *
                (dy + terms.c) *
                terms.cp -
                4.5 *
                terms.a *
                terms.ap
    }

    private fun horizontalFactor(t: Double): Double {
        val dragPower = DRAG.pow(t)

        val geometric =
            (1.0 - dragPower) /
                    (1.0 - DRAG)

        return DRAG * geometric
    }

    private fun gravityCorrection(t: Double): Double {
        val dragPower = DRAG.pow(t)

        val geometric =
            (1.0 - dragPower) /
                    (1.0 - DRAG)

        return 2.969999999999997 *
                (t - geometric)
    }

    private fun computeTerms(t: Double): Terms {
        val oneMinusDrag = 1.0 - DRAG
        val inverse = 1.0 / oneMinusDrag

        val logDrag = ln(DRAG)
        val dragPower = DRAG.pow(t)

        val geometric =
            (1.0 - dragPower) * inverse

        val a =
            DRAG * geometric

        val geometricDerivative =
            -dragPower *
                    logDrag *
                    inverse

        val ap =
            DRAG * geometricDerivative

        val gravityScale =
            0.029699999999999997 *
                    inverse

        val c =
            gravityScale *
                    (t - geometric)

        val cp =
            gravityScale *
                    (1.0 - geometricDerivative)

        return Terms(
            a = a,
            ap = ap,
            c = c,
            cp = cp
        )
    }

    fun PearlSolution.toAimPoint(
        distance: Double
    ): Vec3 {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)

        return mc.player!!.eyePosition.add(
            x * distance,
            y * distance,
            z * distance
        )
    }

    private fun wrapDegrees(value: Float): Float {
        var result = value % 360.0f

        if (result >= 180.0f) {
            result -= 360.0f
        }

        if (result < -180.0f) {
            result += 360.0f
        }

        return result
    }

    private data class Terms(
        val a: Double,
        val ap: Double,
        val c: Double,
        val cp: Double
    )

    data class PearlSolution(
        val flightTime: Long,
        val yaw: Float,
        val pitch: Float
    )
}