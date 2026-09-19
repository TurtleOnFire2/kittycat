package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.debug.PearlLandingDebug
import kitty.cat.features.settings.cheat
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.aabb
import kitty.cat.utils.AimAssist
import kitty.cat.utils.Chat
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.getLook
import kitty.cat.utils.lore
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.floor
import kotlin.math.hypot

object EtherwarpWaypoints : Feature(
    "Etherwarp Waypoints",
    "Shows offset waypoints relative to your current pearl's predicted landing.",
    Categories.Category.KUUDRA
) {
    val yOffset = booleanSetting("You probably dont want to turn this off", true)

    val aimAssist = booleanSetting("Aim assist", false).cheat()
    val aimAssistFov = numberSetting("Aim assist FOV", 1.0, 180.0, 40.0, "°", 1.0).cheat()
    val aimAssistStrength = numberSetting("Aim assist strength", 0.01, 1.0, 0.5, "", 0.005).cheat()

    val autoWarpOnSupply = booleanSetting("Auto warp on supply place", false).cheat()
    val delay = numberSetting("Delay", 0.0, 10.0, 1.0, "",1.0).cheat()

    val noLook = booleanSetting("Serverside click (Ignores FOV)", false).cheat()
    val autoWarpFov = numberSetting("Auto warp FOV", 1.0, 180.0, 20.0, "°", 1.0).cheat()

    private val placedRegex = Regex("(.+) recovered one of Elle's supplies!")

    private val warpedCrates = mutableSetOf<Crate>()

    fun handleChat(unformatted: String) {
        if (!enabled || !autoWarpOnSupply.value) return
        val player = mc.player ?: return

        placedRegex.find(unformatted)?.destructured?.let { (name) ->
            if (!name.contains(mc.player!!.name.string)) return
        } ?: return

        var slot: Int? = null

        for (i in 0..7) {
            val uuid = mc.player!!.inventory.getItem(i).uuid()

            if (uuid in listOf("ETHERWARP_CONDUIT", "ASPECT_OF_THE_VOID")) {
                slot = i
            }
        }

        slot ?: return

        if (mc.player?.inventory?.selectedSlot != slot) mc.player?.inventory?.selectedSlot = slot

        schedule(delay.value) {
            val waypoint = waypoints.find { it.third == CratePriority.missing } ?: return@schedule
            if (waypoint.third in warpedCrates) return@schedule

            if (noLook.value) {
                ClickUtils.queueClick(waypoint.second)
                warpedCrates.add(waypoint.third)

                return@schedule
            }

            val rotation = waypoint.second.getLook(player.eyePosition)

            if (!AimAssist.withinFov(rotation.first, rotation.second, player.yRot, player.xRot, autoWarpFov.value)) {
                return@schedule
            }

            RotationUtils.applyGcd(rotation.first, rotation.second)

            mc.options.keyUse.clickCount++
            warpedCrates.add(waypoint.third)
        }
    }

    val waypoints = listOf(
        Triple("Square", Vec3(-138.5, 79.0, -87.5), Crate.Square),
        Triple("Shop", Vec3(-77.5, 79.0, -134.5), Crate.Shop),
        Triple("XC", Vec3(-129.5, 79.0, -114.5), Crate.xCannon),
    )

    var pearlLanding: Vec3? = null
        private set

    private fun matchingWaypoints() = waypoints.asSequence()
        .filter { it.third == CratePriority.missing }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            pearlLanding = if (enabled) PearlLandingDebug.currentPredictedLanding() else null
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            pearlLanding = null
            warpedCrates.clear()
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            val player = mc.player ?: return@register
            val landing = pearlLanding

            if (landing == null) {
                matchingWaypoints().forEach { waypoint ->
                    ctx.renderBoxBounds(waypoint.second.aabb(1.0).setMaxY(79.05).setMinY(79.0), Color.CYAN)
                    ctx.renderString(waypoint.first, waypoint.second.add(0.0, 1.0, 0.0), Color.WHITE, 4f)
                }
                return@register
            }

            val centered = Vec3(floor(landing.x) + 0.5, floor(landing.y) + 1.0, floor(landing.z) + 0.5)

            matchingWaypoints().forEach { waypoint ->
                val offset = waypoint.second.subtract(centered)

                val pos = player.position().add(offset)
                var y = 0.0

                if (yOffset.value) y = 1.0

                ctx.renderBoxBounds(pos.add(y).aabb(1.0).setMaxY(pos.y + 1.05 + y).setMinY(pos.y + 1.0 + y), Color.CYAN)
                ctx.renderString(waypoint.first, player.position().add(offset).add(0.0, 3.0, 0.0), Color.WHITE, 4f)
            }
        }
    }

    fun onTurn(accumulatedDX: Double, accumulatedDY: Double): DoubleArray? {
        val player = mc.player ?: return null
        if (!enabled || !aimAssist.value || !supplies()) return null

        val landing = pearlLanding ?: return null
        val centered = landing.let {
            Vec3(floor(it.x) + 0.5, floor(it.y) + 1.0, floor(it.z) + 0.5)
        }
        val candidate = matchingWaypoints().mapNotNull { waypoint ->
            if (player.position().distanceToSqr(waypoint.second) <= 100.0) return@mapNotNull null
            val target = run {
                var y = 0.0
                if (yOffset.value) y = 1.0
                player.position().add(waypoint.second.subtract(centered)).add(0.0, +1.0, 0.0).add(y)
            }
            if (player.eyePosition.y < target.y) return@mapNotNull null
            AimAssist.candidate(target, aimAssistFov.value, aimAssistStrength.value)
        }.minByOrNull { it.distance } ?: return null

        return AimAssist.adjustMouse(accumulatedDX, accumulatedDY, candidate)
    }
}
