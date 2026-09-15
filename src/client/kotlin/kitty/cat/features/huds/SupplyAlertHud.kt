package kitty.cat.features.huds

import io.github.humbleui.skija.Color
import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.Supplies
import kitty.cat.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor

object SupplyAlertHud : Hud.Component(
    "SupplyAlertHud",
    0.5,
    0.35,
    1f,
    staticRenderConditions = mutableListOf(Hud.Condition.Always),
) {
    var time = System.currentTimeMillis()
    var text = ""

    override fun render(context: GuiGraphicsExtractor) {
        if (!Supplies.enabled || !Supplies.alertHud.value) return

        if (System.currentTimeMillis() - time > 1000) return

        context.text(mc.font, text, 0, 0, Color.RED)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "Already picking!", 0, 0, Color.RED)
    }

    override fun bounds(): Pair<Double, Double> = Pair(
        mc.font.width("Already picking!").toDouble(),
        mc.font.lineHeight.toDouble(),
    )
}
