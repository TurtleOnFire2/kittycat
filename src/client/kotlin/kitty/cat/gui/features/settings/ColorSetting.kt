package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager
import kotlin.math.abs
import kotlin.math.roundToInt

class ColorSetting(
    override val name: String,
    red: Int = 255,
    green: Int = 255,
    blue: Int = 255,
    alpha: Int = 255
) : Setting {
    var red: Int = red.coerceIn(0, 255)
        private set
    var green: Int = green.coerceIn(0, 255)
        private set
    var blue: Int = blue.coerceIn(0, 255)
        private set
    var alpha: Int = alpha.coerceIn(0, 255)
        private set

    var hue: Float = 0f
        private set
    var saturation: Float = 0f
        private set
    var brightness: Float = 1f
        private set

    init {
        syncHsBFromRgb()
    }

    fun setRgba(red: Int, green: Int, blue: Int, alpha: Int = this.alpha) {
        val nextRed = red.coerceIn(0, 255)
        val nextGreen = green.coerceIn(0, 255)
        val nextBlue = blue.coerceIn(0, 255)
        val nextAlpha = alpha.coerceIn(0, 255)
        if (this.red == nextRed && this.green == nextGreen && this.blue == nextBlue && this.alpha == nextAlpha) return

        this.red = nextRed
        this.green = nextGreen
        this.blue = nextBlue
        this.alpha = nextAlpha
        syncHsBFromRgb()
        ConfigManager.markDirty()
    }

    fun setHue(hue: Float) {
        val normalized = normalizeHue(hue)
        if (this.hue == normalized) return
        this.hue = normalized
        syncRgbFromHsB()
        ConfigManager.markDirty()
    }

    fun setSaturation(saturation: Float) {
        val clamped = saturation.coerceIn(0f, 1f)
        if (this.saturation == clamped) return
        this.saturation = clamped
        syncRgbFromHsB()
        ConfigManager.markDirty()
    }

    fun setBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0f, 1f)
        if (this.brightness == clamped) return
        this.brightness = clamped
        syncRgbFromHsB()
        ConfigManager.markDirty()
    }

    fun alphaSliderPosition(): Float = alpha / 255f

    fun setAlphaFromSlider(position: Float) {
        val nextAlpha = (position.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        if (alpha == nextAlpha) return
        alpha = nextAlpha
        ConfigManager.markDirty()
    }

    fun rgbaText(): String = "$red, $green, $blue, $alpha"

    fun setFromRgbaText(text: String): Boolean {
        val parts = text.split(',', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.size !in 3..4) return false

        val values = parts.map { it.toIntOrNull() ?: return false }
        val r = values[0]
        val g = values[1]
        val b = values[2]
        val a = values.getOrElse(3) { alpha }

        setRgba(r, g, b, a)
        return true
    }

    private fun syncHsBFromRgb() {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        brightness = max
        saturation = if (max == 0f) 0f else delta / max

        hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }

        hue = normalizeHue(hue)
    }

    private fun syncRgbFromHsB() {
        val c = brightness * saturation
        val hPrime = hue / 60f
        val x = c * (1 - abs((hPrime % 2f) - 1f))
        val m = brightness - c

        val (rPrime, gPrime, bPrime) = when {
            hPrime < 1f -> floatArrayOf(c, x, 0f)
            hPrime < 2f -> floatArrayOf(x, c, 0f)
            hPrime < 3f -> floatArrayOf(0f, c, x)
            hPrime < 4f -> floatArrayOf(0f, x, c)
            hPrime < 5f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }

        red = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        green = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        blue = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun normalizeHue(hue: Float): Float {
        val mod = hue % 360f
        return if (mod < 0f) mod + 360f else mod
    }
}
