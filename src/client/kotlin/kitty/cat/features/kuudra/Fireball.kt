package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories.Category
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.getLook
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.isEtherwarpItem
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

    val looks = listOf(
        Pair(90f, 8.2f), // Tri -> X
        Pair(30f, 9.3f), // X -> X+Slash
        Pair(-27f, 8.5f), // X+Slash -> Slash
        Pair(-90f, 8.2f), // Slash -> Equals
        Pair(-150f, 9.3f), // Equals -> Equals+Tri
        Pair(153f, 8.2f)  // Equals+Tri -> Tri
    )

    val positions = listOf(
        Vec3(-97.5, 79.05, -113.5), //Tri
        Vec3(-106.5, 79.05, -113.5),
        Vec3(-110.5, 79.05, -106.5),
        Vec3(-106.5, 79.05, -98.5),
        Vec3(-97.5, 79.05, -98.5),
        Vec3(-93.5, 79.05, -105.5),
    )

    var ticks = 0
    var cd = 0
    var expectedLastTp: Vec3? = null
    var done = false

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !kuudra()) return@register

            val offset = getOffset()
            val startPos = positions[offset]

            ctx.renderBoxBounds(startPos.aabb(0.5), Color.RED)

            ctx.renderBoxBounds(expectedLastTp?.aabb(1.0) ?: return@register, Color.WHITE)
        }
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!enabled || !kuudra() || !build()) {
                ticks = 0
                expectedLastTp = null
                return@register
            }

            if (mc.player?.isCrouching != true) return@register

            val dP = Vec3(mc.player?.x ?: return@register, 79.05, mc.player?.z ?: return@register)
            if (dP !in positions) return@register

            val threshold = if (assumeStun.value) stunThreshold.value else dpsThreshold.value
            if (Build.buildProgress >= threshold && expectedLastTp != null && expectedLastTp!!.x > -100) {
                if (!done) return@register

                done = false

                val target = if (assumeStun.value) Vec3(-71.5, 79.0, -102.5) else Vec3(-85.5, 79.0, -77.5)
                val look = target.getLook(expectedLastTp?.add(0.0, 1.27, 0.0) ?: return@register)

                mc.options.keyUse.clickCount++
                RotationUtils.applyGcd(look.first, look.second)

                if (assumeStun.value) {
                    val slot = hotbarSlotFromID("KUUDRA_SHOP_ITEM") ?: return@register
                    schedule(0) {
                        mc.player!!.inventory.selectedSlot = slot
                        schedule(1) {
                            mc.options.keyUse.clickCount++
                        }
                    }
                }
                return@register
            }

            done = true

            if (cd++ % delay.value.toInt() != 0) return@register

            if (mc.player?.mainHandItem?.isEtherwarpItem() != true) return@register

            val offset = getOffset()

            val look = looks[(ticks++ + offset) % 6]

            mc.options.keyAttack.clickCount++
            mc.options.keyUse.clickCount++

            expectedLastTp = positions[(ticks + offset) % 6]

            RotationUtils.applyGcd(look.first, look.second)
        }
    }

    fun handlePosition(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !kuudra() || !build()) return

        if (packet.change.position() == Vec3(-71.5, 79.05, -102.5)) {
            RotationUtils.applyGcd(-90f, 0f)
        }
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

    fun clickThroughEther(): Boolean {
        return enabled && build() && mc.player?.mainHandItem?.isEtherwarpItem() == true
    }
}