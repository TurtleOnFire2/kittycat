package kitty.cat.render.skija

import io.github.humbleui.skija.*
import io.github.humbleui.types.Rect
import io.github.humbleui.types.RRect
import kitty.cat.features.visual.ClickGui
import kitty.cat.render.nanovg.NVGFont

/** A frame-local display list. No Minecraft GUI primitives or GL calls during extraction. */
class SkijaDraw(private val selectedFont: NVGFont = ClickGui.selectedFont) {
    private val commands = mutableListOf<(Canvas) -> Unit>()

    fun replay(canvas: Canvas) = commands.forEach { it(canvas) }

    fun text(text: String, x: Number, y: Number, color: Int, size: Float = 10f) {
        val font = font(selectedFont, size)
        commands += { canvas ->
            paint.color = color
            canvas.drawString(text, x.toFloat(), y.toFloat() - font.metrics.ascent, font, paint)
        }
    }

    fun centeredText(text: String, x: Number, y: Number, color: Int, size: Float = 10f) {
        text(text, x.toFloat() - font(selectedFont, size).measureTextWidth(text) / 2f, y, color, size)
    }

    fun fieldText(value: String, x: Int, y: Int, width: Int, height: Int, color: Int, centered: Boolean = false, size: Float = 9f) {
        val font = font(selectedFont, size)
        val bounds = font.measureText(value)
        val textX = if (centered) x + (width - bounds.width) / 2f - bounds.left else x + 7f
        val baseline = y + (height - bounds.height) / 2f - bounds.top
        commands += { canvas ->
            val save = canvas.save()
            try {
                canvas.clipRect(Rect.makeXYWH(x + 4f, y.toFloat(), (width - 8).coerceAtLeast(0).toFloat(), height.toFloat()))
                paint.color = color
                canvas.drawString(value, textX, baseline, font, paint)
            } finally { canvas.restoreToCount(save) }
        }
    }

    /** A geometric chevron; rotation is clockwise from right-facing. */
    fun chevron(cx: Float, cy: Float, color: Int, rotation: Float = 0f, size: Float = 4f) {
        commands += { canvas ->
            val save = canvas.save()
            try {
                canvas.translate(cx, cy)
                canvas.rotate(rotation)
                outline.color = color
                outline.strokeWidth = 1.4f
                canvas.drawLine(-size / 2f, -size, size / 2f, 0f, outline)
                canvas.drawLine(size / 2f, 0f, -size / 2f, size, outline)
            } finally { canvas.restoreToCount(save) }
        }
    }

    fun roundedRect(x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int, stroke: Int = 0) {
        if (width <= 0 || height <= 0) return
        commands += { canvas ->
            val p = if (stroke == 0) paint else outline
            p.color = color
            if (stroke > 0) p.strokeWidth = stroke.toFloat()
            val inset = stroke / 2f
            canvas.drawRRect(RRect.makeXYWH(x + inset, y + inset, width - inset * 2, height - inset * 2, radius.toFloat()), p)
        }
    }

    fun enableScissor(x0: Int, y0: Int, x1: Int, y1: Int) {
        commands += { canvas ->
            canvas.save()
            canvas.clipRect(Rect.makeLTRB(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat()))
        }
    }

    fun disableScissor() { commands += { it.restore() } }

    fun linearGradient(x: Int, y: Int, width: Int, height: Int, colors: IntArray, vertical: Boolean = false) {
        val stops = colors.copyOf()
        commands += { canvas ->
            Shader.makeLinearGradient(x.toFloat(), y.toFloat(), (x + if (vertical) 0 else width).toFloat(), (y + if (vertical) height else 0).toFloat(), stops).use { shader ->
                Paint().setShader(shader).use { p ->
                    canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()), p)
                }
            }
        }
    }

    fun checkerboard(x: Int, y: Int, width: Int, height: Int) {
        for (row in 0 until height step 4) for (column in 0 until width step 4) {
            val color = if ((row / 4 + column / 4) % 2 == 0) 0xFFADB0BA.toInt() else 0xFF696D7A.toInt()
            roundedRect(x + column, y + row, minOf(4, width - column), minOf(4, height - row), 0, color)
        }
    }

    fun gradientRect(x: Int, y: Int, width: Int, height: Int, radius: Int, start: Int, end: Int) {
        commands += { canvas ->
            Shader.makeLinearGradient(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), intArrayOf(start, end)).use { shader ->
                Paint().setAntiAlias(true).setShader(shader).use { gradient ->
                    canvas.drawRRect(RRect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), radius.toFloat()), gradient)
                }
            }
        }
    }

    companion object {
        private val paint = Paint().setAntiAlias(true)
        private val outline = Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
        private val typefaces = mutableMapOf<NVGFont, Typeface>()
        private val fonts = mutableMapOf<Pair<NVGFont, Float>, Font>()

        private fun font(source: NVGFont, size: Float): Font = fonts.getOrPut(source to size) {
            val typeface = typefaces.getOrPut(source) {
                Data.makeFromBytes(source.bytes()).use { checkNotNull(FontMgr.getDefault().makeFromData(it)) }
            }
            Font(typeface, size).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS)
        }

        fun textWidth(text: String, size: Float = 10f): Float = font(ClickGui.selectedFont, size).measureTextWidth(text)

        fun truncate(text: String, width: Int, size: Float = 10f): String {
            if (textWidth(text, size) <= width) return text
            val suffix = "…"
            var end = text.length
            while (end > 0 && textWidth(text.substring(0, end) + suffix, size) > width) {
                end = text.offsetByCodePoints(end, -1)
            }
            return if (end == 0) "" else text.substring(0, end) + suffix
        }

        fun cleanup() {
            fonts.values.forEach { it.close() }
            fonts.clear()
            typefaces.values.forEach { it.close() }
            typefaces.clear()
            paint.close()
            outline.close()
        }
    }
}

/** Shared shape API for custom screens, backed exclusively by Skia. */
object SkijaShapes {
    data class ColoredRect(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)
    fun renderRectangle(g: SkijaDraw, x: Int, y: Int, width: Int, height: Int, color: Int) =
        g.roundedRect(x, y, width, height, 0, color)
    fun renderRoundedRectangle(g: SkijaDraw, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) =
        g.roundedRect(x, y, width, height, radius, color)
    fun renderRoundedOutline(g: SkijaDraw, x: Int, y: Int, width: Int, height: Int, radius: Int, thickness: Int, color: Int) =
        g.roundedRect(x, y, width, height, radius, color, thickness)
    fun renderRectangles(g: SkijaDraw, rectangles: Iterable<ColoredRect>) =
        rectangles.forEach { g.roundedRect(it.x, it.y, it.width, it.height, 0, it.color) }
}
