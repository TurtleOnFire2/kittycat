package kitty.cat.gui.clickgui

import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.ActionSetting
import kitty.cat.gui.features.settings.BooleanSetting
import kitty.cat.gui.features.settings.ColorSetting
import kitty.cat.gui.features.settings.KeybindSetting
import kitty.cat.gui.features.settings.NumberSetting
import kitty.cat.gui.features.settings.SelectorSetting
import kitty.cat.gui.features.settings.Setting
import kitty.cat.gui.features.settings.StringSetting
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import kitty.cat.utils.GuiUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import org.reflections.Reflections
import java.awt.Color

class ClickGui : Screen(Component.literal("Kittycat Gui")) {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX in x.toDouble()..(x + width).toDouble() &&
                mouseY in y.toDouble()..(y + height).toDouble()
        }
    }

    private data class SettingLayout(
        val setting: Setting,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX in x.toDouble()..(x + width).toDouble() &&
            mouseY in y.toDouble()..(y + height).toDouble()
        }
    }

    private data class ColorPickerLayout(
        val panelRect: Rect,
        val saturationBrightnessRect: Rect,
        val hueRect: Rect,
        val alphaRect: Rect
    )

    private data class FeatureLayout(
        val feature: Feature,
        val x: Int,
        val y: Int,
        val width: Int,
        val headerHeight: Int,
        val totalHeight: Int,
        val settingLayouts: List<SettingLayout>,
        val expanded: Boolean
    ) {
        fun isHeaderHovered(mouseX: Double, mouseY: Double): Boolean {
            return mouseX in x.toDouble()..(x + width).toDouble() &&
                mouseY in y.toDouble()..(y + headerHeight).toDouble()
        }
    }

    private data class CategoryLayout(
        val index: Int,
        val category: Categories.Category,
        val rect: Rect,
        val centerX: Float,
        val textY: Float,
        val fontSize: Float,
        val selected: Boolean
    )

    private enum class TextInputKind { NUMBER, COLOR_CHANNEL, STRING }

    private enum class ColorChannel {
        RED, GREEN, BLUE, ALPHA
    }

    private data class TextInputSession(
        val setting: Setting,
        val kind: TextInputKind,
        var buffer: String,
        val colorChannel: ColorChannel? = null
    )

    private companion object {
        const val PANEL_MIN_WIDTH = 620
        const val PANEL_MIN_HEIGHT = 360
        const val PANEL_WIDTH_RATIO = 0.88f
        const val PANEL_HEIGHT_RATIO = 0.78f
        const val PANEL_SAFE_MARGIN = 24
        const val DRAG_BAR_HEIGHT = 10
        const val PANEL_CONTENT_PADDING = 8
        const val SIDEBAR_WIDTH_MIN = 138
        const val SIDEBAR_WIDTH_MAX = 212
        const val SIDEBAR_CONTENT_GAP = 10
        var persistedSelectedCategory: Categories.Category? = null
        const val LEFT_MOUSE_BUTTON = 0
        const val RIGHT_MOUSE_BUTTON = 1

        const val CATEGORY_TAB_HEIGHT = 34
        const val CATEGORY_TAB_GAP = 6
        const val CATEGORY_TEXT_SIZE = 10f

        const val FEATURE_MIN_TWO_COLUMN_WIDTH = 480
        const val FEATURE_CARD_GAP = 6
        const val FEATURE_HEADER_HEIGHT = 20
        const val FEATURE_SETTINGS_TOP_PADDING = 6
        const val FEATURE_SETTINGS_BOTTOM_PADDING = 6
        const val FEATURE_SETTING_SIDE_PADDING = 8
        const val FEATURE_SETTING_ROW_HEIGHT = 14
        const val FEATURE_VIEW_BOTTOM_PADDING = 8
        const val FEATURE_SCROLL_STEP = 18

        const val NUMBER_VALUE_X = 72
        const val NUMBER_TEXT_WIDTH = 34
        const val NUMBER_UNIT_SLOT_WIDTH = 18
        const val NUMBER_TEXT_HEIGHT = 12
        const val NUMBER_SLIDER_HEIGHT = 6

        const val SELECTOR_VALUE_X = 72
        const val SELECTOR_VALUE_HEIGHT = 12

        const val KEYBIND_VALUE_X = 72
        const val KEYBIND_VALUE_HEIGHT = 12
        const val STRING_VALUE_X = 72
        const val STRING_VALUE_HEIGHT = 12

        const val FEATURE_SWITCH_WIDTH = 24
        const val FEATURE_SWITCH_HEIGHT = 12
        const val FEATURE_SWITCH_RIGHT_PADDING = 2
        const val FEATURE_SWITCH_KNOB_MARGIN = 2
        const val BOOLEAN_SETTING_SWITCH_Y_OFFSET = 0

        const val FEATURE_ACTION_BUTTON_WIDTH = 34
        const val FEATURE_ACTION_BUTTON_HEIGHT = 12
        const val TEXT_BASELINE_OFFSET = 0f

        const val COLOR_INPUT_GAP = 2
        const val COLOR_CHANNEL_VALUE_PADDING = 2
        const val SETTING_NAME_Y_OFFSET = 4
        const val VALUE_TEXT_Y_OFFSET = 2

        const val COLOR_PICKER_PANEL_WIDTH = 120
        const val COLOR_PICKER_SB_HEIGHT = 66
        const val COLOR_PICKER_SLIDER_HEIGHT = 8
        const val COLOR_PICKER_PADDING = 6
        const val COLOR_PICKER_OUTER_GAP = 6
        const val COLOR_PICKER_CONTENT_Y_OFFSET = 2
        const val COLOR_PICKER_SB_STEP = 3
        const val COLOR_PICKER_BAR_STEP = 2

        const val CATEGORY_SCROLL_COOLDOWN_TICKS = 2

        const val HOVER_TOOLTIP_DELAY_MS = 600L
        const val TOOLTIP_PADDING_H = 4
        const val TOOLTIP_PADDING_V = 4
        const val TOOLTIP_TEXT_SIZE = 9f

        const val DEFAULT_BASE_RED = 20
        const val DEFAULT_BASE_GREEN = 8
        const val DEFAULT_BASE_BLUE = 15
        const val DEFAULT_BASE_ALPHA = 168
        const val DEFAULT_ACCENT_RED = 204
        const val DEFAULT_ACCENT_GREEN = 84
        const val DEFAULT_ACCENT_BLUE = 116
        const val DEFAULT_ACCENT_ALPHA = 220
    }

    private var offsetX = 0
    private var offsetY = 0
    private var draggingPanel = false

    private var hoveredFeature: Feature? = null
    private var featureHoverStartMs: Long = 0L
    private var hoveredSetting: Setting? = null
    private var settingHoverStartMs: Long = 0L

    private var draggingNumberSetting: NumberSetting? = null
    private var draggingHueSetting: ColorSetting? = null
    private var draggingAlphaSetting: ColorSetting? = null
    private var draggingSaturationBrightnessSetting: ColorSetting? = null

    private var textInputSession: TextInputSession? = null
    private var openColorPickerFor: ColorSetting? = null
    private var keybindCaptureSetting: KeybindSetting? = null

    private var categoryList = mutableListOf<Categories.Category>()
    private var selectedIndex = 0
    private var cooldown = 0
    private var featureScrollOffset = 0
    private var maxFeatureScroll = 0

    val featureList: List<Feature> =
        Reflections("kitty.cat.features")
            .getSubTypesOf(Feature::class.java)
            .mapNotNull { clazz ->
                try {
                    clazz.getField("INSTANCE").get(null) as Feature
                } catch (_: Exception) {
                    null
                }
            }

    var activeFeatures: List<Feature> = emptyList()
    private val expandedFeatures = mutableSetOf<Feature>()

    override fun init() {
        categoryList = Categories.Category.entries.toMutableList()
        if (categoryList.isEmpty()) {
            activeFeatures = emptyList()
            return
        }
        persistedSelectedCategory
            ?.let { persisted -> categoryList.indexOf(persisted).takeIf { it >= 0 } }
            ?.let { persistedIndex -> selectedIndex = persistedIndex }
        selectedIndex = selectedIndex.coerceIn(0, categoryList.lastIndex)
        selectCategory(selectedIndex, playSound = false)
    }

    override fun tick() {
        cooldown--
        super.tick()
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val nowMs = System.currentTimeMillis()
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val sw = minecraft.window.guiScaledWidth
        val sh = minecraft.window.guiScaledHeight
        val scale = minecraft.window.guiScale.toFloat()

        renderMainPanelBody(guiGraphics, panelX, panelY)
        renderTopDragBar(guiGraphics, panelX, panelY)

        renderCategoryBar(guiGraphics, sw, sh, scale, panelX, panelY)
        val sidebar = sidebarRect(panelX, panelY)
        GuiUtils.renderRectangle(
            guiGraphics,
            sidebar.x + sidebar.width + SIDEBAR_CONTENT_GAP / 2,
            sidebar.y,
            1,
            sidebar.height,
            panelBorderColor(142)
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            0,
            1,
            panelBorderColor(120)
        )

        updateFeatureScrollBounds()
        val clipRect = featureClipRect(panelX, panelY)
        val featureLayouts = buildFeatureLayouts(panelX, panelY)
        guiGraphics.enableScissor(
            clipRect.x,
            clipRect.y,
            clipRect.x + clipRect.width,
            clipRect.y + clipRect.height
        )
        featureLayouts.forEach { layout ->
            if (layout.y + layout.totalHeight < clipRect.y || layout.y > clipRect.y + clipRect.height) {
                return@forEach
            }

            val borderColor = when {
                layout.feature.enabled -> accentBrightBorderColor()
                layout.expanded -> accentDarkColor()
                else -> accentDimColor()
            }

            GuiUtils.renderRoundedRectangle(
                guiGraphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                0,
                accentPanelColor(alpha = 116)
            )
            GuiUtils.renderRoundedOutline(
                guiGraphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                0,
                1,
                borderColor
            )

            renderFeatureHeader(guiGraphics, sw, sh, scale, layout)

            if (!layout.expanded) return@forEach

            GuiUtils.renderRectangle(
                guiGraphics,
                layout.x + 4,
                layout.y + FEATURE_HEADER_HEIGHT,
                layout.width - 8,
                1,
                accentDimColor()
            )

            if (layout.settingLayouts.isEmpty()) {
                drawText(
                    guiGraphics,
                    sw,
                    sh,
                    scale,
                    "No settings",
                    (layout.x + 8).toFloat(),
                    (layout.y + FEATURE_HEADER_HEIGHT + FEATURE_SETTINGS_TOP_PADDING).toFloat(),
                    10f,
                    textMutedColor()
                )
            } else {
                layout.settingLayouts.forEach { settingLayout ->
                    renderSettingRow(guiGraphics, sw, sh, scale, settingLayout)
                }
                layout.settingLayouts.forEach { settingLayout ->
                    val setting = settingLayout.setting as? SelectorSetting ?: return@forEach
                    if (!setting.dropdownOpen) return@forEach
                    renderSelectorDropdownOverlay(guiGraphics, sw, sh, scale, settingLayout, setting)
                }
            }
        }
        guiGraphics.disableScissor()

        // Update hover tracking for feature headers and settings
        val mouseXD = mouseX.toDouble()
        val mouseYD = mouseY.toDouble()
        var newHoveredFeature: Feature? = null
        var newHoveredSetting: Setting? = null
        val mouseInFeatureClip = clipRect.contains(mouseXD, mouseYD)

        featureLayouts.forEach { layout ->
            if (layout.isHeaderHovered(mouseXD, mouseYD) && mouseInFeatureClip) {
                newHoveredFeature = layout.feature
            }
            if (layout.expanded && mouseInFeatureClip) {
                layout.settingLayouts.forEach { sl ->
                    if (sl.contains(mouseXD, mouseYD)) {
                        newHoveredSetting = sl.setting
                    }
                }
            }
        }

        if (newHoveredFeature != hoveredFeature) {
            hoveredFeature = newHoveredFeature
            featureHoverStartMs = nowMs
        }
        if (newHoveredSetting != hoveredSetting) {
            hoveredSetting = newHoveredSetting
            settingHoverStartMs = nowMs
        }

        renderColorPickerOverlay(guiGraphics, sw, sh, scale)

        // Render hover tooltip (on top of everything)
        val currentSetting = hoveredSetting
        val currentFeature = hoveredFeature
        when {
            currentSetting != null && currentSetting.description.isNotEmpty() &&
                nowMs - settingHoverStartMs >= HOVER_TOOLTIP_DELAY_MS -> {
                renderTooltip(guiGraphics, sw, sh, scale, currentSetting.description, mouseX, mouseY)
            }
            currentFeature != null && currentFeature.description.isNotEmpty() &&
                nowMs - featureHoverStartMs >= HOVER_TOOLTIP_DELAY_MS -> {
                renderTooltip(guiGraphics, sw, sh, scale, currentFeature.description, mouseX, mouseY)
            }
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks)
    }

    private fun panelWidth(): Int {
        val proposed = (this.width * PANEL_WIDTH_RATIO).toInt()
        val maxAllowed = (this.width - PANEL_SAFE_MARGIN).coerceAtLeast(360)
        val minAllowed = PANEL_MIN_WIDTH.coerceAtMost(maxAllowed)
        return proposed.coerceIn(minAllowed, maxAllowed)
    }

    private fun panelHeight(): Int {
        val proposed = (this.height * PANEL_HEIGHT_RATIO).toInt()
        val maxAllowed = (this.height - PANEL_SAFE_MARGIN).coerceAtLeast(250)
        val minAllowed = PANEL_MIN_HEIGHT.coerceAtMost(maxAllowed)
        return proposed.coerceIn(minAllowed, maxAllowed)
    }

    private fun sidebarWidth(currentPanelWidth: Int): Int {
        return (currentPanelWidth * 0.18f).toInt().coerceIn(SIDEBAR_WIDTH_MIN, SIDEBAR_WIDTH_MAX)
    }

    private fun panelOriginX(): Int = this.width / 2 - panelWidth() / 2 + offsetX
    private fun panelOriginY(): Int = this.height / 2 - panelHeight() / 2 + offsetY

    private fun sidebarRect(panelX: Int, panelY: Int): Rect {
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        return Rect(
            x = panelX + PANEL_CONTENT_PADDING,
            y = panelY + PANEL_CONTENT_PADDING + DRAG_BAR_HEIGHT,
            width = sidebarWidth(panelWidth),
            height = panelHeight - PANEL_CONTENT_PADDING * 2 - DRAG_BAR_HEIGHT
        )
    }

    private fun featureAreaRect(panelX: Int, panelY: Int): Rect {
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val sidebar = sidebarRect(panelX, panelY)
        val x = sidebar.x + sidebar.width + SIDEBAR_CONTENT_GAP
        val y = panelY + PANEL_CONTENT_PADDING + DRAG_BAR_HEIGHT
        val right = panelX + panelWidth - PANEL_CONTENT_PADDING
        val bottom = panelY + panelHeight - PANEL_CONTENT_PADDING

        return Rect(
            x = x,
            y = y,
            width = (right - x).coerceAtLeast(96),
            height = (bottom - y - FEATURE_VIEW_BOTTOM_PADDING).coerceAtLeast(52)
        )
    }

    private fun featureColumnCount(contentWidth: Int): Int {
        return if (contentWidth >= FEATURE_MIN_TWO_COLUMN_WIDTH) 2 else 1
    }

    private fun featureCardWidth(contentWidth: Int, columns: Int): Int {
        if (columns <= 1) return contentWidth.coerceAtLeast(120)
        val totalGap = FEATURE_CARD_GAP * (columns - 1)
        return ((contentWidth - totalGap) / columns).coerceAtLeast(120)
    }

    private fun buildCategoryLayouts(panelX: Int, panelY: Int): List<CategoryLayout> {
        if (categoryList.isEmpty()) return emptyList()

        val sidebar = sidebarRect(panelX, panelY)
        val innerX = sidebar.x + 2
        val innerWidth = (sidebar.width - 4).coerceAtLeast(40)
        val categoryCount = categoryList.size
        val totalGap = CATEGORY_TAB_GAP * (categoryCount - 1).coerceAtLeast(0)
        val availableForTabs = (sidebar.height - 4 - totalGap).coerceAtLeast(0)
        val tabHeight = (availableForTabs / categoryCount).coerceIn(16, CATEGORY_TAB_HEIGHT)

        val layouts = mutableListOf<CategoryLayout>()
        var cursorY = sidebar.y + 2

        categoryList.indices.forEach { index ->
            val category = categoryList[index]
            val rect = Rect(
                x = innerX,
                y = cursorY,
                width = innerWidth,
                height = tabHeight
            )
            layouts += CategoryLayout(
                index = index,
                category = category,
                rect = rect,
                centerX = (rect.x + rect.width / 2f),
                textY = (rect.y + rect.height / 2f - 5f),
                fontSize = CATEGORY_TEXT_SIZE,
                selected = index == selectedIndex
            )
            cursorY += tabHeight + CATEGORY_TAB_GAP
        }

        return layouts
    }

    private fun featureClipRect(panelX: Int, panelY: Int): Rect {
        val featureArea = featureAreaRect(panelX, panelY)
        return Rect(
            x = featureArea.x,
            y = featureArea.y,
            width = featureArea.width,
            height = featureArea.height
        )
    }

    private fun updateFeatureScrollBounds() {
        val visibleHeight = featureAreaRect(panelOriginX(), panelOriginY()).height.coerceAtLeast(0)
        val contentHeight = computeFeatureContentHeight()
        maxFeatureScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
        featureScrollOffset = featureScrollOffset.coerceIn(-maxFeatureScroll, 0)
    }

    private fun computeFeatureContentHeight(): Int {
        val featureArea = featureAreaRect(panelOriginX(), panelOriginY())
        val columns = featureColumnCount(featureArea.width)
        val columnHeights = IntArray(columns) { 0 }

        activeFeatures.forEach { feature ->
            val expanded = feature in expandedFeatures
            val settingsContentHeight = if (!expanded) {
                0
            } else if (feature.settings.isEmpty()) {
                FEATURE_SETTING_ROW_HEIGHT
            } else {
                feature.settings.sumOf { settingHeight(it) }
            }

            val totalHeight = FEATURE_HEADER_HEIGHT + if (expanded) {
                FEATURE_SETTINGS_TOP_PADDING + settingsContentHeight + FEATURE_SETTINGS_BOTTOM_PADDING
            } else {
                0
            }

            val targetColumn = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0
            columnHeights[targetColumn] += totalHeight + FEATURE_CARD_GAP
        }

        val tallestColumn = columnHeights.maxOrNull() ?: 0
        return (tallestColumn - FEATURE_CARD_GAP).coerceAtLeast(0)
    }

    private fun scrollFeatureContent(deltaY: Double): Boolean {
        if (deltaY == 0.0 || maxFeatureScroll <= 0) return false
        val previous = featureScrollOffset
        val step = if (deltaY > 0.0) FEATURE_SCROLL_STEP else -FEATURE_SCROLL_STEP
        featureScrollOffset = (featureScrollOffset + step).coerceIn(-maxFeatureScroll, 0)
        return previous != featureScrollOffset
    }

    private fun numberTextRect(layout: SettingLayout): Rect {
        val fullWidth = NUMBER_TEXT_WIDTH + NUMBER_UNIT_SLOT_WIDTH
        return Rect(
            x = layout.x + layout.width - fullWidth - 2,
            y = layout.y + 1,
            width = fullWidth,
            height = NUMBER_TEXT_HEIGHT
        )
    }

    private fun numberSliderRect(layout: SettingLayout): Rect {
        val textRect = numberTextRect(layout)
        val sliderX = layout.x + NUMBER_VALUE_X
        val sliderWidth = (textRect.x - sliderX - 6).coerceAtLeast(12)
        return Rect(
            x = sliderX,
            y = layout.y + (FEATURE_SETTING_ROW_HEIGHT - NUMBER_SLIDER_HEIGHT) / 2,
            width = sliderWidth,
            height = NUMBER_SLIDER_HEIGHT
        )
    }

    private fun selectorBaseRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + SELECTOR_VALUE_X,
            y = layout.y + 1,
            width = layout.width - SELECTOR_VALUE_X - 2,
            height = SELECTOR_VALUE_HEIGHT
        )
    }

    private fun selectorOptionRect(layout: SettingLayout, optionIndex: Int): Rect {
        val base = selectorBaseRect(layout)
        return Rect(
            x = base.x,
            y = layout.y + FEATURE_SETTING_ROW_HEIGHT + optionIndex * FEATURE_SETTING_ROW_HEIGHT,
            width = base.width,
            height = FEATURE_SETTING_ROW_HEIGHT
        )
    }

    private fun keybindRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + KEYBIND_VALUE_X,
            y = layout.y + 1,
            width = layout.width - KEYBIND_VALUE_X - 2,
            height = KEYBIND_VALUE_HEIGHT
        )
    }

    private fun stringTextRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + STRING_VALUE_X,
            y = layout.y + 1,
            width = layout.width - STRING_VALUE_X - 2,
            height = STRING_VALUE_HEIGHT
        )
    }

    private fun colorSwatchRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + layout.width - 14,
            y = layout.y + 1,
            width = 12,
            height = 12
        )
    }

    private fun colorPickerLayout(): ColorPickerLayout {
        val panelXOrigin = panelOriginX()
        val panelYOrigin = panelOriginY()
        val featureArea = featureAreaRect(panelXOrigin, panelYOrigin)
        val pickerWidth = COLOR_PICKER_PANEL_WIDTH.coerceAtMost((featureArea.width - 10).coerceAtLeast(92))
        val panelX = (featureArea.x + featureArea.width - pickerWidth - COLOR_PICKER_OUTER_GAP).coerceAtLeast(featureArea.x + 2)
        val panelY = featureArea.y + COLOR_PICKER_OUTER_GAP

        val sbRect = Rect(
            x = panelX + COLOR_PICKER_PADDING,
            y = panelY + COLOR_PICKER_PADDING + COLOR_PICKER_CONTENT_Y_OFFSET,
            width = pickerWidth - COLOR_PICKER_PADDING * 2,
            height = COLOR_PICKER_SB_HEIGHT
        )

        val hueRect = Rect(
            x = sbRect.x,
            y = sbRect.y + sbRect.height + COLOR_PICKER_PADDING,
            width = sbRect.width,
            height = COLOR_PICKER_SLIDER_HEIGHT
        )

        val alphaRect = Rect(
            x = sbRect.x,
            y = hueRect.y + hueRect.height + COLOR_PICKER_PADDING,
            width = sbRect.width,
            height = COLOR_PICKER_SLIDER_HEIGHT
        )

        val channelRowY = alphaRect.y + alphaRect.height + COLOR_PICKER_PADDING

        val panelRect = Rect(
            x = panelX,
            y = panelY,
            width = pickerWidth,
            height = channelRowY + NUMBER_TEXT_HEIGHT + COLOR_PICKER_PADDING - panelY
        )

        return ColorPickerLayout(
            panelRect = panelRect,
            saturationBrightnessRect = sbRect,
            hueRect = hueRect,
            alphaRect = alphaRect
        )
    }

    private fun colorPickerChannelRect(layout: ColorPickerLayout, channel: ColorChannel): Rect {
        val totalWidth = layout.saturationBrightnessRect.width
        val slotWidth = ((totalWidth - COLOR_INPUT_GAP * 3) / 4).coerceAtLeast(8)
        val index = when (channel) {
            ColorChannel.RED -> 0
            ColorChannel.GREEN -> 1
            ColorChannel.BLUE -> 2
            ColorChannel.ALPHA -> 3
        }
        return Rect(
            x = layout.saturationBrightnessRect.x + index * (slotWidth + COLOR_INPUT_GAP),
            y = layout.alphaRect.y + layout.alphaRect.height + COLOR_PICKER_PADDING,
            width = slotWidth,
            height = NUMBER_TEXT_HEIGHT
        )
    }

    private fun booleanSwitchRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + layout.width - FEATURE_SWITCH_RIGHT_PADDING - FEATURE_SWITCH_WIDTH,
            y = layout.y + (FEATURE_SETTING_ROW_HEIGHT - FEATURE_SWITCH_HEIGHT) / 2 + BOOLEAN_SETTING_SWITCH_Y_OFFSET,
            width = FEATURE_SWITCH_WIDTH,
            height = FEATURE_SWITCH_HEIGHT
        )
    }

    private fun actionButtonRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + layout.width - FEATURE_SWITCH_RIGHT_PADDING - FEATURE_ACTION_BUTTON_WIDTH,
            y = layout.y + (FEATURE_SETTING_ROW_HEIGHT - FEATURE_ACTION_BUTTON_HEIGHT) / 2,
            width = FEATURE_ACTION_BUTTON_WIDTH,
            height = FEATURE_ACTION_BUTTON_HEIGHT
        )
    }

    private fun featureSwitchRect(layout: FeatureLayout): Rect {
        return Rect(
            x = layout.x + layout.width - FEATURE_SETTING_SIDE_PADDING - FEATURE_SWITCH_RIGHT_PADDING - FEATURE_SWITCH_WIDTH,
            y = layout.y + (layout.headerHeight - FEATURE_SWITCH_HEIGHT) / 2,
            width = FEATURE_SWITCH_WIDTH,
            height = FEATURE_SWITCH_HEIGHT
        )
    }

    private fun isTextInputActive(setting: Setting, colorChannel: ColorChannel? = null): Boolean {
        val session = textInputSession ?: return false
        return session.setting === setting && session.colorChannel == colorChannel
    }

    private fun activeTextBufferOrNull(setting: Setting, colorChannel: ColorChannel? = null): String? {
        val session = textInputSession ?: return null
        if (session.setting !== setting) return null
        if (session.colorChannel != colorChannel) return null
        return session.buffer
    }

    private fun beginTextInput(
        setting: Setting,
        kind: TextInputKind,
        initial: String,
        colorChannel: ColorChannel? = null
    ) {
        textInputSession = TextInputSession(
            setting = setting,
            kind = kind,
            buffer = initial,
            colorChannel = colorChannel
        )
    }

    private fun commitTextInput(): Boolean {
        val session = textInputSession ?: return false
        val success = when (session.kind) {
            TextInputKind.NUMBER -> (session.setting as? NumberSetting)?.setFromText(session.buffer.trim()) ?: false
            TextInputKind.COLOR_CHANNEL -> {
                val setting = session.setting as? ColorSetting ?: return false
                val channel = session.colorChannel ?: return false
                val parsed = session.buffer.trim().toIntOrNull() ?: return false
                applyColorChannel(setting, channel, parsed)
                true
            }
            TextInputKind.STRING -> (session.setting as? StringSetting)?.setFromText(session.buffer) ?: false
        }
        textInputSession = null
        return success
    }

    private fun cancelTextInput() {
        textInputSession = null
    }

    private fun appendToTextInput(character: Char): Boolean {
        val session = textInputSession ?: return false
        val allowed = when (session.kind) {
            TextInputKind.NUMBER -> {
                val setting = session.setting as? NumberSetting
                if (setting == null) {
                    character.isDigit() || character == '.'
                } else {
                    character.isDigit() ||
                        (character == '.' && setting.allowsDecimalInput()) ||
                        (character == '-' && setting.min < 0.0)
                }
            }
            TextInputKind.COLOR_CHANNEL -> character.isDigit()
            TextInputKind.STRING -> {
                val setting = session.setting as? StringSetting ?: return false
                session.buffer.length < setting.maxLength.coerceAtLeast(1)
            }
        }
        if (!allowed) return false

        if (session.kind == TextInputKind.NUMBER) {
            if (character == '-' && session.buffer.isNotEmpty()) return false
            if (character == '.' && session.buffer.contains('.')) return false
        }

        session.buffer += character
        return true
    }

    private fun removeLastTextInputChar(): Boolean {
        val session = textInputSession ?: return false
        if (session.buffer.isNotEmpty()) {
            session.buffer = session.buffer.dropLast(1)
        }
        return true
    }

    private fun updateNumberFromMouse(setting: NumberSetting, sliderRect: Rect, mouseX: Double) {
        val normalized = ((mouseX - sliderRect.x) / sliderRect.width.toDouble()).coerceIn(0.0, 1.0)
        setting.setFromSlider(normalized)
    }

    private fun updateHueFromMouse(setting: ColorSetting, hueRect: Rect, mouseX: Double) {
        val normalized = ((mouseX - hueRect.x) / hueRect.width.toDouble()).coerceIn(0.0, 1.0).toFloat()
        setting.setHue(normalized * 360f)
    }

    private fun updateAlphaFromMouse(setting: ColorSetting, alphaRect: Rect, mouseX: Double) {
        val normalized = ((mouseX - alphaRect.x) / alphaRect.width.toDouble()).coerceIn(0.0, 1.0).toFloat()
        setting.setAlphaFromSlider(normalized)
    }

    private fun updateSaturationBrightnessFromMouse(
        setting: ColorSetting,
        saturationBrightnessRect: Rect,
        mouseX: Double,
        mouseY: Double
    ) {
        val saturation = ((mouseX - saturationBrightnessRect.x) / saturationBrightnessRect.width.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
        val brightness = (1.0 - ((mouseY - saturationBrightnessRect.y) / saturationBrightnessRect.height.toDouble()))
            .coerceIn(0.0, 1.0)
            .toFloat()
        setting.setSaturation(saturation)
        setting.setBrightness(brightness)
    }

    private fun findSettingLayout(setting: Setting): SettingLayout? {
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        return buildFeatureLayouts(panelX, panelY)
            .asSequence()
            .flatMap { it.settingLayouts.asSequence() }
            .firstOrNull { it.setting === setting }
    }

    private fun hsvToArgb(hueDegrees: Float, saturation: Float, brightness: Float, alpha: Int): Int {
        val rgb = Color.HSBtoRGB(
            (hueDegrees / 360f).coerceIn(0f, 1f),
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f)
        )
        return (alpha.coerceIn(0, 255) shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun colorChannelValue(setting: ColorSetting, channel: ColorChannel): Int {
        return when (channel) {
            ColorChannel.RED -> setting.red
            ColorChannel.GREEN -> setting.green
            ColorChannel.BLUE -> setting.blue
            ColorChannel.ALPHA -> setting.alpha
        }
    }

    private fun applyColorChannel(setting: ColorSetting, channel: ColorChannel, value: Int) {
        val clamped = value.coerceIn(0, 255)
        when (channel) {
            ColorChannel.RED -> setting.setRgba(clamped, setting.green, setting.blue, setting.alpha)
            ColorChannel.GREEN -> setting.setRgba(setting.red, clamped, setting.blue, setting.alpha)
            ColorChannel.BLUE -> setting.setRgba(setting.red, setting.green, clamped, setting.alpha)
            ColorChannel.ALPHA -> setting.setRgba(setting.red, setting.green, setting.blue, clamped)
        }
    }

    private fun themedColor(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        baseWeight: Float = 1f,
        accentWeight: Float = 0f
    ): Int {
        val base = ClickGuiFeature.baseColor
        val accent = ClickGuiFeature.accentColor
        val redShift = ((base.red - DEFAULT_BASE_RED) * baseWeight + (accent.red - DEFAULT_ACCENT_RED) * accentWeight).toInt()
        val greenShift = ((base.green - DEFAULT_BASE_GREEN) * baseWeight + (accent.green - DEFAULT_ACCENT_GREEN) * accentWeight).toInt()
        val blueShift = ((base.blue - DEFAULT_BASE_BLUE) * baseWeight + (accent.blue - DEFAULT_ACCENT_BLUE) * accentWeight).toInt()
        val alphaShift = ((base.alpha - DEFAULT_BASE_ALPHA) * baseWeight + (accent.alpha - DEFAULT_ACCENT_ALPHA) * accentWeight).toInt()

        return Color(
            (red + redShift).coerceIn(0, 255),
            (green + greenShift).coerceIn(0, 255),
            (blue + blueShift).coerceIn(0, 255),
            (alpha + alphaShift).coerceIn(0, 255)
        ).rgb
    }

    private fun panelBorderColor(alpha: Int = 216): Int = themedColor(204, 84, 116, alpha, baseWeight = 0f, accentWeight = 1f)
    private fun panelBottomLayerColor(alpha: Int = 194): Int = themedColor(30, 16, 40, alpha, baseWeight = 0.82f, accentWeight = 0.22f)
    private fun sidebarPanelColor(alpha: Int = 152): Int = themedColor(39, 7, 13, alpha, baseWeight = 1f, accentWeight = 0.12f)
    private fun sidebarTabColor(alpha: Int = 134): Int = themedColor(54, 8, 18, alpha, baseWeight = 1f, accentWeight = 0.15f)
    private fun sidebarSelectedColor(alpha: Int = 162): Int = themedColor(66, 10, 23, alpha, baseWeight = 0.9f, accentWeight = 0.28f)
    private fun featureCardColor(alpha: Int = 106): Int = themedColor(17, 5, 9, alpha, baseWeight = 1f, accentWeight = 0.09f)
    private fun featureCardMutedColor(alpha: Int = 90): Int = themedColor(24, 8, 13, alpha, baseWeight = 1f, accentWeight = 0.11f)
    private fun fieldFillColor(alpha: Int = 120): Int = themedColor(25, 9, 14, alpha, baseWeight = 1f, accentWeight = 0.08f)

    private fun accentHighlightColor(): Int = themedColor(240, 210, 220, 255, baseWeight = 0.12f, accentWeight = 0.36f)
    private fun accentDarkColor(): Int = panelBorderColor(198)
    private fun accentDimColor(): Int = panelBorderColor(155)
    private fun accentLowColor(alpha: Int = 255): Int = themedColor(95, 37, 51, alpha, baseWeight = 0.35f, accentWeight = 0.42f)
    private fun accentPanelColor(alpha: Int = 255): Int = featureCardColor(alpha)
    private fun accentPanelDarkColor(alpha: Int = 255): Int = featureCardMutedColor(alpha)
    private fun accentBrightBorderColor(): Int = panelBorderColor(236)
    private fun topBarColor(): Int = themedColor(35, 11, 20, 168, baseWeight = 1f, accentWeight = 0.18f)
    private fun toggleOnColor(): Int = themedColor(207, 84, 117, 255, baseWeight = 0f, accentWeight = 1f)
    private fun toggleOffColor(): Int = themedColor(80, 28, 38, 255, baseWeight = 0.7f, accentWeight = 0.25f)
    private fun textMutedColor(): Int = themedColor(193, 152, 165, 255, baseWeight = 0.2f, accentWeight = 0.3f)
    private fun textPrimaryColor(): Int = themedColor(246, 227, 233, 255, baseWeight = 0.08f, accentWeight = 0.18f)

    private fun renderMainPanelBody(guiGraphics: GuiGraphicsExtractor, panelX: Int, panelY: Int) {
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()

        GuiUtils.renderRectangle(
            guiGraphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            panelBottomLayerColor()
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            0,
            1,
            panelBorderColor()
        )
    }

    private fun renderTopDragBar(guiGraphics: GuiGraphicsExtractor, panelX: Int, panelY: Int) {
        val panelWidth = panelWidth()
        GuiUtils.renderRectangle(
            guiGraphics,
            panelX,
            panelY,
            panelWidth,
            DRAG_BAR_HEIGHT,
            topBarColor()
        )
        GuiUtils.renderRectangle(
            guiGraphics,
            panelX,
            panelY + DRAG_BAR_HEIGHT,
            panelWidth,
            1,
            panelBorderColor(164)
        )
    }

    override fun mouseClicked(mbe: MouseButtonEvent, bl: Boolean): Boolean {
        val mouseX = mbe.x()
        val mouseY = mbe.y()
        val button = mbe.button()
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        updateFeatureScrollBounds()
        val clipRect = featureClipRect(panelX, panelY)
        val mouseInFeatureArea = clipRect.contains(mouseX, mouseY)
        val featureLayouts = buildFeatureLayouts(panelX, panelY)

        if (button == LEFT_MOUSE_BUTTON && textInputSession != null && !clickedInsideActiveTextField(mouseX, mouseY)) {
            commitTextInput()
        }

        openColorPickerFor?.let { colorSetting ->
            val pickerLayout = colorPickerLayout()
            if (button == LEFT_MOUSE_BUTTON) {
                when {
                    pickerLayout.saturationBrightnessRect.contains(mouseX, mouseY) -> {
                        updateSaturationBrightnessFromMouse(
                            setting = colorSetting,
                            saturationBrightnessRect = pickerLayout.saturationBrightnessRect,
                            mouseX = mouseX,
                            mouseY = mouseY
                        )
                        draggingSaturationBrightnessSetting = colorSetting
                        return true
                    }
                    pickerLayout.hueRect.contains(mouseX, mouseY) -> {
                        updateHueFromMouse(colorSetting, pickerLayout.hueRect, mouseX)
                        draggingHueSetting = colorSetting
                        return true
                    }
                    pickerLayout.alphaRect.contains(mouseX, mouseY) -> {
                        updateAlphaFromMouse(colorSetting, pickerLayout.alphaRect, mouseX)
                        draggingAlphaSetting = colorSetting
                        return true
                    }
                    listOf(
                        ColorChannel.RED,
                        ColorChannel.GREEN,
                        ColorChannel.BLUE,
                        ColorChannel.ALPHA
                    ).any { channel ->
                        val channelRect = colorPickerChannelRect(pickerLayout, channel)
                        if (!channelRect.contains(mouseX, mouseY)) return@any false
                        beginTextInput(
                            setting = colorSetting,
                            kind = TextInputKind.COLOR_CHANNEL,
                            initial = colorChannelValue(colorSetting, channel).toString(),
                            colorChannel = channel
                        )
                        true
                    } -> return true
                    pickerLayout.panelRect.contains(mouseX, mouseY) -> return true
                    else -> openColorPickerFor = null
                }
            } else if (!pickerLayout.panelRect.contains(mouseX, mouseY)) {
                openColorPickerFor = null
            }
        }

        if (button == LEFT_MOUSE_BUTTON) {
            val clickedCategory = buildCategoryLayouts(panelX, panelY)
                .firstOrNull { it.rect.contains(mouseX, mouseY) }
            if (clickedCategory != null) {
                selectCategory(clickedCategory.index, playSound = true)
                return true
            }
        }

        val titleBarRect = Rect(panelX, panelY, panelWidth(), DRAG_BAR_HEIGHT)
        if (button == LEFT_MOUSE_BUTTON && titleBarRect.contains(mouseX, mouseY)) {
            draggingPanel = true
            draggingNumberSetting = null
            draggingHueSetting = null
            draggingAlphaSetting = null
            return true
        }

        if (mouseInFeatureArea) {
            featureLayouts.forEach { layout ->
                if (!layout.isHeaderHovered(mouseX, mouseY)) return@forEach

                val headerSwitch = featureSwitchRect(layout)
                if (button == LEFT_MOUSE_BUTTON && headerSwitch.contains(mouseX, mouseY)) {
                    layout.feature.toggle()
                    playClickSound(1.0f)
                    return true
                }

                when (button) {
                    LEFT_MOUSE_BUTTON -> {
                        layout.feature.toggle()
                        playClickSound(1.0f)
                        return true
                    }
                    RIGHT_MOUSE_BUTTON -> {
                        if (layout.feature in expandedFeatures) {
                            expandedFeatures -= layout.feature
                        } else {
                            expandedFeatures += layout.feature
                        }
                        updateFeatureScrollBounds()
                        playClickSound(0.95f)
                        return true
                    }
                }
            }
        }

        var interactedWithSelector = false

        if (mouseInFeatureArea) {
            featureLayouts.filter { it.expanded }.forEach { layout ->
                layout.settingLayouts.forEach { settingLayout ->
                    when (val setting = settingLayout.setting) {
                        is SelectorSetting -> {
                            if (button != LEFT_MOUSE_BUTTON) return@forEach

                            val baseRect = selectorBaseRect(settingLayout)
                            if (baseRect.contains(mouseX, mouseY)) {
                                setting.dropdownOpen = !setting.dropdownOpen
                                interactedWithSelector = true
                                return true
                            }

                            if (setting.dropdownOpen) {
                                setting.options.forEachIndexed { optionIndex, option ->
                                    val optionRect = selectorOptionRect(settingLayout, optionIndex)
                                    if (!optionRect.contains(mouseX, mouseY)) return@forEachIndexed
                                    setting.toggle(option)
                                    if (!setting.allowMultiple) {
                                        setting.dropdownOpen = false
                                    }
                                    interactedWithSelector = true
                                    playClickSound(1.0f)
                                    return true
                                }
                            }
                            return@forEach
                        }

                        is BooleanSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button == LEFT_MOUSE_BUTTON) {
                                setting.toggle()
                                playClickSound(1.1f)
                                return true
                            }
                        }

                        is NumberSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button != LEFT_MOUSE_BUTTON) return@forEach

                            val textRect = numberTextRect(settingLayout)
                            val sliderRect = numberSliderRect(settingLayout)

                            when {
                                textRect.contains(mouseX, mouseY) -> {
                                    beginTextInput(setting, TextInputKind.NUMBER, setting.textValue(includeUnit = false))
                                    return true
                                }
                                sliderRect.contains(mouseX, mouseY) -> {
                                    updateNumberFromMouse(setting, sliderRect, mouseX)
                                    draggingNumberSetting = setting
                                    return true
                                }
                            }
                        }

                        is KeybindSetting -> {
                            val bindRect = keybindRect(settingLayout)
                            if (!bindRect.contains(mouseX, mouseY)) return@forEach

                            when (button) {
                                LEFT_MOUSE_BUTTON -> {
                                    cancelTextInput()
                                    keybindCaptureSetting = if (keybindCaptureSetting === setting) null else setting
                                    playClickSound(1.0f)
                                    return true
                                }

                                RIGHT_MOUSE_BUTTON -> {
                                    setting.clear()
                                    keybindCaptureSetting = null
                                    playClickSound(0.95f)
                                    return true
                                }
                            }
                        }

                        is StringSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button != LEFT_MOUSE_BUTTON) return@forEach

                            val textRect = stringTextRect(settingLayout)
                            if (textRect.contains(mouseX, mouseY)) {
                                beginTextInput(setting, TextInputKind.STRING, setting.value)
                                return true
                            }
                        }

                        is ColorSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button != LEFT_MOUSE_BUTTON) return@forEach

                            val swatchRect = colorSwatchRect(settingLayout)

                            when {
                                swatchRect.contains(mouseX, mouseY) -> {
                                    openColorPickerFor = if (openColorPickerFor === setting) null else setting
                                    return true
                                }
                            }
                        }

                        is ActionSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button == LEFT_MOUSE_BUTTON && actionButtonRect(settingLayout).contains(mouseX, mouseY)) {
                                setting.trigger()
                                playClickSound(1.0f)
                                return true
                            }
                        }
                    }
                }
            }
        }

        if (!interactedWithSelector && button == LEFT_MOUSE_BUTTON) {
            closeAllSelectorDropdowns()
        }

        return super.mouseClicked(mbe, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        draggingPanel = false
        draggingNumberSetting = null
        draggingHueSetting = null
        draggingAlphaSetting = null
        draggingSaturationBrightnessSetting = null
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        if (draggingPanel) {
            offsetX += d.toInt()
            offsetY += e.toInt()
            return true
        }

        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()

        draggingNumberSetting?.let { setting ->
            findSettingLayout(setting)?.let { layout ->
                updateNumberFromMouse(setting, numberSliderRect(layout), mouseX)
                return true
            }
        }

        draggingSaturationBrightnessSetting?.let { setting ->
            val picker = colorPickerLayout()
            updateSaturationBrightnessFromMouse(
                setting = setting,
                saturationBrightnessRect = picker.saturationBrightnessRect,
                mouseX = mouseX,
                mouseY = mouseY
            )
            return true
        }

        draggingHueSetting?.let { setting ->
            if (openColorPickerFor === setting) {
                val picker = colorPickerLayout()
                updateHueFromMouse(setting, picker.hueRect, mouseX)
                return true
            }
        }

        draggingAlphaSetting?.let { setting ->
            if (openColorPickerFor === setting) {
                val picker = colorPickerLayout()
                updateAlphaFromMouse(setting, picker.alphaRect, mouseX)
                return true
            }
        }

        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double, g: Double): Boolean {
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        updateFeatureScrollBounds()

        val featureArea = featureClipRect(panelX, panelY)
        if (featureArea.contains(d, e) && scrollFeatureContent(g)) {
            return true
        }

        if (categoryList.isEmpty()) return false
        val categoryArea = sidebarRect(panelX, panelY)
        if (!categoryArea.contains(d, e)) return false
        if (cooldown > 0) return false

        cooldown = CATEGORY_SCROLL_COOLDOWN_TICKS

        when {
            g > 0.0 -> selectCategory(selectedIndex - 1, playSound = true)
            g < 0.0 -> selectCategory(selectedIndex + 1, playSound = true)
            else -> return false
        }
        return true
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        keybindCaptureSetting?.let { setting ->
            when (keyEvent.key()) {
                GLFW.GLFW_KEY_ESCAPE,
                GLFW.GLFW_KEY_BACKSPACE,
                GLFW.GLFW_KEY_DELETE -> setting.clear()

                GLFW.GLFW_KEY_UNKNOWN -> return true
                else -> setting.setKeyCode(keyEvent.key())
            }
            keybindCaptureSetting = null
            playClickSound(1.0f)
            return true
        }

        val activeInput = textInputSession
        if (activeInput != null) {
            when (keyEvent.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    removeLastTextInputChar()
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    commitTextInput()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    cancelTextInput()
                    return true
                }
            }
        }

        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeAllSelectorDropdowns()
            cancelTextInput()
            openColorPickerFor = null
        }

        return super.keyPressed(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (textInputSession == null) return super.charTyped(characterEvent)
        val codepoint = characterEvent.codepoint()
        if (codepoint !in 32..126) return true
        appendToTextInput(codepoint.toChar())
        return true
    }

    override fun keyReleased(keyEvent: KeyEvent): Boolean = super.keyReleased(keyEvent)

    override fun onClose() {
        draggingPanel = false
        draggingNumberSetting = null
        draggingHueSetting = null
        draggingAlphaSetting = null
        draggingSaturationBrightnessSetting = null
        cancelTextInput()
        keybindCaptureSetting = null
        closeAllSelectorDropdowns()
        openColorPickerFor = null
        if (ClickGuiFeature.enabled) {
            ClickGuiFeature.setEnabled(false)
        }
        super.onClose()
    }

    private fun renderSettingRow(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
        val setting = settingLayout.setting
        drawText(
            guiGraphics = guiGraphics,
            sw = sw,
            sh = sh,
            scale = scale,
            text = setting.name,
            x = (settingLayout.x + 1).toFloat(),
            y = (settingLayout.y + SETTING_NAME_Y_OFFSET).toFloat(),
            size = 9f,
            color = textPrimaryColor()
        )

        when (setting) {
            is BooleanSetting -> renderBooleanSetting(guiGraphics, settingLayout, setting)
            is NumberSetting -> renderNumberSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is SelectorSetting -> renderSelectorSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is KeybindSetting -> renderKeybindSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is StringSetting -> renderStringSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is ColorSetting -> renderColorSetting(guiGraphics, settingLayout, setting)
            is ActionSetting -> renderActionSetting(guiGraphics, sw, sh, scale, settingLayout)
        }
    }

    private fun renderBooleanSetting(guiGraphics: GuiGraphicsExtractor, settingLayout: SettingLayout, setting: BooleanSetting) {
        val switchRect = booleanSwitchRect(settingLayout)
        val trackColor = if (setting.value) toggleOnColor() else toggleOffColor()
        val knobSize = switchRect.height - FEATURE_SWITCH_KNOB_MARGIN * 2
        val knobX = if (setting.value) {
            switchRect.x + switchRect.width - FEATURE_SWITCH_KNOB_MARGIN - knobSize
        } else {
            switchRect.x + FEATURE_SWITCH_KNOB_MARGIN
        }
        val knobY = switchRect.y + FEATURE_SWITCH_KNOB_MARGIN

        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            switchRect.x,
            switchRect.y,
            switchRect.width,
            switchRect.height,
            switchRect.height / 2,
            trackColor
        )
        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            knobX,
            knobY,
            knobSize,
            knobSize,
            knobSize / 2,
            textPrimaryColor()
        )
    }

    private fun renderNumberSetting(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: NumberSetting) {
        val textRect = numberTextRect(settingLayout)
        val sliderRect = numberSliderRect(settingLayout)

        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            sliderRect.x,
            sliderRect.y,
            sliderRect.width,
            sliderRect.height,
            sliderRect.height / 2,
            fieldFillColor(154)
        )

        val fillWidth = (sliderRect.width * setting.sliderPosition()).toInt().coerceIn(0, sliderRect.width)
        if (fillWidth > 0) {
            GuiUtils.renderRoundedRectangle(
                guiGraphics,
                sliderRect.x,
                sliderRect.y,
                fillWidth,
                sliderRect.height,
                sliderRect.height / 2,
                toggleOnColor()
            )
        }

        GuiUtils.renderRoundedOutline(
            guiGraphics,
            textRect.x,
            textRect.y,
            textRect.width,
            textRect.height,
            3,
            1,
            if (isTextInputActive(setting)) accentBrightBorderColor() else accentDimColor()
        )

        val valueText = activeTextBufferOrNull(setting) ?: setting.textValue(includeUnit = true)
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            valueText,
            (textRect.x + 2).toFloat(),
            (textRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textMutedColor()
        )
    }

    private fun renderSelectorSetting(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: SelectorSetting) {
        val baseRect = selectorBaseRect(settingLayout)
        GuiUtils.renderRoundedRectangle(guiGraphics, baseRect.x, baseRect.y, baseRect.width, baseRect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(guiGraphics, baseRect.x, baseRect.y, baseRect.width, baseRect.height, 2, 1, accentDimColor())

        val selectedText = if (setting.allowMultiple) {
            setting.selected.joinToString(", ").ifBlank { "None" }
        } else {
            setting.selectedSingle
        }
        val arrow = if (setting.dropdownOpen) "v" else ">"
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            selectedText,
            (baseRect.x + 2).toFloat(),
            (baseRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textMutedColor()
        )
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            arrow,
            (baseRect.x + baseRect.width - 7).toFloat(),
            (baseRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textPrimaryColor()
        )
    }

    private fun renderKeybindSetting(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: KeybindSetting
    ) {
        val rect = keybindRect(settingLayout)
        val captureActive = keybindCaptureSetting === setting
        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            2,
            1,
            if (captureActive) accentBrightBorderColor() else accentDimColor()
        )

        val value = if (captureActive) "Press key..." else setting.displayValue()
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            value,
            (rect.x + 2).toFloat(),
            (rect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textMutedColor()
        )
    }

    private fun renderStringSetting(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: StringSetting
    ) {
        val rect = stringTextRect(settingLayout)
        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            2,
            1,
            if (isTextInputActive(setting)) accentBrightBorderColor() else accentDimColor()
        )

        val raw = activeTextBufferOrNull(setting) ?: setting.value
        val value = if (raw.length > 46) "${raw.take(46)}..." else raw
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            value,
            (rect.x + 2).toFloat(),
            (rect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textMutedColor()
        )
    }

    private fun renderSelectorDropdownOverlay(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: SelectorSetting
    ) {
        val firstOptionRect = selectorOptionRect(settingLayout, 0)
        val overlayHeight = setting.options.size * FEATURE_SETTING_ROW_HEIGHT
        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            firstOptionRect.x,
            firstOptionRect.y,
            firstOptionRect.width,
            overlayHeight,
            2,
            fieldFillColor(214)
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            firstOptionRect.x,
            firstOptionRect.y,
            firstOptionRect.width,
            overlayHeight,
            2,
            1,
            accentDimColor()
        )

        setting.options.forEachIndexed { optionIndex, option ->
            val optionRect = selectorOptionRect(settingLayout, optionIndex)
            val selected = setting.isSelected(option)
            val backgroundColor = if (selected) sidebarSelectedColor(214) else fieldFillColor(188)
            GuiUtils.renderRoundedRectangle(guiGraphics, optionRect.x, optionRect.y, optionRect.width, optionRect.height, 2, backgroundColor)
            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                option,
                (optionRect.x + 2).toFloat(),
                (optionRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
                9f,
                if (selected) textPrimaryColor() else textMutedColor()
            )
        }
    }

    private fun renderColorPickerOverlay(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float) {
        val setting = openColorPickerFor ?: return
        if (findSettingLayout(setting) == null) {
            openColorPickerFor = null
            return
        }

        val picker = colorPickerLayout()
        val panel = picker.panelRect
        val sbRect = picker.saturationBrightnessRect
        val hueRect = picker.hueRect
        val alphaRect = picker.alphaRect

        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            0,
            fieldFillColor(216)
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            0,
            1,
            accentBrightBorderColor()
        )

        renderSaturationBrightnessBox(guiGraphics, sbRect, setting)
        GuiUtils.renderRoundedOutline(guiGraphics, sbRect.x, sbRect.y, sbRect.width, sbRect.height, 2, 1, accentDimColor())

        val sbMarkerX = sbRect.x + (setting.saturation * (sbRect.width - 1)).toInt().coerceIn(0, sbRect.width - 1)
        val sbMarkerY = sbRect.y + ((1f - setting.brightness) * (sbRect.height - 1)).toInt().coerceIn(0, sbRect.height - 1)
        GuiUtils.renderRoundedOutline(guiGraphics, sbMarkerX - 2, sbMarkerY - 2, 5, 5, 2, 1, textPrimaryColor())

        renderHueBar(guiGraphics, hueRect, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(guiGraphics, hueRect.x, hueRect.y, hueRect.width, hueRect.height, 2, 1, accentDimColor())
        val hueKnobX = hueRect.x + ((setting.hue / 360f) * (hueRect.width - 1)).toInt().coerceIn(0, hueRect.width - 1)
        GuiUtils.renderRectangle(guiGraphics, hueKnobX, hueRect.y - 1, 1, hueRect.height + 2, textPrimaryColor())

        renderAlphaBar(guiGraphics, alphaRect, setting, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(guiGraphics, alphaRect.x, alphaRect.y, alphaRect.width, alphaRect.height, 2, 1, accentDimColor())
        val alphaKnobX = alphaRect.x + (setting.alphaSliderPosition() * (alphaRect.width - 1)).toInt().coerceIn(0, alphaRect.width - 1)
        GuiUtils.renderRectangle(guiGraphics, alphaKnobX, alphaRect.y - 1, 1, alphaRect.height + 2, textPrimaryColor())

        listOf(
            ColorChannel.RED,
            ColorChannel.GREEN,
            ColorChannel.BLUE,
            ColorChannel.ALPHA
        ).forEach { channel ->
            val rect = colorPickerChannelRect(picker, channel)
            GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
            GuiUtils.renderRoundedOutline(
                guiGraphics,
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                2,
                1,
                if (isTextInputActive(setting, channel)) accentBrightBorderColor() else accentDimColor()
            )

            val text = activeTextBufferOrNull(setting, channel) ?: colorChannelValue(setting, channel).toString()
            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                text,
                (rect.x + COLOR_CHANNEL_VALUE_PADDING).toFloat(),
                (rect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
                9f,
                textMutedColor()
            )
        }
    }

    private fun renderHueBar(guiGraphics: GuiGraphicsExtractor, rect: Rect, step: Int) {
        val safeStep = step.coerceAtLeast(1)
        var offset = 0
        while (offset < rect.width) {
            val segmentWidth = minOf(safeStep, rect.width - offset)
            val hue = (offset.toFloat() / rect.width.toFloat()) * 360f
            GuiUtils.renderRectangle(
                guiGraphics,
                rect.x + offset,
                rect.y,
                segmentWidth,
                rect.height,
                hsvToArgb(hue, 1f, 1f, 255)
            )
            offset += safeStep
        }
    }

    private fun renderAlphaBar(guiGraphics: GuiGraphicsExtractor, rect: Rect, setting: ColorSetting, step: Int) {
        val safeStep = step.coerceAtLeast(1)
        val rgb = (setting.red shl 16) or (setting.green shl 8) or setting.blue
        var offset = 0
        while (offset < rect.width) {
            val segmentWidth = minOf(safeStep, rect.width - offset)
            val alpha = (offset.toFloat() / rect.width.toFloat() * 255f).toInt().coerceIn(0, 255)
            GuiUtils.renderRectangle(
                guiGraphics,
                rect.x + offset,
                rect.y,
                segmentWidth,
                rect.height,
                (alpha shl 24) or rgb
            )
            offset += safeStep
        }
    }

    private fun renderSaturationBrightnessBox(guiGraphics: GuiGraphicsExtractor, rect: Rect, setting: ColorSetting) {
        val safeStep = COLOR_PICKER_SB_STEP.coerceAtLeast(1)
        var localY = 0
        while (localY < rect.height) {
            val blockHeight = minOf(safeStep, rect.height - localY)
            val brightness = (1.0 - localY.toDouble() / (rect.height - 1).coerceAtLeast(1).toDouble()).toFloat()
            var localX = 0
            while (localX < rect.width) {
                val blockWidth = minOf(safeStep, rect.width - localX)
                val saturation = (localX.toDouble() / (rect.width - 1).coerceAtLeast(1).toDouble()).toFloat()
                GuiUtils.renderRectangle(
                    guiGraphics,
                    rect.x + localX,
                    rect.y + localY,
                    blockWidth,
                    blockHeight,
                    hsvToArgb(setting.hue, saturation, brightness, 255)
                )
                localX += safeStep
            }
            localY += safeStep
        }
    }

    private fun renderColorSetting(guiGraphics: GuiGraphicsExtractor, settingLayout: SettingLayout, setting: ColorSetting) {
        val swatchRect = colorSwatchRect(settingLayout)

        val argb = (setting.alpha shl 24) or (setting.red shl 16) or (setting.green shl 8) or setting.blue
        GuiUtils.renderRoundedRectangle(guiGraphics, swatchRect.x, swatchRect.y, swatchRect.width, swatchRect.height, 2, argb)
        GuiUtils.renderRoundedOutline(guiGraphics, swatchRect.x, swatchRect.y, swatchRect.width, swatchRect.height, 2, 1, accentDimColor())
    }

    private fun renderActionSetting(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
        val buttonRect = actionButtonRect(settingLayout)
        GuiUtils.renderRoundedRectangle(guiGraphics, buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 3, fieldFillColor())
        GuiUtils.renderRoundedOutline(guiGraphics, buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 3, 1, accentDimColor())
        drawCenteredText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Run",
            (buttonRect.x + buttonRect.width / 2).toFloat(),
            (buttonRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            textPrimaryColor()
        )
    }

    private fun buildFeatureLayouts(panelX: Int, panelY: Int): List<FeatureLayout> {
        val layouts = mutableListOf<FeatureLayout>()
        val featureArea = featureAreaRect(panelX, panelY)
        val columns = featureColumnCount(featureArea.width)
        val cardWidth = featureCardWidth(featureArea.width, columns)
        val columnHeights = IntArray(columns) { featureArea.y + featureScrollOffset }

        activeFeatures.forEach { feature ->
            val targetColumn = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0
            val cardX = featureArea.x + targetColumn * (cardWidth + FEATURE_CARD_GAP)
            val cardY = columnHeights[targetColumn]
            val expanded = feature in expandedFeatures

            val settingLayouts = if (expanded) buildSettingLayouts(feature, cardX, cardY, cardWidth) else emptyList()
            val settingsContentHeight = if (!expanded) {
                0
            } else if (settingLayouts.isEmpty()) {
                FEATURE_SETTING_ROW_HEIGHT
            } else {
                settingLayouts.sumOf { it.height }
            }

            val totalHeight = FEATURE_HEADER_HEIGHT + if (expanded) {
                FEATURE_SETTINGS_TOP_PADDING + settingsContentHeight + FEATURE_SETTINGS_BOTTOM_PADDING
            } else {
                0
            }

            layouts += FeatureLayout(
                feature = feature,
                x = cardX,
                y = cardY,
                width = cardWidth,
                headerHeight = FEATURE_HEADER_HEIGHT,
                totalHeight = totalHeight,
                settingLayouts = settingLayouts,
                expanded = expanded
            )

            columnHeights[targetColumn] = cardY + totalHeight + FEATURE_CARD_GAP
        }

        return layouts
    }

    private fun buildSettingLayouts(feature: Feature, cardX: Int, cardY: Int, cardWidth: Int): List<SettingLayout> {
        val settingLayouts = mutableListOf<SettingLayout>()
        var cursorY = cardY + FEATURE_HEADER_HEIGHT + FEATURE_SETTINGS_TOP_PADDING

        val settingX = cardX + FEATURE_SETTING_SIDE_PADDING
        val settingWidth = cardWidth - FEATURE_SETTING_SIDE_PADDING * 2

        feature.settings.forEach { setting ->
            val height = settingHeight(setting)
            settingLayouts += SettingLayout(
                setting = setting,
                x = settingX,
                y = cursorY,
                width = settingWidth,
                height = height
            )
            cursorY += height
        }

        return settingLayouts
    }

    private fun settingHeight(setting: Setting): Int {
        return when (setting) {
            is ColorSetting -> FEATURE_SETTING_ROW_HEIGHT
            is SelectorSetting -> FEATURE_SETTING_ROW_HEIGHT
            else -> FEATURE_SETTING_ROW_HEIGHT
        }
    }

    private fun renderTooltip(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, text: String, mouseX: Int, mouseY: Int) {
        val font = ClickGuiFeature.selectedFont
        val measuredWidth = NVGRenderer.textWidth(text, TOOLTIP_TEXT_SIZE * scale, font) / scale
        val boxWidth = measuredWidth.toInt() + TOOLTIP_PADDING_H * 2
        val boxHeight = TOOLTIP_TEXT_SIZE.toInt() + TOOLTIP_PADDING_V * 2

        val tx = mouseX + 10
        var ty = mouseY - boxHeight - 6

        val panelBottom = panelOriginY() + panelHeight()
        if (ty < panelOriginY()) ty = mouseY + 14
        if (ty + boxHeight > panelBottom) ty = panelBottom - boxHeight - 4

        GuiUtils.renderRoundedRectangle(guiGraphics, tx, ty, boxWidth, boxHeight, 3, fieldFillColor(230))
        GuiUtils.renderRoundedOutline(guiGraphics, tx, ty, boxWidth, boxHeight, 3, 1, accentDimColor())
        drawText(
            guiGraphics, sw, sh, scale, text,
            (tx + TOOLTIP_PADDING_H).toFloat(),
            (ty + TOOLTIP_PADDING_V).toFloat(),
            TOOLTIP_TEXT_SIZE,
            textPrimaryColor()
        )
    }

    private fun renderFeatureHeader(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, scale: Float, layout: FeatureLayout) {
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            layout.feature.name,
            (layout.x + 8).toFloat(),
            (layout.y + 5).toFloat(),
            12f,
            if (layout.feature.enabled) textPrimaryColor() else accentHighlightColor()
        )

        val switchRect = featureSwitchRect(layout)
        val trackColor = if (layout.feature.enabled) toggleOnColor() else toggleOffColor()
        val knobSize = switchRect.height - FEATURE_SWITCH_KNOB_MARGIN * 2
        val knobX = if (layout.feature.enabled) {
            switchRect.x + switchRect.width - FEATURE_SWITCH_KNOB_MARGIN - knobSize
        } else {
            switchRect.x + FEATURE_SWITCH_KNOB_MARGIN
        }
        val knobY = switchRect.y + FEATURE_SWITCH_KNOB_MARGIN

        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            switchRect.x,
            switchRect.y,
            switchRect.width,
            switchRect.height,
            switchRect.height / 2,
            trackColor
        )
        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            knobX,
            knobY,
            knobSize,
            knobSize,
            knobSize / 2,
            textPrimaryColor()
        )
    }

    private fun renderCategoryBar(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        panelX: Int,
        panelY: Int
    ) {
        if (categoryList.isEmpty()) return

        val sidebar = sidebarRect(panelX, panelY)
        GuiUtils.renderRectangle(
            guiGraphics,
            sidebar.x,
            sidebar.y,
            sidebar.width,
            sidebar.height,
            sidebarPanelColor()
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            sidebar.x,
            sidebar.y,
            sidebar.width,
            sidebar.height,
            0,
            1,
            panelBorderColor(164)
        )

        val categoryLayouts = buildCategoryLayouts(panelX, panelY)

        categoryLayouts.forEach { layout ->
            GuiUtils.renderRectangle(
                guiGraphics,
                layout.rect.x,
                layout.rect.y,
                layout.rect.width,
                layout.rect.height,
                if (layout.selected) sidebarSelectedColor() else sidebarTabColor()
            )
            GuiUtils.renderRoundedOutline(
                guiGraphics,
                layout.rect.x,
                layout.rect.y,
                layout.rect.width,
                layout.rect.height,
                0,
                1,
                if (layout.selected) panelBorderColor(226) else panelBorderColor(170)
            )

            val label = layout.category.name.lowercase().replaceFirstChar { it.uppercase() }
            drawCenteredText(
                guiGraphics,
                sw,
                sh,
                scale,
                label,
                layout.centerX,
                layout.textY,
                if (layout.selected) 10.5f else layout.fontSize,
                if (layout.selected) textPrimaryColor() else textMutedColor()
            )
        }
    }

    private fun drawText(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        val font = ClickGuiFeature.selectedFont
        NVGPIPRenderer.draw(guiGraphics, 0, 0, sw, sh) {
            NVGRenderer.text(
                text = text,
                x = x * scale,
                y = (y + TEXT_BASELINE_OFFSET) * scale,
                size = size * scale,
                color = color,
                font = font
            )
        }
    }

    private fun drawCenteredText(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        text: String,
        cx: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        val font = ClickGuiFeature.selectedFont
        NVGPIPRenderer.draw(guiGraphics, 0, 0, sw, sh) {
            NVGRenderer.textCentered(
                text = text,
                cx = cx * scale,
                y = (y + TEXT_BASELINE_OFFSET) * scale,
                size = size * scale,
                color = color,
                font = font
            )
        }
    }

    private fun selectCategory(index: Int, playSound: Boolean) {
        if (categoryList.isEmpty()) return
        val clamped = index.coerceIn(0, categoryList.lastIndex)
        val changed = clamped != selectedIndex

        selectedIndex = clamped
        persistedSelectedCategory = categoryList[selectedIndex]
        activeFeatures = featureList.filter { it.category == categoryList[selectedIndex] }
        expandedFeatures.retainAll(activeFeatures.toSet())
        featureScrollOffset = 0
        updateFeatureScrollBounds()
        closeAllSelectorDropdowns()
        cancelTextInput()
        openColorPickerFor = null
        draggingNumberSetting = null
        draggingHueSetting = null
        draggingAlphaSetting = null
        draggingSaturationBrightnessSetting = null

        if (playSound && changed) {
            playClickSound(0.95f)
        }
    }

    private fun closeAllSelectorDropdowns() {
        featureList.forEach { feature ->
            feature.selectorSettings.forEach { it.dropdownOpen = false }
        }
    }

    private fun clickedInsideActiveTextField(mouseX: Double, mouseY: Double): Boolean {
        val session = textInputSession ?: return false
        return when (session.kind) {
            TextInputKind.NUMBER -> {
                val layout = findSettingLayout(session.setting) ?: return false
                numberTextRect(layout).contains(mouseX, mouseY)
            }
            TextInputKind.COLOR_CHANNEL -> {
                val channel = session.colorChannel ?: return false
                val setting = session.setting as? ColorSetting ?: return false
                if (openColorPickerFor !== setting) return false
                val picker = colorPickerLayout()
                colorPickerChannelRect(picker, channel).contains(mouseX, mouseY)
            }
            TextInputKind.STRING -> {
                val layout = findSettingLayout(session.setting) ?: return false
                stringTextRect(layout).contains(mouseX, mouseY)
            }
        }
    }

    private fun playClickSound(pitch: Float) {
        minecraft.player?.playSound(SoundEvents.LEVER_CLICK, 0.1f, pitch)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}
