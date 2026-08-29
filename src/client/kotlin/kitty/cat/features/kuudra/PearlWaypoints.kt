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
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.TrajectorySolver
import kitty.cat.utils.TrajectorySolver.toAimPoint
import kitty.cat.utils.aabb
import kitty.cat.utils.lookinAt
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.regex.Pattern

object PearlWaypoints: Feature("Pearl Waypoints", "", Categories.Category.KUUDRA) {
    val offset = numberSetting("Offset", 0.0, 1000.0, 0.0, "ms")
    val doubleOffset = numberSetting("Double pearl offset", 0.0, 1000.0, 0.0, "ms")
    val snap = booleanSetting("Snap to waypoint", false)
    val snapAt = numberSetting("Snap at X time left", 0.0, 5000.0, 0.0, "ms")
    val range = numberSetting("Snap range", 0.0, 2.0, 0.0, "", 0.05)
    val triggerbot = booleanSetting("Triggerbot", false)

    private var lastPos: Vec3? = null
    private var solutions: MutableList<AimPoint> = mutableListOf()
    private var timeSincePickUp: Long = 0
    private var timeSinceLastTitle = 0
    private var tracking = false

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
                    solutions.add(AimPoint(sol.toAimPoint(15.0), it.first, sol.flightTime - offset.value.toInt(), it.third, sol.yaw, sol.pitch))
                }
            } else {
                val pearl = KuudraUtils.dropOffs.firstOrNull { it.first == supply.name || (supply == Supply.Square && it.first == square.name) } ?: return@register
                val sol = TrajectorySolver.solve(false, pos, pearl.second) ?: return@register
                solutions.add(AimPoint(sol.toAimPoint(15.0), pearl.first, sol.flightTime - offset.value.toInt(), pearl.third, sol.yaw, sol.pitch))
            }

            KuudraUtils.doublePearls.forEach {
                val sol = TrajectorySolver.solve(true, pos, it.second) ?: return@forEach
                solutions.add(AimPoint(sol.toAimPoint(30.0), it.first, sol.flightTime - doubleOffset.value.toInt(), it.third, sol.yaw, sol.pitch))
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
                    remaining < snapAt.value &&
                    snap.value &&
                    solution.pos.lookinAt(range.value, 35.0)
                ) {
                    RotationUtils.applyGcd(solution.yaw, solution.pitch)
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
        val pitch: Float
    )
}