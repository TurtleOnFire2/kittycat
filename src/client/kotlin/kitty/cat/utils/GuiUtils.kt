package kitty.cat.utils

import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object GuiUtils {
    fun renderRectangle(guiGraphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        val left = minOf(x, x + width)
        val right = maxOf(x, x + width)
        val top = minOf(y, y + height)
        val bottom = maxOf(y, y + height)

        guiGraphics.fill(left, top, right, bottom, color)
    }

    fun renderRoundedRectangle(
        guiGraphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        color: Int
    ) {
        val left = minOf(x, x + width)
        val right = maxOf(x, x + width)
        val top = minOf(y, y + height)
        val bottom = maxOf(y, y + height)

        val rectWidth = right - left
        val rectHeight = bottom - top

        if (rectWidth <= 0 || rectHeight <= 0) return

        val cornerRadius = radius.coerceAtLeast(0).coerceAtMost(minOf(rectWidth, rectHeight) / 2)
        if (cornerRadius == 0) {
            guiGraphics.fill(left, top, right, bottom, color)
            return
        }
        drawWithNanoVG(guiGraphics) { scale ->
            NVGRenderer.roundedRect(
                x = left * scale,
                y = top * scale,
                width = rectWidth * scale,
                height = rectHeight * scale,
                radius = cornerRadius * scale,
                color = color
            )
        }
    }

    fun renderRoundedOutline(
        guiGraphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        thickness: Int,
        color: Int
    ) {
        val left = minOf(x, x + width)
        val right = maxOf(x, x + width)
        val top = minOf(y, y + height)
        val bottom = maxOf(y, y + height)

        val rectWidth = right - left
        val rectHeight = bottom - top
        if (rectWidth <= 0 || rectHeight <= 0) return

        val outlineThickness = thickness.coerceAtLeast(1).coerceAtMost(minOf(rectWidth, rectHeight) / 2)
        val outerRadius = radius.coerceIn(0, minOf(rectWidth, rectHeight) / 2)

        drawWithNanoVG(guiGraphics) { scale ->
            NVGRenderer.roundedRectStroke(
                x = left * scale,
                y = top * scale,
                width = rectWidth * scale,
                height = rectHeight * scale,
                radius = outerRadius * scale,
                strokeWidth = outlineThickness * scale,
                color = color
            )
        }
    }

    private fun drawWithNanoVG(guiGraphics: GuiGraphicsExtractor, draw: (scale: Float) -> Unit) {
        val window = Minecraft.getInstance().window
        val sw = window.guiScaledWidth
        val sh = window.guiScaledHeight
        val scale = window.guiScale.toFloat()

        NVGPIPRenderer.draw(guiGraphics, 0, 0, sw, sh) {
            draw(scale)
        }
    }
}
