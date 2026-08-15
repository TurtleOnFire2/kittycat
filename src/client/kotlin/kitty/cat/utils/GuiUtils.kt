package kitty.cat.utils

import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import kitty.cat.render.skija.SkijaRenderer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object GuiUtils {
    fun renderRectangle(guiGraphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        val left = minOf(x, x + width)
        val right = maxOf(x, x + width)
        val top = minOf(y, y + height)
        val bottom = maxOf(y, y + height)

        val rectWidth = right - left
        val rectHeight = bottom - top
        if (rectWidth <= 0 || rectHeight <= 0) return
        drawWithNanoVG(guiGraphics) { scale ->
            roundedRect(left * scale, top * scale, rectWidth * scale, rectHeight * scale, 0f, color)
        }
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
            renderRectangle(guiGraphics, left, top, rectWidth, rectHeight, color)
            return
        }
        drawWithNanoVG(guiGraphics) { scale ->
            roundedRect(
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
            roundedRectStroke(
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

    private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        if (RenderSystem.outputColorTextureOverride?.texture()?.let(SkijaRenderer::supports) == true) {
            SkijaRenderer.roundedRect(x, y, width, height, radius, color)
        } else {
            NVGRenderer.roundedRect(x, y, width, height, radius, color)
        }
    }

    private fun roundedRectStroke(x: Float, y: Float, width: Float, height: Float, radius: Float, strokeWidth: Float, color: Int) {
        if (RenderSystem.outputColorTextureOverride?.texture()?.let(SkijaRenderer::supports) == true) {
            SkijaRenderer.roundedRectStroke(x, y, width, height, radius, strokeWidth, color)
        } else {
            NVGRenderer.roundedRectStroke(x, y, width, height, radius, strokeWidth, color)
        }
    }
}
