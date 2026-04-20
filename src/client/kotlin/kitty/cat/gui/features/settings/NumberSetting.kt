package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

class NumberSetting(
    override val name: String,
    min: Double,
    max: Double,
    defaultValue: Double,
    val unit: String = "",
    val step: Double = 0.0,
    override val description: String = ""
) : Setting {
    val min: Double = minOf(min, max)
    val max: Double = maxOf(min, max)

    var value: Double = clampAndSnap(defaultValue)
        private set

    fun setValue(newValue: Double) {
        val clamped = clampAndSnap(newValue)
        if (value == clamped) return
        value = clamped
        ConfigManager.markDirty()
    }

    fun sliderPosition(): Double {
        val range = max - min
        if (range == 0.0) return 0.0
        return ((value - min) / range).coerceIn(0.0, 1.0)
    }

    fun setFromSlider(sliderPosition: Double) {
        val normalized = sliderPosition.coerceIn(0.0, 1.0)
        setValue(min + (max - min) * normalized)
    }

    fun allowsDecimalInput(): Boolean {
        if (step > 0.0) {
            return !isWhole(step)
        }
        return !isWhole(min) || !isWhole(max)
    }

    fun textValue(includeUnit: Boolean = false): String {
        val raw = formatValue(value)
        return if (includeUnit && unit.isNotEmpty()) "$raw $unit" else raw
    }

    fun setFromText(input: String): Boolean {
        val raw = input.trim()
        if (raw.isEmpty()) return false

        val numericText = if (unit.isNotEmpty() && raw.endsWith(unit, ignoreCase = true)) {
            raw.dropLast(unit.length).trim()
        } else {
            raw
        }

        if (!allowsDecimalInput() && numericText.contains('.')) return false

        val parsed = numericText.toDoubleOrNull() ?: return false
        setValue(parsed)
        return true
    }

    private fun clampAndSnap(raw: Double): Double {
        val clamped = raw.coerceIn(min, max)
        if (step <= 0.0) return clamped

        val snapped = ((clamped - min) / step).roundToInt() * step + min
        val scale = decimalPlaces(step).coerceIn(0, 6)
        val rounded = BigDecimal.valueOf(snapped).setScale(scale, RoundingMode.HALF_UP).toDouble()
        return rounded.coerceIn(min, max)
    }

    private fun formatValue(raw: Double): String {
        val scale = if (step > 0.0) {
            decimalPlaces(step)
        } else {
            maxOf(decimalPlaces(min), decimalPlaces(max))
        }.coerceIn(0, 6)

        val rounded = BigDecimal.valueOf(raw)
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return rounded.toPlainString()
    }

    private fun isWhole(value: Double): Boolean = value % 1.0 == 0.0

    private fun decimalPlaces(value: Double): Int {
        return BigDecimal.valueOf(value).stripTrailingZeros().scale().coerceAtLeast(0)
    }
}
