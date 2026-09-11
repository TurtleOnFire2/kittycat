package kitty.cat.features.huds

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.Build
import kitty.cat.gui.Hud
import kitty.cat.utils.KuudraUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.roundToInt

object BuildHud : Hud.Component("BuildHud", 0.0, 0.0, 1f) {
    private const val SECONDARY_SCALE = 0.75f
    private const val ALERT_SCALE = 2f
    private const val GAP = 2
    private val alertText = Component.literal("GO STUN!").withStyle(ChatFormatting.BOLD)

    private fun showStunAlert(): Boolean =
        Build.stunAlert.value && Build.buildProgress > Build.stunThreshold.value

    override fun render(context: GuiGraphicsExtractor) {
        if (!Build.enabled || !KuudraUtils.build()) return
        if (showStunAlert()) drawStunAlert(context)
        else if (Build.progressHud.value) drawProgress(context, Build.buildProgress, Build.pileProgress)
    }

    override fun example(context: GuiGraphicsExtractor) {
        if (showStunAlert()) drawStunAlert(context)
        else drawProgress(context, 75, 40)
    }

    private fun drawStunAlert(context: GuiGraphicsExtractor) {
        val pose = context.pose()
        pose.pushMatrix()
        val normal = progressBounds()
        pose.translate(
            ((normal.first - mc.font.width(alertText) * ALERT_SCALE) / 2).toFloat(),
            ((normal.second - mc.font.lineHeight * ALERT_SCALE) / 2).toFloat()
        )
        pose.scale(ALERT_SCALE)
        context.text(mc.font, alertText, 0, 0, Color(144, 238, 144).rgb)
        pose.popMatrix()
    }

    private fun drawProgress(context: GuiGraphicsExtractor, build: Int, pile: Int) {
        val width = progressBounds().first.toFloat()
        val pileFirst = Build.progressHudOrder.selectedSingle == "Pile first"
        drawPercentage(context, if (pileFirst) pile else build, width)

        val pose = context.pose()
        pose.pushMatrix()
        pose.translate(0f, (mc.font.lineHeight + GAP).toFloat())
        pose.scale(SECONDARY_SCALE)
        drawPercentage(context, if (pileFirst) build else pile, width / SECONDARY_SCALE)
        pose.popMatrix()
    }

    private fun drawPercentage(context: GuiGraphicsExtractor, progress: Int, width: Float) {
        val percentage = progress.coerceIn(0, 100)
        if (percentage == 0) return
        val text = Component.literal("$percentage%").withStyle(ChatFormatting.BOLD)
        val color = Build.progressColor(percentage).rgb
        context.text(mc.font, text, ((width - mc.font.width(text)) / 2).roundToInt(), 0, color)
    }

    override fun bounds(): Pair<Double, Double> = if (showStunAlert()) Pair(
        (mc.font.width(alertText) * ALERT_SCALE).toDouble(),
        (mc.font.lineHeight * ALERT_SCALE).toDouble()
    ) else progressBounds()

    override fun offsetBounds(width: Int, height: Int): Pair<Int, Int> {
        if (!showStunAlert()) return Pair(0, 0)
        val normal = progressBounds()
        val alert = bounds()
        return Pair(
            ((normal.first - alert.first) * scale / 2).roundToInt(),
            ((normal.second - alert.second) * scale / 2).roundToInt()
        )
    }

    private fun progressBounds(): Pair<Double, Double> = Pair(
        mc.font.width(Component.literal("100%").withStyle(ChatFormatting.BOLD)).toDouble(),
        mc.font.lineHeight * (1.0 + SECONDARY_SCALE) + GAP
    )
}
