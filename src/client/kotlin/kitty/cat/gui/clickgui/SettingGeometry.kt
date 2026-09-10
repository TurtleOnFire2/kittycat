package kitty.cat.gui.clickgui

/** Shared geometry for settings rendering and pointer hit testing. */
data class UiRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
}

class SettingGeometry(val x: Int, val y: Int, val width: Int) {
    val inline = width >= 180
    private val controlOffset = if (inline) (width * 0.44f).toInt() else 0
    val label = UiRect(x, y, if (inline) controlOffset - 8 else width, 14)
    val control = UiRect(x + controlOffset, y + if (inline) 1 else 16, width - controlOffset, 16)
    val fieldHeight = if (inline) ROW_HEIGHT else 36
    val sliderHeight = fieldHeight + 12
    val numberSlider = UiRect(x + 4, y + fieldHeight + 1, (width - 8).coerceAtLeast(1), 4)
    val rangeSlider = UiRect(x + 4, y + fieldHeight, (width - 8).coerceAtLeast(1), 8)

    fun option(index: Int) = UiRect(control.x, y + fieldHeight + index * ROW_HEIGHT, control.width, ROW_HEIGHT)
    fun selectorHeight(optionCount: Int, open: Boolean) = fieldHeight + if (open) optionCount * ROW_HEIGHT else 0

    companion object {
        const val ROW_HEIGHT = 20
    }
}
