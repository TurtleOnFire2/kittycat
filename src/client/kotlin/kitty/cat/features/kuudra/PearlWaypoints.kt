package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.Supply
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.TrajectorySolver
import kitty.cat.utils.TrajectorySolver.toAimPoint
import kitty.cat.utils.aabb
import kitty.cat.utils.lookinAt
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.sqrt

object PearlWaypoints: Feature("Pearl Waypoints", "", Categories.Category.KUUDRA) {
    val offset = numberSetting("Offset", 0.0, 1000.0, 0.0, "ms")
    val doubleOffset = numberSetting("Double pearl offset", 0.0, 1000.0, 0.0, "ms")
    val triggerbot = booleanSetting("Triggerbot", false)
    val singleAimAssist = booleanSetting("Single pearl aim assist", false)
    val singleAimAssistFov = numberSetting("Single pearl aim assist FOV", 5.0, 180.0, 20.0, "°", 1.0)
    val singleAimAssistStrength = numberSetting("Single pearl aim assist strength", 0.01, 1.0, 0.5, "", 0.005)
    val doubleAimAssist = booleanSetting("Double pearl aim assist", false)
    val doubleAimAssistFov = numberSetting("Double pearl aim assist FOV", 5.0, 180.0, 20.0, "°", 1.0)
    val doubleAimAssistStrength = numberSetting("Double pearl aim assist strength", 0.01, 1.0, 0.5, "", 0.005)

