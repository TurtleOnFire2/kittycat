package kitty.cat.features.huds

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.BackboneAlert
import kitty.cat.gui.Hud
import net.minecraft.client.gui.GuiGraphicsExtractor

object BackboneHud : Hud.Component("BackboneHud", 0.0, 0.0, 1f, staticRenderConditions = mutableListOf(Hud.Condition.Always)) {
    var render = false

    override fun render(context: GuiGraphicsExtractor) {
        if (!render || !BackboneAlert.enabled) return
        context.text(mc.font, "BACKBONE", 0, 0, -1)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "BACKBONE", 0, 0, -1)
    }

    override fun bounds(): Pair<Double, Double> {
        return Pair(mc.font.width("BACKBONE").toDouble(), mc.font.lineHeight.toDouble())
    }
}