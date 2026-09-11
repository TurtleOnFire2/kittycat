package kitty.cat.features.huds

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.KuudraDisplay
import kitty.cat.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor

object KuudraHpHud : Hud.Component("KuudraHpHud", 0.5, 0.1, 1f) {
    private val preview = KuudraDisplay.formatHealth(20000f)

    override fun render(context: GuiGraphicsExtractor) {
        if (!KuudraDisplay.enabled || !KuudraDisplay.showHp.value || KuudraDisplay.hpString.isEmpty()) return
        context.text(mc.font, KuudraDisplay.hpString, 0, 0, -1)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, preview, 0, 0, -1)
    }

    override fun bounds(): Pair<Double, Double> = Pair(
        mc.font.width(KuudraDisplay.hpString.ifEmpty { preview }).toDouble(),
        mc.font.lineHeight.toDouble()
    )
}
