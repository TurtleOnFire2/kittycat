package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

object RotationUtils {
    /** Speeds are degrees/second; durations are seconds. */
    data class Profile(
        val minSpeed: Float = 165f,
        val maxSpeed: Float = 245f,
        val minDuration: Float = 0.10f,
        val maxDuration: Float = 0.90f,
        val overshootChance: Float = 0.78f,
        val minOvershoot: Float = 0.20f,
        val maxOvershoot: Float = 1.65f,
        val flickChance: Float = 0.0f,
        val maxFlick: Float = 0.05f,
        val microCorrection: Float = 0.075f,
        val timeout: Float = 1.5f,
    ) {
        init {
            require(minSpeed > 0f && maxSpeed >= minSpeed)
            require(minDuration >= 0f && maxDuration >= minDuration)
            require(overshootChance in 0f..1f && flickChance in 0f..1f)
            require(minOvershoot >= 0f && maxOvershoot >= minOvershoot)
            require(maxFlick >= 0f && microCorrection >= 0f)
            require(timeout > 0f)
        }
    }

    private data class Rotation(
        val startYaw: Float,
        val startPitch: Float,
        var goalYaw: Float,
        var goalPitch: Float,
        val overYawOffset: Float,
        val overPitchOffset: Float,
        val mainTime: Float,
        val correctionTime: Float,
        val flickCenter: Float,
        val flickWidth: Float,
        val flickYaw: Float,
        val flickPitch: Float,
        val noise: Float,
        val yawPhase: Float,
        val pitchPhase: Float,
        val yawFrequency: Float,
        val pitchFrequency: Float,
        val worldTarget: Vec3?,
        val startedAtNanos: Long,
        val timeoutNanos: Long,
        val onComplete: (() -> Unit)?,
        val onTimeout: (() -> Unit)?,
        var elapsed: Float = 0f,
    ) {
        val duration get() = mainTime + correctionTime
    }

    var defaultProfile = Profile()
    val isRotating get() = current != null

    private var current: Rotation? = null
    private var lastFrameNanos = 0L