    private var lastPos: Vec3? = null
    private var solutions: MutableList<AimPoint> = mutableListOf()
    private var timeSincePickUp: Long = 0
    private var timeSinceLastTitle = 0
    private var tracking = false
    private var preparedPearlThrow = false
    private val aimAssistTimeouts = mutableMapOf<Pair<String, Boolean>, Long>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!enabled || !kuudra() || !supplies()) return@register

            val pos = mc.player?.position()?.add(0.0, mc.player!!.eyeHeight.toDouble(), 0.0) ?: return@register
            if (pos == lastPos) return@register
            lastPos = pos
            solutions.clear()

            val supply = KuudraUtils.getSupply()
            val square = KuudraUtils.square

            if (supply == Supply.Square && square == Supply.None) {
                KuudraUtils.activeDropOffs.forEach {
                    val sol = TrajectorySolver.solve(false, pos, it.second) ?: return@forEach
                    solutions.add(AimPoint(sol.toAimPoint(15.0), it.first, sol.flightTime - offset.value.toInt(), it.third, sol.yaw, sol.pitch, false))
                }
            } else {
                val pearl = KuudraUtils.dropOffs.firstOrNull { it.first == supply.name || (supply == Supply.Square && it.first == square.name) } ?: return@register
                val sol = TrajectorySolver.solve(false, pos, pearl.second) ?: return@register
                solutions.add(AimPoint(sol.toAimPoint(15.0), pearl.first, sol.flightTime - offset.value.toInt(), pearl.third, sol.yaw, sol.pitch, false))
            }

            KuudraUtils.doublePearls.forEach {
                val sol = TrajectorySolver.solve(true, pos, it.second) ?: return@forEach
                solutions.add(AimPoint(sol.toAimPoint(30.0), it.first, sol.flightTime - doubleOffset.value.toInt(), it.third, sol.yaw, sol.pitch, true))
            }
        }
        LevelRenderEvents.END_MAIN.register render@{ ctx ->
            if (!enabled || solutions.isEmpty() || !kuudra() || !supplies()) {
                return@render
            }

            val iterator = solutions.iterator()

            while (iterator.hasNext()) {
                val solution = iterator.next()

                ctx.renderBoxBounds(solution.pos.aabb(0.1), solution.color)

                val delay = 4250 - solution.time
                val remaining = delay - timeSincePickUp
                val time = if (remaining <= 0) {
                    "§2Ready"
                } else {
                    "${remaining}ms"
                }

                if (
                    triggerbot.value &&
                    remaining <= 0 &&
                    solution.pos.lookinAt(0.2, 35.0)
                ) {
                    mc.options.keyUse.clickCount++
                    iterator.remove()
                    break
                }

                ctx.renderString(
                    "${solution.name}: $time",
                    solution.pos.add(0.0, -0.2, 0.0),
                    scale = 4f
                )
            }
        }
    }

    fun onTurn(accumulatedDX: Double, accumulatedDY: Double): DoubleArray? {
        val player = mc.player ?: return null
        if (!enabled || !kuudra() || !supplies() || !tracking || solutions.isEmpty()) return null
        if (player.mainHandItem.item != Items.ENDER_PEARL) return null
        if (abs(accumulatedDX) < 0.001 && abs(accumulatedDY) < 0.001) return null

        data class Candidate(val solution: AimPoint, val yawDifference: Float, val pitchDifference: Float, val distance: Double)

        val hasMultipleSingleWaypoints = solutions.count { !it.isDouble } > 1
        val candidate = solutions.asSequence().mapNotNull { solution ->
            if (!solution.isDouble && hasMultipleSingleWaypoints) return@mapNotNull null
            val enabledForType = if (solution.isDouble) doubleAimAssist.value else singleAimAssist.value
            if (!enabledForType) return@mapNotNull null
            if ((aimAssistTimeouts[solution.timeoutKey] ?: 0L) > System.currentTimeMillis()) return@mapNotNull null

            val fov = if (solution.isDouble) doubleAimAssistFov.value else singleAimAssistFov.value
            val yawDifference = angleDifference(solution.yaw, player.yRot)
            val pitchDifference = solution.pitch - player.xRot
            val halfFov = fov / 2.0
            if (abs(yawDifference) > halfFov || abs(pitchDifference) > halfFov) return@mapNotNull null

            Candidate(
                solution,
                yawDifference,
                pitchDifference,
                sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference.toDouble())
            )
        }.minByOrNull { it.distance } ?: return null

        val scale = rotationGcd() / 0.15
        val neededX = candidate.yawDifference / scale
        val neededY = candidate.pitchDifference / scale
        val neededMagnitude = sqrt(neededX * neededX + neededY * neededY)
        if (neededMagnitude < 1e-6) return null

        val userMagnitude = sqrt(accumulatedDX * accumulatedDX + accumulatedDY * accumulatedDY)
        val strength = if (candidate.solution.isDouble) doubleAimAssistStrength.value else singleAimAssistStrength.value
        val pull = (userMagnitude * strength).coerceAtMost(neededMagnitude)
        val assistX = neededX / neededMagnitude * pull
        val assistY = neededY / neededMagnitude * pull

        return doubleArrayOf(
            accumulatedDX * (1.0 - strength) + assistX,
            accumulatedDY * (1.0 - strength) + assistY,
        )
    }

    fun prepareUseItem(player: Player, interactionHand: InteractionHand) {
        preparedPearlThrow = player.getItemInHand(interactionHand).item == Items.ENDER_PEARL
    }

    fun useItem(result: InteractionResult) {
        val pearlWasThrown = preparedPearlThrow && result.consumesAction()
        preparedPearlThrow = false
        if (!pearlWasThrown || !enabled || !kuudra() || !supplies() || !tracking) return

        val player = mc.player ?: return
        val now = System.currentTimeMillis()
        val hasMultipleSingleWaypoints = solutions.count { !it.isDouble } > 1
        val target = solutions.asSequence().filter { solution ->
            if (!solution.isDouble && hasMultipleSingleWaypoints) return@filter false
            val enabledForType = if (solution.isDouble) doubleAimAssist.value else singleAimAssist.value
            if (!enabledForType || (aimAssistTimeouts[solution.timeoutKey] ?: 0L) > now) return@filter false

            val fov = if (solution.isDouble) doubleAimAssistFov.value else singleAimAssistFov.value
            abs(angleDifference(solution.yaw, player.yRot)) <= fov / 2.0 &&
                abs(solution.pitch - player.xRot) <= fov / 2.0
        }.minByOrNull { solution ->
            val yaw = angleDifference(solution.yaw, player.yRot)
            val pitch = solution.pitch - player.xRot
            yaw * yaw + pitch * pitch
        } ?: return

        aimAssistTimeouts[target.timeoutKey] = now + AIM_ASSIST_THROW_TIMEOUT_MS
        aimAssistTimeouts.entries.removeIf { it.value <= now }
    }

    private fun angleDifference(target: Float, current: Float): Float {
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

    private val colorCodeRegex = Regex("§[0-9a-fk-or]")
    private val progressPattern = Pattern.compile("\\[\\|+]\\s*(\\d+)%")

    fun handleTitle(packet: ClientboundSetTitleTextPacket) {
        val raw = packet.text.string.replace(colorCodeRegex, "")

        if (!raw.contains("[||||||||||||||||||||]")) return
        val matcher = progressPattern.matcher(raw)
        if (!matcher.find()) return
        val percent = matcher.group(1).toInt()

        timeSinceLastTitle = 0

        when (percent) {
            0 -> {
                tracking = true
                timeSincePickUp = 0
            }
            100 -> {
                tracking = false
                timeSincePickUp = 0
            }
        }
    }

    fun serverTick() {
        timeSinceLastTitle += 50

        if (timeSinceLastTitle >= 750) {
            tracking = false
            timeSincePickUp = 0
        }

        if (tracking && timeSincePickUp >= 0) {
            timeSincePickUp += 50
        }
    }

    private data class AimPoint(
        val pos: Vec3,
        val name: String,
        val time: Long,
        val color: Color,
        val yaw: Float,
        val pitch: Float,
        val isDouble: Boolean
    ) {
        val timeoutKey: Pair<String, Boolean> get() = name to isDouble
    }

    private const val AIM_ASSIST_THROW_TIMEOUT_MS = 2_000L
}
