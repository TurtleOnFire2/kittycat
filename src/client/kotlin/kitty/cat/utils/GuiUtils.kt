package kitty.cat.utils

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.ceil
import kotlin.math.sqrt

object GuiUtils {
    data class ColoredRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Int
    )

    fun renderRectangle(guiGraphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        val left = minOf(x, x + width)
        val right = maxOf(x, x + width)
        val top = minOf(y, y + height)
        val bottom = maxOf(y, y + height)

        val rectWidth = right - left
        val rectHeight = bottom - top
        if (rectWidth <= 0 || rectHeight <= 0) return
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
            renderRectangle(guiGraphics, left, top, rectWidth, rectHeight, color)
            return
        }
        fillRounded(guiGraphics, left, top, rectWidth, rectHeight, cornerRadius, color)
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

        fillRoundedOutline(guiGraphics, left, top, rectWidth, rectHeight, outerRadius, outlineThickness, color)
    }

    /** Draws many rectangles in a single PIP render target. */
    fun renderRectangles(guiGraphics: GuiGraphicsExtractor, rectangles: Iterable<ColoredRect>) {
        rectangles.forEach { rect ->
            if (rect.width > 0 && rect.height > 0) {
                guiGraphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, rect.color)
            }
        }
    }

    private fun fillRounded(gui: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
        for (row in 0 until height) {
            val inset = roundedInset(row, height, radius)
            gui.fill(x + inset, y + row, x + width - inset, y + row + 1, color)
        }
    }

    private fun fillRoundedOutline(
        gui: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int,
        radius: Int, thickness: Int, color: Int
    ) {
        val innerWidth = width - thickness * 2
        val innerHeight = height - thickness * 2
        val innerRadius = (radius - thickness).coerceAtLeast(0)
        for (row in 0 until height) {
            val outerInset = roundedInset(row, height, radius)
            val left = x + outerInset
            val right = x + width - outerInset
            val innerRow = row - thickness
            if (innerWidth <= 0 || innerHeight <= 0 || innerRow !in 0 until innerHeight) {
                gui.fill(left, y + row, right, y + row + 1, color)
                continue
            }
            val innerInset = roundedInset(innerRow, innerHeight, innerRadius)
            val innerLeft = x + thickness + innerInset
            val innerRight = x + width - thickness - innerInset
            if (left < innerLeft) gui.fill(left, y + row, innerLeft, y + row + 1, color)
            if (innerRight < right) gui.fill(innerRight, y + row, right, y + row + 1, color)
        }
    }

    private fun roundedInset(row: Int, height: Int, radius: Int): Int {
        if (radius <= 0 || row in radius until (height - radius)) return 0
        val distanceFromCenter = radius - 0.5 - if (row < radius) row else height - 1 - row
        return ceil(radius - sqrt(radius * radius - distanceFromCenter * distanceFromCenter)).toInt()
    }
}
