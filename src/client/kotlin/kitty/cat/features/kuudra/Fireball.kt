package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.gui.categories.Categories.Category
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.isEtherwarpItem
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3

object Fireball : Feature("Fireball", "", Category.KUUDRA) {

    val pile = selectorSetting("Start pile", listOf("Tri", "X", "Slash", "Equals"))

    val looks = listOf(
        Pair(0f, 0f), // Tri -> X
        Pair(1f, 0f), // X -> X+Slash
        Pair(2f, 0f), // X+Slash -> Slash
        Pair(3f, 0f), // Slash -> Equals
        Pair(4f, 0f), // Equals -> Equals+Tri
        Pair(5f, 0f)  // Equals+Tri -> Tri
    )

    val positions = listOf<Vec3>()

    var firstTp = true

    var queuedWarps = 0
    var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!enabled || !kuudra() || !build()) {
                ticks = 0
                return@register
            }

            if (mc.player?.isCrouching != true) return@register
            if (mc.player?.mainHandItem?.isEtherwarpItem() != true) return@register

            val offset = getOffset()

            //Set rotation only since the first one needs to be aligned.
            if (firstTp) {
                firstTp = false
                mc.options.keyAttack.clickCount++
                RotationUtils.applyGcd(looks[offset].first, looks[offset].second)
                return@register
            }

            //Offset by one since it actually takes the previous rotation
            val look = looks[(ticks++ + offset) % 6 + 1]

            mc.options.keyAttack.clickCount++
            mc.options.keyUse.clickCount++
            queuedWarps++
            RotationUtils.applyGcd(look.first, look.second)
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            queuedWarps = 0
            firstTp = true
        }
    }

    fun handlePosition(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !kuudra() || !build() || queuedWarps == 0) return

        if (packet.change.position in positions) queuedWarps--
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