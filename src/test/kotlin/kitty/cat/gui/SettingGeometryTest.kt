package kitty.cat.gui

import kitty.cat.gui.clickgui.SettingGeometry
import kitty.cat.gui.clickgui.UiRect
import kotlin.test.*

class SettingGeometryTest {
    @Test fun controlsStayInsideNarrowAndWideRows() {
        for (width in listOf(96, 160, 220, 296, 480, 900)) {
            val geometry = SettingGeometry(20, 80, width)
            for (control in listOf(geometry.control, geometry.numberSlider, geometry.rangeSlider)) {
                assertTrue(control.x >= geometry.x)
                assertTrue(control.x + control.width <= geometry.x + width)
                assertTrue(control.x >= geometry.label.x + geometry.label.width || control.y >= geometry.label.y + geometry.label.height)
                assertTrue(control.y + control.height < geometry.y + geometry.sliderHeight)
            }
            assertTrue(geometry.control.y + geometry.control.height < geometry.rangeSlider.y)
        }
    }

    @Test fun expandedSelectorsReserveEveryOptionWithoutOverlappingNextRow() {
        for (count in listOf(1, 3, 12, 50)) {
            val geometry = SettingGeometry(10, -100, 220)
            val bottom = geometry.y + geometry.selectorHeight(count, true)
            for (index in 0 until count) {
                val option = geometry.option(index)
                assertTrue(option.y >= geometry.control.y + geometry.control.height)
                assertTrue(option.y + option.height <= bottom)
                if (index > 0) assertEquals(geometry.option(index - 1).y + option.height, option.y)
            }
            assertEquals(bottom, geometry.option(count - 1).y + SettingGeometry.ROW_HEIGHT)
            assertEquals(geometry.fieldHeight, geometry.selectorHeight(count, false))
        }
    }

    @Test fun sharedEdgesBelongToOnlyOneControl() {
        val first = UiRect(0, 0, 100, 24)
        val second = UiRect(0, 24, 100, 24)
        assertFalse(first.contains(50.0, 24.0))
        assertTrue(second.contains(50.0, 24.0))
    }

    @Test fun compactRowsUseHalfTheSpaceWithSafeNarrowFallback() {
        val compact = SettingGeometry(0, 0, 280)
        val narrow = SettingGeometry(0, 0, 160)
        assertEquals(20, compact.fieldHeight)
        assertEquals(32, compact.sliderHeight)
        assertTrue(narrow.control.y >= narrow.label.y + narrow.label.height)
        assertTrue(narrow.fieldHeight > compact.fieldHeight)
    }
}
