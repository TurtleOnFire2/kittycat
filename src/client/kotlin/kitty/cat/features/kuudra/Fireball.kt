package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories.Category
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.RotationUtils.framePartialTick
import kitty.cat.utils.aabb
import kitty.cat.utils.getLook
import kitty.cat.utils.isEtherwarpItem
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3
import java.awt.Color

object Fireball : Feature("Fireball", "", Category.KUUDRA) {

    init {
        cheat()
    }

    val pile = selectorSetting("Start pile", listOf("Tri", "X", "Slash", "Equals"))
    val delay = numberSetting("Delay", 1.0, 10.0, 1.0)
    val assumeStun = booleanSetting("Assume Stun", false)
    val stunThreshold = numberSetting(
        "Stun threshold", min = 0.0, max = 100.0, defaultValue = 80.0,
        unit = "%", step = 1.0,
    )
    val dpsThreshold = numberSetting(
        "Dps threshold", min = 0.0, max = 100.0, defaultValue = 80.0,
        unit = "%", step = 1.0,
    )
    val warpDelay = numberSetting("Warp delay (Make this like similar to your ping/50)...", 1.0, 10.0, 1.0)



    val looks = listOf(
        Pair(90f, 8.2f), // Tri -> X
        Pair(30f, 9.3f), // X -> X+Slash
        Pair(-27f, 8.5f), // X+Slash -> Slash
        Pair(-90f, 8.2f), // Slash -> Equals
        Pair(-150f, 9.3f), // Equals -> Equals+Tri
        Pair(153f, 8.2f)  // Equals+Tri -> Tri
    )

    val positions = listOf(
        Vec3(-97.5, 79.05, -113.5),
        Vec3(-106.5, 79.05, -113.5),
        Vec3(-110.5, 79.05, -106.5),
        Vec3(-106.5, 79.05, -98.5),
        Vec3(-97.5, 79.05, -98.5),
        Vec3(-93.5, 79.05, -105.5),
    )

    var firstTp = true

    var ticks = 0

    var cd = 0

    var lastTp = 0

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !kuudra() || !supplies()) return@register

            val offset = getOffset()
            val startPos = positions[offset]

            ctx.renderBoxBounds(startPos.aabb(0.5), Color.RED)
        }
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!enabled || !kuudra() || !build()) {
                ticks = 0
                return@register
            }

            if (mc.player?.isCrouching != true) return@register

            if (assumeStun.value) {
                if (lastTp > warpDelay.value && Build.buildProgress > stunThreshold.value) {
                    val look = Vec3(-71.5, 79.0, -102.5).getLook(mc.player?.getEyePosition(framePartialTick()) ?: return@register)

                    mc.options.keyUse.clickCount++
                    RotationUtils.applyGcd(look.first, look.second)
                    lastTp = 0
                    return@register
                }
                return@register
            } else if (Build.buildProgress >= dpsThreshold.value) {
                if (lastTp > warpDelay.value) {
                    val look = Vec3(-85.5, 79.0, -77.5).getLook(mc.player?.getEyePosition(framePartialTick()) ?: return@register)

                    mc.options.keyUse.clickCount++
                    RotationUtils.applyGcd(look.first, look.second)
                    lastTp = 0
                    return@register
                }
                return@register
            }

            val dP = Vec3(mc.player?.x ?: return@register, 79.05, mc.player?.z ?: return@register)
            if (dP !in positions) return@register

            if (cd++ % delay.value.toInt() != 0) return@register

            if (mc.player?.mainHandItem?.isEtherwarpItem() != true) return@register

            val offset = getOffset()

            val look = looks[(ticks++ + offset) % 6]

            mc.options.keyAttack.clickCount++
            mc.options.keyUse.clickCount++

            RotationUtils.applyGcd(look.first, look.second)
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            firstTp = true
        }
    }

    fun serverTick() {
        lastTp++
    }

    fun handlePosition(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !kuudra() || !build()) return

        if (packet.change.position in positions) lastTp = 0
    }

    fun getOffset(): Int {
        return when (pile.selectedSingle) {
            "Tri" -> 0
            "X" -> 1
            "Slash" -> 3
            "Equals" -> 4
            else -> 0
        }
    }
}