    /** Starts a humanized rotation, replacing any rotation already in progress. */
    fun rotate(
        yaw: Float,
        pitch: Float,
        profile: Profile = defaultProfile,
        onComplete: (() -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
    ) {
        startRotation(yaw, pitch, null, profile, onComplete, onTimeout)
    }

    private fun startRotation(
        yaw: Float,
        pitch: Float,
        worldTarget: Vec3?,
        profile: Profile,
        onComplete: (() -> Unit)?,
        onTimeout: (() -> Unit)?,
    ) {
        val player = mc.player ?: return
        val startYaw = player.yRot
        val startPitch = player.xRot
        val goalYaw = startYaw + Mth.wrapDegrees(yaw - startYaw)
        val goalPitch = pitch.coerceIn(-90f, 90f)
        val dy = goalYaw - startYaw
        val dp = goalPitch - startPitch
        val distance = hypot(dy.toDouble(), dp.toDouble()).toFloat()

        if (distance < 0.01f) {
            applyGcd(goalYaw, goalPitch)
            current = null
            onComplete?.invoke()
            return
        }

        val duration = (distance / random(profile.minSpeed, profile.maxSpeed))
            .coerceIn(profile.minDuration, profile.maxDuration)
        val overshoot = if (distance > 3f && Random.nextFloat() < profile.overshootChance) {
            random(profile.minOvershoot, profile.maxOvershoot) * (distance / 45f).coerceIn(0.25f, 1f)
        } else {
            0f
        }
        val unitYaw = dy / distance
        val unitPitch = dp / distance
        val correctionTime = if (overshoot > 0f) max(0.065f, duration * random(0.12f, 0.22f)) else 0f
        val mainTime = max(0.035f, duration - correctionTime)
        val hasFlick = distance > 8f && profile.maxFlick > 0f &&
            Random.nextFloat() < profile.flickChance
        val flick = if (hasFlick) random(profile.maxFlick * 0.25f, profile.maxFlick) else 0f
        val side = if (Random.nextBoolean()) 1f else -1f

        val now = System.nanoTime()
        current = Rotation(
            startYaw,
            startPitch,
            goalYaw,
            goalPitch,
            unitYaw * overshoot,
            (goalPitch + unitPitch * overshoot).coerceIn(-90f, 90f) - goalPitch,
            mainTime,
            correctionTime,
            random(0.34f, 0.72f),
            random(0.045f, 0.09f),
            -unitPitch * flick * side,
            unitYaw * flick * side,
            profile.microCorrection * (distance / 30f).coerceIn(0.25f, 1f),
            random(0f, TWO_PI),
            random(0f, TWO_PI),
            random(1.7f, 2.8f),
            random(2.0f, 3.2f),
            worldTarget,
            now,
            (profile.timeout * NANOS_PER_SECOND).toLong(),
            onComplete,
            onTimeout,
        )
        lastFrameNanos = now
    }

    /** Rotates from the player's eyes toward a fixed world-space point. */
    fun lookAt(
        target: Vec3,
        profile: Profile = defaultProfile,
        onComplete: (() -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
    ) {
        val player = mc.player ?: return
        val (yaw, pitch) = target.getLook(player.getEyePosition(framePartialTick()))
        startRotation(yaw, pitch, target, profile, onComplete, onTimeout)
    }

    fun cancel() {
        current = null
        lastFrameNanos = 0L
    }

    /** Renderer hook: advances the active rotation exactly once per rendered frame. */
    @JvmStatic
    fun onFrame() {
        val state = current ?: return
        val player = mc.player
        if (player == null || mc.level == null) {
            cancel()
            return
        }

        val now = System.nanoTime()
        if (now - state.startedAtNanos >= state.timeoutNanos) {
            current = null
            lastFrameNanos = 0L
            state.onTimeout?.invoke()
            return
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
            return
        }
        val frameTime = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = now
        state.elapsed += frameTime

        state.worldTarget?.let { target ->
            val (yaw, pitch) = target.getLook(player.getEyePosition(framePartialTick()))
            state.goalYaw += Mth.wrapDegrees(yaw - state.goalYaw)
            state.goalPitch = pitch.coerceIn(-90f, 90f)
        }

        if (state.elapsed >= state.duration) {
            applyGcd(state.goalYaw, state.goalPitch)
            current = null
            state.onComplete?.invoke()
            return
        }

        val totalProgress = (state.elapsed / state.duration).coerceIn(0f, 1f)
        val overYaw = state.goalYaw + state.overYawOffset
        val overPitch = (state.goalPitch + state.overPitchOffset).coerceIn(-90f, 90f)
        val (baseYaw, basePitch) = if (state.elapsed < state.mainTime || state.correctionTime == 0f) {
            val p = smootherStep((state.elapsed / state.mainTime).coerceIn(0f, 1f))
            lerp(state.startYaw, overYaw, p) to lerp(state.startPitch, overPitch, p)
        } else {
            val p = smootherStep(
                ((state.elapsed - state.mainTime) / state.correctionTime).coerceIn(0f, 1f),
            )
            lerp(overYaw, state.goalYaw, p) to lerp(overPitch, state.goalPitch, p)
        }

        val flick = pulse(totalProgress, state.flickCenter, state.flickWidth)
        val envelope = sin(PI * totalProgress).toFloat()
        val yawNoise = sin(state.yawPhase + totalProgress * TWO_PI * state.yawFrequency) * state.noise * envelope
        val pitchNoise = sin(state.pitchPhase + totalProgress * TWO_PI * state.pitchFrequency) *
            state.noise * 0.65f * envelope
        applyGcd(
            baseYaw + state.flickYaw * flick + yawNoise,
            basePitch + state.flickPitch * flick + pitchNoise,
        )
    }

    /** Quantizes each delta to the same step produced by Minecraft's mouse sensitivity. */
    private fun applyGcd(yaw: Float, pitch: Float) {
        val player = mc.player ?: return
        val sensitivity = mc.options.sensitivity().get().toFloat()
        val multiplier = sensitivity * 0.6f + 0.2f
        val gcd = multiplier * multiplier * multiplier * 1.2f
        val yawStep = round(Mth.wrapDegrees(yaw - player.yRot) / gcd) * gcd
        val pitchStep = round((pitch - player.xRot) / gcd) * gcd

        player.yRot += yawStep
        player.xRot = (player.xRot + pitchStep).coerceIn(-90f, 90f)
    }

    private fun smootherStep(p: Float) = p * p * p * (p * (p * 6f - 15f) + 10f)
    private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
    private fun framePartialTick() = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

    private fun pulse(progress: Float, center: Float, width: Float): Float {
        val distance = abs(progress - center)
        if (distance >= width) return 0f
        return sin((1f - distance / width) * (PI / 2.0)).toFloat()
    }

    private fun random(min: Float, max: Float) =
        if (min == max) min else Random.nextFloat() * (max - min) + min

    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
