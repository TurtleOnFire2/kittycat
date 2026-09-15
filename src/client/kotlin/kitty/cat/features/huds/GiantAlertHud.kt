package kitty.cat.features.huds

import io.github.humbleui.skija.Color
import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.Supplies
import kitty.cat.gui.Hud
import kitty.cat.utils.KuudraUtils.supplies
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.monster.Giant

object GiantAlertHud : Hud.Component(
    "GiantAlertHud",
    0.5,
    0.4,
    1f,
    staticRenderConditions = mutableListOf(Hud.Condition.Always),
) {
    override fun render(context: GuiGraphicsExtractor) {
        if (!Supplies.enabled || !Supplies.giantAlert.value || !supplies()) return

        val eyePos = mc.player?.eyePosition ?: return
        val inGiant = mc.level
            ?.entitiesForRendering()
            ?.filterIsInstance<Giant>()
            ?.any { it.boundingBox.contains(eyePos) }
            ?: false

        if (inGiant) context.text(mc.font, "Standing in giant!", 0, 0, Color.RED)
    }

    override fun example(context: GuiGraphicsExtractor) {
        context.text(mc.font, "Standing in giant!", 0, 0, Color.RED)
    }

    override fun bounds(): Pair<Double, Double> = Pair(
        mc.font.width("Standing in giant!").toDouble(),
        mc.font.lineHeight.toDouble(),
    )
}
