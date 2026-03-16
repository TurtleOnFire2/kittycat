package kitty.cat.render.nanovg

import net.minecraft.client.Minecraft
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import java.nio.ByteBuffer
import kotlin.math.round

object NVGRenderer {

    private val nvgColor = NVGColor.malloc()
    private val fontBounds = FloatArray(4)
    private val fontMap = HashMap<NVGFont, FontEntry>()
    private var drawing = false
    var vg = -1L
        private set

    val defaultFont: NVGFont by lazy {
        NVGFont(
            "OnestRegular",
            NVGRenderer::class.java.getResourceAsStream("/assets/kittycat/font/onest_regular.ttf")
                ?: error("Could not load font: onest_regular.ttf")
        )
    }

    fun ensureInit() {
        if (vg != -1L) return
        vg = nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES)
        require(vg != -1L) { "Failed to initialize NanoVG" }
    }

    fun devicePixelRatio(): Float {
        return try {
            val window = Minecraft.getInstance().window
            val fbw = window.width
            val ww = window.screenWidth
            if (ww == 0) 1f else fbw.toFloat() / ww.toFloat()
        } catch (_: Throwable) { 1f }
    }

    fun beginFrame(width: Float, height: Float) {
        ensureInit()
        if (drawing) return
        val dpr = devicePixelRatio()
        nvgBeginFrame(vg, width / dpr, height / dpr, dpr)
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        drawing = true
    }

    fun endFrame() {
        if (!drawing) return
        nvgEndFrame(vg)
        drawing = false
    }

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: NVGFont = defaultFont) {
        prepareTextState(size, font)
        val snappedX = snapToPixel(x)
        val snappedY = snapToPixel(y)
        setColor(color)
        nvgFillColor(vg, nvgColor)
        nvgText(vg, snappedX, snappedY, text)
    }

    fun textCentered(text: String, cx: Float, y: Float, size: Float, color: Int, font: NVGFont = defaultFont) {
        val w = textWidth(text, size, font)
        text(text, cx - w / 2f, y, size, color, font)
    }

    fun textWidth(text: String, size: Float, font: NVGFont = defaultFont): Float {
        ensureInit()
        prepareTextState(size, font)
        return nvgTextBounds(vg, 0f, 0f, text, fontBounds)
    }

    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        if (width <= 0f || height <= 0f) return
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, width, height, radius.coerceAtLeast(0f))
        setColor(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun roundedRectStroke(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        strokeWidth: Float,
        color: Int
    ) {
        if (width <= 0f || height <= 0f || strokeWidth <= 0f) return
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, width, height, radius.coerceAtLeast(0f))
        nvgStrokeWidth(vg, strokeWidth)
        setColor(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    private fun setColor(argb: Int) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        nvgRGBAf(r, g, b, a, nvgColor)
    }

    private fun prepareTextState(size: Float, font: NVGFont): Float {
        val snappedSize = (round(size * 10f) / 10f).coerceAtLeast(1f)
        nvgFontSize(vg, snappedSize)
        nvgFontFaceId(vg, getFontID(font))
        nvgFontBlur(vg, 0f)
        nvgTextLetterSpacing(vg, 0f)
        return snappedSize
    }

    private fun snapToPixel(value: Float): Float {
        return round(value)
    }

    private fun getFontID(font: NVGFont): Int {
        return fontMap.getOrPut(font) {
            val buffer = font.buffer()
            FontEntry(nvgCreateFontMem(vg, font.name, buffer, false), buffer)
        }.id
    }

    private data class FontEntry(val id: Int, val buffer: ByteBuffer)
}
