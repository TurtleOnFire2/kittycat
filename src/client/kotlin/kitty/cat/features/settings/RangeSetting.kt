package kitty.cat.features.settings

import kitty.cat.config.ConfigManager
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

class RangeSetting(
    override val name: String,
    min: Double,
    max: Double,
    defaultLowerValue: Double,
    defaultUpperValue: Double,
    val unit: String = "",
    val step: Double = 0.0,
    override val description: String = "",
    internal val legacyLowerName: String? = null,
    internal val legacyUpperName: String? = null
) : Setting {
    val min: Double = minOf(min, max)
    val max: Double = maxOf(min, max)

    var lowerValue: Double
        private set
    var upperValue: Double
        private set

    val value: ClosedFloatingPointRange<Double>
        get() = lowerValue..upperValue

    init {
        val lower = clampAndSnap(defaultLowerValue)
        val upper = clampAndSnap(defaultUpperValue)
        lowerValue = minOf(lower, upper)
        upperValue = maxOf(lower, upper)
    }

    fun setValues(lower: Double, upper: Double) {
        val snappedLower = clampAndSnap(lower)
        val snappedUpper = clampAndSnap(upper)
        val nextLower = minOf(snappedLower, snappedUpper)
        val nextUpper = maxOf(snappedLower, snappedUpper)
        if (lowerValue == nextLower && upperValue == nextUpper) return
        lowerValue = nextLower
        upperValue = nextUpper
        ConfigManager.markDirty()
    }

    fun setLowerValue(newValue: Double) {
        val next = clampAndSnap(newValue).coerceAtMost(upperValue)
        if (lowerValue == next) return
        lowerValue = next
        ConfigManager.markDirty()
    }

    fun setUpperValue(newValue: Double) {
        val next = clampAndSnap(newValue).coerceAtLeast(lowerValue)
        if (upperValue == next) return
        upperValue = next
        ConfigManager.markDirty()
    }

    fun lowerSliderPosition(): Double = sliderPosition(lowerValue)

    fun upperSliderPosition(): Double = sliderPosition(upperValue)

    fun setLowerFromSlider(sliderPosition: Double) {
        setLowerValue(valueFromSlider(sliderPosition))
    }

    fun setUpperFromSlider(sliderPosition: Double) {
        setUpperValue(valueFromSlider(sliderPosition))
    }

    fun allowsDecimalInput(): Boolean {
        if (step > 0.0) return !isWhole(step)
        return !isWhole(min) || !isWhole(max)
    }

    fun textValue(includeUnit: Boolean = false): String {
        val raw = "${formatValue(lowerValue)} - ${formatValue(upperValue)}"
        return if (includeUnit && unit.isNotEmpty()) "$raw $unit" else raw
    }

    fun editableText(): String = "${formatValue(lowerValue)},${formatValue(upperValue)}"

    fun setFromText(input: String): Boolean {
        val raw = input.trim()
        if (raw.isEmpty()) return false

        val numericText = if (unit.isNotEmpty() && raw.endsWith(unit, ignoreCase = true)) {
            raw.dropLast(unit.length).trim()
        } else {
            raw
        }
        val match = RANGE_PATTERN.matchEntire(numericText) ?: return false
        val lowerText = match.groupValues[1]
        val upperText = match.groupValues[2]
        if (!allowsDecimalInput() && (lowerText.contains('.') || upperText.contains('.'))) return false

        val lower = lowerText.toDoubleOrNull() ?: return false
        val upper = upperText.toDoubleOrNull() ?: return false
        setValues(lower, upper)
        return true
    }

    private fun sliderPosition(raw: Double): Double {
        val range = max - min
        if (range == 0.0) return 0.0
        return ((raw - min) / range).coerceIn(0.0, 1.0)
    }

    private fun valueFromSlider(sliderPosition: Double): Double {
        val normalized = sliderPosition.coerceIn(0.0, 1.0)
        return min + (max - min) * normalized
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
            maxOf(decimalPlaces(min), decimalPlaces(max), decimalPlaces(raw))
        }.coerceIn(0, 6)

        return BigDecimal.valueOf(raw)
            .setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun isWhole(value: Double): Boolean = value % 1.0 == 0.0

    private fun decimalPlaces(value: Double): Int {
        return BigDecimal.valueOf(value).stripTrailingZeros().scale().coerceAtLeast(0)
    }

    private companion object {
        val RANGE_PATTERN = Regex(
            """^\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:,|\.\.)\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*$"""
        )
    }
}
