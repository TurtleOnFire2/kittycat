package kitty.cat.features.huds

import kitty.cat.KittycatClient.mc

import kitty.cat.features.kuudra.Supplies
import kitty.cat.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket

object SupplyHud : Hud.Component("BackboneHud", 0.0, 0.0, 1f, staticRenderConditions = mutableListOf(Hud.Condition.Always)) {

    private val colorCodeRegex = Regex("§[0-9a-fk-or]")

    var progress: Component? = null
    private var timeSinceLastTitle = 0

    fun handleTitle(packet: ClientboundSetTitleTextPacket): Boolean {
        if (!Supplies.pickUpHud.value) return

        val raw = packet.text.string.replace(colorCodeRegex, "")

        if (!raw.contains("[||||||||||||||||||||]")) return false

        progress = packet.text

        timeSinceLastTitle = 0
        return true
    }

    fun serverTick() {
        timeSinceLastTitle += 50

        if (timeSinceLastTitle >= 750) {
            progress = null
        }
    }

    override fun render(context: GuiGraphicsExtractor) {
        if (!Supplies.pickUpHud.value || progress == null) return

        context.text(mc.font, progress!!, 0, 0, -1)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "[||||||||||||||||||||] 100%", 0, 0, -1)
    }

    override fun bounds(): Pair<Double, Double> {
        return Pair(mc.font.width("[||||||||||||||||||||] 100%").toDouble(), mc.font.lineHeight.toDouble())
    }
}