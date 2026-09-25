package kitty.cat.features.huds

import io.github.humbleui.skija.Color
import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.Alerts
import kitty.cat.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor

object AlertHud : Hud.Component(
    "AlertHud",
    0.5,
    0.35,
    1f,
    staticRenderConditions = mutableListOf(Hud.Condition.Always),
) {
    override fun render(context: GuiGraphicsExtractor) {
        if (!Alerts.enabled) return

        if (Alerts.count <= 0) return

        val time = "%.2f".format(Alerts.count / 20.0)

        context.text(mc.font, "${Alerts.text} §f$time", 0, 0, Color.CYAN)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "Supplies: §f8.85s", 0, 0, Color.CYAN)
    }

    override fun bounds(): Pair<Double, Double> = Pair(
        mc.font.width("Supplies: §f8.85s").toDouble(),
        mc.font.lineHeight.toDouble(),
    )
}