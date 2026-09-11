package kitty.cat.gui.clickgui

import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.clickgui.UiRect as Rect
import kitty.cat.features.Feature
import kitty.cat.features.debug.ExampleFeature
import kitty.cat.features.dungeons.AutoLB
import kitty.cat.features.dungeons.LeverTriggerbot
import kitty.cat.features.dungeons.Relics
import kitty.cat.features.dungeons.Storm
import kitty.cat.features.dungeons.Terminals
import kitty.cat.features.kuudra.AutoGFS
import kitty.cat.features.kuudra.BackboneAlert
import kitty.cat.features.kuudra.Fixes
import kitty.cat.features.kuudra.HideTags
import kitty.cat.features.kuudra.KuudraDev
import kitty.cat.features.kuudra.PearlWaypoints
import kitty.cat.features.kuudra.RendDamage
import kitty.cat.features.kuudra.RendMacro
import kitty.cat.features.kuudra.SafeSpots
import kitty.cat.features.kuudra.Stun
import kitty.cat.features.kuudra.Supplies
import kitty.cat.features.kuudra.Build
import kitty.cat.features.kuudra.KuudraDisplay
import kitty.cat.features.kuudra.SupplyCheats
import kitty.cat.features.kuudra.TinyMobs
import kitty.cat.features.misc.BestiaryHud
import kitty.cat.features.misc.ChatMacros
import kitty.cat.features.misc.FarmHelper
import kitty.cat.features.misc.Pests
import kitty.cat.features.misc.Safari
import kitty.cat.features.settings.ActionSetting
import kitty.cat.features.settings.BooleanSetting
import kitty.cat.features.settings.ColorSetting
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.features.settings.NumberSetting
import kitty.cat.features.settings.OrderSetting
import kitty.cat.features.settings.RangeSetting
import kitty.cat.features.settings.SelectorSetting
import kitty.cat.features.settings.RegistrySetting
import kitty.cat.features.settings.Setting
import kitty.cat.features.settings.StringSetting
import kitty.cat.features.visual.ArrowTracers
import kitty.cat.features.visual.BestiaryESP
import kitty.cat.features.visual.CatEars
import kitty.cat.features.visual.CustomESP
import kitty.cat.render.skija.SkijaDraw
import kitty.cat.render.skija.SkijaRenderer
import kitty.cat.render.skija.SkijaShapes as GuiUtils
import net.minecraft.client.gui.GuiGraphicsExtractor as MinecraftGuiExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import java.awt.Color

class ClickGui : Screen(Component.literal("Kittycat Gui")) {
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

    private enum class TextInputKind { NUMBER, RANGE, COLOR_CHANNEL, STRING, REGISTRY }

    private enum class RangeHandle { LOWER, UPPER }

    private data class RangeDrag(val setting: RangeSetting, val handle: RangeHandle)

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
        const val DRAG_BAR_HEIGHT = 52
        const val PANEL_CONTENT_PADDING = 12
        const val SIDEBAR_WIDTH_MIN = 132
        const val SIDEBAR_WIDTH_MAX = 164
        const val SIDEBAR_CONTENT_GAP = 10
        var persistedSelectedCategory: Categories.Category? = null
        const val LEFT_MOUSE_BUTTON = 0
        const val RIGHT_MOUSE_BUTTON = 1

        const val CATEGORY_TAB_HEIGHT = 34
        const val CATEGORY_TAB_GAP = 6
        const val CATEGORY_TEXT_SIZE = 10f

        const val FEATURE_MIN_TWO_COLUMN_WIDTH = 600
        const val FEATURE_CARD_GAP = 10
        const val FEATURE_HEADER_HEIGHT = 54
        const val FEATURE_SETTINGS_TOP_PADDING = 6
        const val FEATURE_SETTINGS_BOTTOM_PADDING = 6
        const val FEATURE_SETTING_SIDE_PADDING = 8
        const val FEATURE_SETTING_ROW_HEIGHT = SettingGeometry.ROW_HEIGHT
        const val FEATURE_VIEW_BOTTOM_PADDING = 8
        const val FEATURE_SCROLL_STEP = 18

        const val NUMBER_TEXT_HEIGHT = 18
        const val NUMBER_SLIDER_HEIGHT = 6
        const val RANGE_HANDLE_SIZE = 8



        const val FEATURE_SWITCH_WIDTH = 24
        const val FEATURE_SWITCH_HEIGHT = 12
        const val FEATURE_SWITCH_RIGHT_PADDING = 8
        const val FEATURE_SWITCH_KNOB_MARGIN = 2
        const val BOOLEAN_SETTING_SWITCH_Y_OFFSET = 0

        const val FEATURE_ACTION_BUTTON_WIDTH = 34
        const val FEATURE_ACTION_BUTTON_HEIGHT = 16
        const val TEXT_BASELINE_OFFSET = 0f

        const val COLOR_INPUT_GAP = 2
        const val COLOR_CHANNEL_VALUE_PADDING = 2
        const val SETTING_NAME_Y_OFFSET = 4
        const val VALUE_TEXT_Y_OFFSET = 4

        const val COLOR_PICKER_PANEL_WIDTH = 176
        const val COLOR_PICKER_SB_HEIGHT = 66
        const val COLOR_PICKER_SLIDER_HEIGHT = 8
        const val COLOR_PICKER_PADDING = 6
        const val COLOR_PICKER_OUTER_GAP = 6
        const val COLOR_PICKER_CONTENT_Y_OFFSET = 2
        const val COLOR_PICKER_SB_STEP = 3
        const val COLOR_PICKER_BAR_STEP = 2

        const val CATEGORY_SCROLL_COOLDOWN_TICKS = 2

        const val HOVER_TOOLTIP_DELAY_MS = 600L
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
    private var searchQuery = ""
    private var searchFocused = false
    private fun focusedFeature(): Feature? = expandedFeatures.firstOrNull()
    private fun displayedFeatures(): List<Feature> {
        focusedFeature()?.let { return listOf(it) }
        val query = searchQuery.trim()
        return if (query.isEmpty()) activeFeatures else featureList.filter {
            it.name.contains(query, true) || it.description.contains(query, true)
        }
    }

    private fun closeInspector() {
        commitTextInput()
        expandedFeatures.clear()
        closeAllSelectorDropdowns()
        openColorPickerFor = null
        keybindCaptureSetting = null
        featureScrollOffset = 0
    }

    private fun inspectFeature(feature: Feature) {
        closeInspector()
        expandedFeatures += feature
        searchFocused = false
        updateFeatureScrollBounds()
    }

    private var pointerX = 0
    private var pointerY = 0
    private var draggingPanel = false

    private var hoveredFeature: Feature? = null
    private var featureHoverStartMs: Long = 0L
    private var hoveredSetting: Setting? = null
    private var settingHoverStartMs: Long = 0L

    private var draggingNumberSetting: NumberSetting? = null
    private var draggingRange: RangeDrag? = null
    private var draggingHueSetting: ColorSetting? = null
    private var draggingAlphaSetting: ColorSetting? = null
    private var draggingSaturationBrightnessSetting: ColorSetting? = null
    private var draggingOrderSetting: OrderSetting? = null
    private var draggingOrderIndex = -1

    private var textInputSession: TextInputSession? = null
    private var registryHighlight = -1
    private var openColorPickerFor: ColorSetting? = null
    private var keybindCaptureSetting: KeybindSetting? = null

    private var categoryList = mutableListOf<Categories.Category>()
    private var selectedIndex = 0
    private var cooldown = 0
    private var featureScrollOffset = 0
    private var maxFeatureScroll = 0

    val featureList: List<Feature> = listOf(
        Safari, ArrowTracers, CatEars, CustomESP, BestiaryESP, ClickGuiFeature,
        Storm, AutoLB, Relics, LeverTriggerbot, Terminals,
        KuudraDisplay, RendMacro, Stun, BackboneAlert, KuudraDev, TinyMobs, HideTags, Fixes, PearlWaypoints, Supplies, AutoGFS, RendDamage, SupplyCheats, SafeSpots,
        BestiaryHud, Pests, ChatMacros, FarmHelper, Build,
        ExampleFeature
    )

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

    override fun extractRenderState(context: MinecraftGuiExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val graphics = SkijaDraw()
        GuiUtils.renderRectangle(graphics, 0, 0, width, height, 0x80060912.toInt())
        val nowMs = System.currentTimeMillis()
        pointerX = mouseX
        pointerY = mouseY
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val sw = minecraft.window.guiScaledWidth
        val sh = minecraft.window.guiScaledHeight
        val scale = minecraft.window.guiScale.toFloat()

        renderMainPanelBody(graphics, panelX, panelY)
        renderTopDragBar(graphics, panelX, panelY)

        renderCategoryBar(graphics, sw, sh, scale, panelX, panelY)
        val sidebar = sidebarRect(panelX, panelY)
        GuiUtils.renderRectangle(
            graphics,
            sidebar.x + sidebar.width + SIDEBAR_CONTENT_GAP / 2,
            sidebar.y,
            1,
            sidebar.height,
            panelBorderColor(28)
        )
        GuiUtils.renderRoundedOutline(
            graphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            10,
            1,
            panelBorderColor(55)
        )

        updateFeatureScrollBounds()
        val clipRect = featureClipRect(panelX, panelY)
        val featureLayouts = buildFeatureLayouts(panelX, panelY)
        graphics.enableScissor(
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
                layout.isHeaderHovered(mouseX.toDouble(), mouseY.toDouble()) -> panelBorderColor(145)
                layout.feature.enabled -> panelBorderColor(95)
                layout.expanded -> panelBorderColor(48)
                else -> panelBorderColor(55)
            }

            GuiUtils.renderRoundedRectangle(
                graphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                6,
                if (layout.isHeaderHovered(mouseX.toDouble(), mouseY.toDouble())) surfaceColor(0.12f) else surfaceColor(0.055f)
            )
            GuiUtils.renderRoundedOutline(
                graphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                6,
                1,
                borderColor
            )

            renderFeatureHeader(graphics, sw, sh, scale, layout)

            if (!layout.expanded) return@forEach

            GuiUtils.renderRectangle(
                graphics,
                layout.x + 4,
                layout.y + FEATURE_HEADER_HEIGHT,
                layout.width - 8,
                1,
                accentDimColor()
            )

            if (layout.settingLayouts.isEmpty()) {
                drawText(
                    graphics,
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
                    renderSettingRow(graphics, sw, sh, scale, settingLayout)
                }
            }
        }
        if (featureLayouts.isEmpty()) {
            graphics.centeredText("No modules found", clipRect.x + clipRect.width / 2, clipRect.y + 46, textPrimaryColor(), 15f)
            graphics.centeredText("Try a different search.", clipRect.x + clipRect.width / 2, clipRect.y + 70, textMutedColor(), 10f)
        }
        // Expanded selectors reserve their own layout space and scroll with the card.
        featureLayouts.forEach { layout ->
            if (!layout.expanded) return@forEach
            layout.settingLayouts.forEach { settingLayout ->
                val setting = settingLayout.setting as? SelectorSetting ?: return@forEach
                if (setting.dropdownOpen) {
                    renderSelectorDropdownOverlay(graphics, sw, sh, scale, settingLayout, setting)
                }
            }
        }
        graphics.disableScissor()

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

        renderColorPickerOverlay(graphics, sw, sh, scale)

        // Render hover tooltip (on top of everything)
        val currentSetting = hoveredSetting
        val currentFeature = hoveredFeature
        when {
            openColorPickerFor == null && currentSetting != null &&
                nowMs - settingHoverStartMs >= HOVER_TOOLTIP_DELAY_MS -> {
                renderTooltip(graphics, sw, sh, scale, currentSetting.name + if (currentSetting.description.isBlank()) "" else ": " + currentSetting.description, mouseX, mouseY)
            }
            openColorPickerFor == null && currentFeature != null && currentFeature.description.isNotEmpty() &&
                nowMs - featureHoverStartMs >= HOVER_TOOLTIP_DELAY_MS -> {
                renderTooltip(graphics, sw, sh, scale, currentFeature.description, mouseX, mouseY)
            }
        }
        SkijaRenderer.submit(this, width, height, graphics)
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
        val y = panelY + PANEL_CONTENT_PADDING + DRAG_BAR_HEIGHT + 50
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
        return if (focusedFeature() == null && contentWidth >= FEATURE_MIN_TWO_COLUMN_WIDTH) 2 else 1
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
        val availableForTabs = (sidebar.height - 100 - totalGap).coerceAtLeast(0)
        val tabHeight = (availableForTabs / categoryCount).coerceIn(16, CATEGORY_TAB_HEIGHT)

        val layouts = mutableListOf<CategoryLayout>()
        var cursorY = sidebar.y + 4

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

        displayedFeatures().forEach { feature ->
            val expanded = feature in expandedFeatures
            val settingsContentHeight = if (!expanded) {
                0
            } else if (feature.settings.isEmpty()) {
                FEATURE_SETTING_ROW_HEIGHT
            } else {
                val settingWidth = featureCardWidth(featureArea.width, columns) - FEATURE_SETTING_SIDE_PADDING * 2
                feature.settings.sumOf { settingHeight(it, settingWidth) }
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

    // Compact inline controls fall back to stacked rows on narrow cards.
    // Drawing and hit testing use the same responsive geometry.
    private fun geometry(layout: SettingLayout) = SettingGeometry(layout.x, layout.y, layout.width)
    private fun controlRect(layout: SettingLayout) = geometry(layout).control

    private fun numberTextRect(layout: SettingLayout): Rect = controlRect(layout)
    private fun rangeTextRect(layout: SettingLayout): Rect = controlRect(layout)

    private fun numberSliderRect(layout: SettingLayout) =
        geometry(layout).numberSlider

    private fun rangeSliderRect(layout: SettingLayout) =
        geometry(layout).rangeSlider

    private fun selectorBaseRect(layout: SettingLayout): Rect = controlRect(layout)

    private fun selectorOptionRect(layout: SettingLayout, optionIndex: Int) =
        geometry(layout).option(optionIndex)

    private fun orderOptionRect(layout: SettingLayout, index: Int) = Rect(
        layout.x,
        layout.y + FEATURE_SETTING_ROW_HEIGHT + index * FEATURE_SETTING_ROW_HEIGHT,
        layout.width,
        FEATURE_SETTING_ROW_HEIGHT
    )

    private fun keybindRect(layout: SettingLayout): Rect = controlRect(layout)
    private fun stringTextRect(layout: SettingLayout): Rect = controlRect(layout)

    private fun colorSwatchRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + layout.width - 88,
            y = layout.y + 2,
            width = 88,
            height = 16
        )
    }

    private fun colorPickerLayout(): ColorPickerLayout {
        val panelXOrigin = panelOriginX()
        val panelYOrigin = panelOriginY()
        val featureArea = featureAreaRect(panelXOrigin, panelYOrigin)
        val pickerWidth = COLOR_PICKER_PANEL_WIDTH.coerceAtMost((featureArea.width - 10).coerceAtLeast(92))
        val anchor = openColorPickerFor?.let { findSettingLayout(it) }?.let { colorSwatchRect(it) }
        val panelX = ((anchor?.let { it.x + it.width } ?: (featureArea.x + featureArea.width)) - pickerWidth)
            .coerceIn(4, (width - pickerWidth - 4).coerceAtLeast(4))
        val pickerHeight = COLOR_PICKER_PADDING * 6 + 24 + COLOR_PICKER_SB_HEIGHT + COLOR_PICKER_SLIDER_HEIGHT * 2 + 12 + NUMBER_TEXT_HEIGHT
        val below = anchor?.let { it.y + it.height + 5 } ?: featureArea.y
        val panelY = (if (below + pickerHeight <= height - 4) below else (anchor?.y ?: below) - pickerHeight - 5)
            .coerceIn(4, (height - pickerHeight - 4).coerceAtLeast(4))

        val sbRect = Rect(
            x = panelX + COLOR_PICKER_PADDING,
            y = panelY + COLOR_PICKER_PADDING + 24,
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

        val channelRowY = alphaRect.y + alphaRect.height + COLOR_PICKER_PADDING + 12

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
            y = layout.alphaRect.y + layout.alphaRect.height + COLOR_PICKER_PADDING + 12,
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
        registryHighlight = -1
    }

    private fun commitTextInput(): Boolean {
        val session = textInputSession ?: return false
        val success = when (session.kind) {
            TextInputKind.NUMBER -> (session.setting as? NumberSetting)?.setFromText(session.buffer.trim()) ?: false
            TextInputKind.RANGE -> (session.setting as? RangeSetting)?.setFromText(session.buffer.trim()) ?: false
            TextInputKind.COLOR_CHANNEL -> {
                val setting = session.setting as? ColorSetting ?: return false
                val channel = session.colorChannel ?: return false
                val parsed = session.buffer.trim().toIntOrNull() ?: return false
                applyColorChannel(setting, channel, parsed)
                true
            }
            TextInputKind.STRING -> (session.setting as? StringSetting)?.setFromText(session.buffer) ?: false
            TextInputKind.REGISTRY -> (session.setting as? RegistrySetting)?.setValue(session.buffer) ?: false
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
            TextInputKind.RANGE -> {
                val setting = session.setting as? RangeSetting
                if (setting == null) {
                    character.isDigit() || character == '.' || character == ',' || character == '-'
                } else {
                    character.isDigit() ||
                        (character == '.' && setting.allowsDecimalInput()) ||
                        (character == '-' && setting.min < 0.0) ||
                        (character == ',' && !session.buffer.contains(','))
                }
            }
            TextInputKind.COLOR_CHANNEL -> character.isDigit()
            TextInputKind.REGISTRY -> session.buffer.length < (session.setting as RegistrySetting).maxLength
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
        registryHighlight = -1
        return true
    }

    private fun removeLastTextInputChar(): Boolean {
        registryHighlight = -1
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

    private fun rangeHandleForMouse(setting: RangeSetting, sliderRect: Rect, mouseX: Double): RangeHandle {
        val normalized = rangeSliderPositionForMouse(sliderRect, mouseX)
        val lowerDistance = kotlin.math.abs(normalized - setting.lowerSliderPosition())
        val upperDistance = kotlin.math.abs(normalized - setting.upperSliderPosition())
        return when {
            lowerDistance < upperDistance -> RangeHandle.LOWER
            upperDistance < lowerDistance -> RangeHandle.UPPER
            normalized <= setting.lowerSliderPosition() -> RangeHandle.LOWER
            else -> RangeHandle.UPPER
        }
    }

    private fun updateRangeFromMouse(setting: RangeSetting, handle: RangeHandle, sliderRect: Rect, mouseX: Double) {
        val normalized = rangeSliderPositionForMouse(sliderRect, mouseX)
        when (handle) {
            RangeHandle.LOWER -> setting.setLowerFromSlider(normalized)
            RangeHandle.UPPER -> setting.setUpperFromSlider(normalized)
        }
    }

    private fun rangeSliderPositionForMouse(sliderRect: Rect, mouseX: Double): Double {
        val trackStart = sliderRect.x + RANGE_HANDLE_SIZE / 2.0
        val trackWidth = (sliderRect.width - RANGE_HANDLE_SIZE).coerceAtLeast(1)
        return ((mouseX - trackStart) / trackWidth).coerceIn(0.0, 1.0)
    }

    private fun rangeHandleX(sliderRect: Rect, position: Double): Int {
        val trackStart = sliderRect.x + RANGE_HANDLE_SIZE / 2
        val trackWidth = (sliderRect.width - RANGE_HANDLE_SIZE).coerceAtLeast(1)
        return trackStart + (trackWidth * position.coerceIn(0.0, 1.0)).toInt()
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
        val base = ClickGuiFeature.themeBase
        val accent = ClickGuiFeature.themeAccent
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

    private fun withAlpha(color: Color, alpha: Int) = Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255)).rgb
    private fun surfaceColor(tint: Float, alpha: Int = 255): Int {
        val base = ClickGuiFeature.themeBase
        val accent = ClickGuiFeature.themeAccent
        return Color(
            (base.red * (1f - tint) + accent.red * tint).toInt(),
            (base.green * (1f - tint) + accent.green * tint).toInt(),
            (base.blue * (1f - tint) + accent.blue * tint).toInt(), alpha
        ).rgb
    }
    private fun panelBorderColor(alpha: Int = 216) = withAlpha(ClickGuiFeature.themeAccent, alpha)
    private fun panelBottomLayerColor(alpha: Int = 255) = surfaceColor(0.015f, alpha)
    private fun sidebarSelectedColor(alpha: Int = 255) = surfaceColor(0.16f, alpha)
    private fun fieldFillColor(alpha: Int = 255) = surfaceColor(0.09f, alpha)
    private fun accentHighlightColor() = textPrimaryColor()
    private fun accentDarkColor() = panelBorderColor(100)
    private fun accentDimColor() = panelBorderColor(42)
    private fun accentLowColor(alpha: Int = 255) = surfaceColor(0.22f, alpha)
    private fun accentPanelColor(alpha: Int = 255) = surfaceColor(0.055f, alpha)
    private fun accentPanelDarkColor(alpha: Int = 255) = surfaceColor(0.04f, alpha)
    private fun accentBrightBorderColor() = panelBorderColor(210)
    private fun toggleOnColor() = ClickGuiFeature.themeAccent.rgb
    private fun toggleOffColor() = surfaceColor(0.16f)
    private fun textMutedColor() = Color(153, 157, 178).rgb
    private fun textPrimaryColor() = Color(239, 241, 250).rgb

    private fun hudButtonRect() = Rect(panelOriginX() + panelWidth() - 118, panelOriginY() + 15, 102, 25)
    private fun backButtonRect(): Rect {
        val area = featureAreaRect(panelOriginX(), panelOriginY())
        return Rect(area.x, area.y - 41, 24, 26)
    }
    private fun searchRect(): Rect {
        val area = featureAreaRect(panelOriginX(), panelOriginY())
        val searchWidth = (area.width / 2).coerceAtMost(150)
        return Rect(area.x + area.width - searchWidth, area.y - 40, searchWidth, 25)
    }
    private fun themeRect(index: Int): Rect {
        val sidebar = sidebarRect(panelOriginX(), panelOriginY())
        return Rect(sidebar.x + 6 + index * 24, sidebar.y + sidebar.height - 32, 18, 18)
    }

    private fun renderMainPanelBody(g: SkijaDraw, panelX: Int, panelY: Int) {
        g.roundedRect(panelX - 8, panelY + 6, panelWidth() + 16, panelHeight() + 8, 18, Color(0, 0, 0, 65).rgb)
        g.roundedRect(panelX - 3, panelY + 3, panelWidth() + 6, panelHeight() + 3, 14, Color(0, 0, 0, 100).rgb)
        g.roundedRect(panelX, panelY, panelWidth(), panelHeight(), 12, panelBottomLayerColor())
        g.gradientRect(panelX + 1, panelY + 1, panelWidth() - 2, 52, 12, panelBorderColor(32), panelBorderColor(0))
    }

    private fun renderTopDragBar(g: SkijaDraw, panelX: Int, panelY: Int) {
        g.gradientRect(panelX + 16, panelY + 13, 28, 28, 9, toggleOnColor(), surfaceColor(0.45f))
        g.centeredText("K", panelX + 30, panelY + 17, Color.WHITE.rgb, 18f)
        g.text("kittycat", panelX + 54, panelY + 17, textPrimaryColor(), 20f)
        val button = hudButtonRect()
        val hover = button.contains(pointerX.toDouble(), pointerY.toDouble())
        g.roundedRect(button.x, button.y, button.width, button.height, 7, if (hover) sidebarSelectedColor() else surfaceColor(0.07f))
        g.roundedRect(button.x, button.y, button.width, button.height, 7, panelBorderColor(if (hover) 180 else 65), 1)
        g.centeredText("HUD editor", button.x + button.width / 2, button.y + 7, textPrimaryColor(), 10f)
        val area = featureAreaRect(panelX, panelY)
        val focused = focusedFeature()
        val category = (focused?.category ?: categoryList.getOrNull(selectedIndex))?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Modules"
        if (focused != null) {
            val back = backButtonRect()
            g.roundedRect(back.x, back.y, back.width, back.height, 6, surfaceColor(0.1f))
            g.chevron(back.x + back.width / 2f, back.y + back.height / 2f, textPrimaryColor(), 180f)
            g.text(SkijaDraw.truncate("Module settings", area.width - 34, 18f), area.x + 34, area.y - 41, textPrimaryColor(), 18f)
            g.text(category, area.x + 34, area.y - 19, textMutedColor(), 9f)
        } else {
            val heading = if (searchQuery.isNotBlank()) "Results" else category
            g.text(SkijaDraw.truncate(heading, area.width - searchRect().width - 12, 21f), area.x, area.y - 43, textPrimaryColor(), 21f)
            g.text("${displayedFeatures().size} modules", area.x, area.y - 18, textMutedColor(), 9f)
            val search = searchRect()
            g.roundedRect(search.x, search.y, search.width, search.height, 7, surfaceColor(0.07f))
            g.roundedRect(search.x, search.y, search.width, search.height, 7, panelBorderColor(if (searchFocused) 150 else 38), 1)
            val value = if (searchQuery.isEmpty() && !searchFocused) "Search modules..." else searchQuery + if (searchFocused) "|" else ""
            g.text(SkijaDraw.truncate(value, search.width - 18, 9f), search.x + 9, search.y + 8, textMutedColor(), 9f)
        }
        if (maxFeatureScroll > 0) {
            val thumbHeight = (area.height * area.height / (area.height + maxFeatureScroll)).coerceAtLeast(16)
            val thumbY = area.y + (-featureScrollOffset.toFloat() / maxFeatureScroll * (area.height - thumbHeight)).toInt()
            g.roundedRect(area.x + area.width + 3, thumbY, 2, thumbHeight, 1, panelBorderColor(160))
        }
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
            if (focusedFeature() != null && backButtonRect().contains(mouseX, mouseY)) {
                closeInspector()
                return true
            }
            searchFocused = focusedFeature() == null && searchRect().contains(mouseX, mouseY)
            if (searchFocused) return true
            if (hudButtonRect().contains(mouseX, mouseY)) {
                commitTextInput()
                onClose()
                kitty.cat.gui.Hud.open(this)
                return true
            }
            ClickGuiFeature.themes.indices.firstOrNull { themeRect(it).contains(mouseX, mouseY) }?.let {
                ClickGuiFeature.theme.select(ClickGuiFeature.themes[it].name)
                playClickSound(1.1f)
                return true
            }
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
            draggingRange = null
            draggingHueSetting = null
            draggingAlphaSetting = null
            return true
        }

        // Open dropdown options sit above feature cards and settings. Give the
        // topmost open dropdown first chance to consume the click.
        if (mouseInFeatureArea && button == LEFT_MOUSE_BUTTON) {
            featureLayouts.asReversed().forEach { layout ->
                if (!layout.expanded) return@forEach
                layout.settingLayouts.asReversed().forEach { settingLayout ->
                    val setting = settingLayout.setting as? SelectorSetting ?: return@forEach
                    if (!setting.dropdownOpen) return@forEach
                    setting.options.forEachIndexed { optionIndex, option ->
                        if (!selectorOptionRect(settingLayout, optionIndex).contains(mouseX, mouseY)) return@forEachIndexed
                        setting.toggle(option)
                        if (!setting.allowMultiple) setting.dropdownOpen = false
                        playClickSound(1.0f)
                        return true
                    }
                }
            }
        }

        if (mouseInFeatureArea) {
            featureLayouts.forEach { layout ->
                if (!layout.isHeaderHovered(mouseX, mouseY)) return@forEach

                if (button == LEFT_MOUSE_BUTTON) {
                    layout.feature.toggle()
                    playClickSound(1.0f)
                    return true
                }

                if (button == RIGHT_MOUSE_BUTTON) {
                    if (focusedFeature() == null) inspectFeature(layout.feature)
                    playClickSound(0.95f)
                    return true
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
                                val opening = !setting.dropdownOpen
                                closeAllSelectorDropdowns()
                                setting.dropdownOpen = opening
                                updateFeatureScrollBounds()
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

                        is RangeSetting -> {
                            if (!settingLayout.contains(mouseX, mouseY)) return@forEach
                            if (button != LEFT_MOUSE_BUTTON) return@forEach

                            val textRect = rangeTextRect(settingLayout)
                            val sliderRect = rangeSliderRect(settingLayout)

                            when {
                                textRect.contains(mouseX, mouseY) -> {
                                    beginTextInput(setting, TextInputKind.RANGE, setting.editableText())
                                    return true
                                }
                                sliderRect.contains(mouseX, mouseY) -> {
                                    val handle = rangeHandleForMouse(setting, sliderRect, mouseX)
                                    updateRangeFromMouse(setting, handle, sliderRect, mouseX)
                                    draggingRange = RangeDrag(setting, handle)
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
                        is RegistrySetting -> {
                            if (button != LEFT_MOUSE_BUTTON) return@forEach
                            if (stringTextRect(settingLayout).contains(mouseX, mouseY)) {
                                if (!isTextInputActive(setting)) beginTextInput(setting, TextInputKind.REGISTRY, "")
                                return true
                            }
                            if (isTextInputActive(setting)) {
                                visibleRegistrySuggestions(setting).forEachIndexed { index, entry ->
                                    if (registryOptionRect(settingLayout, index).contains(mouseX, mouseY)) {
                                        setting.setValue(entry)
                                        cancelTextInput()
                                        return true
                                    }
                                }
                            }
                        }
                        is OrderSetting -> {
                            if (button != LEFT_MOUSE_BUTTON) return@forEach
                            setting.order.indices.firstOrNull { orderOptionRect(settingLayout, it).contains(mouseX, mouseY) }?.let {
                                draggingOrderSetting = setting
                                draggingOrderIndex = it
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
        draggingRange = null
        draggingHueSetting = null
        draggingAlphaSetting = null
        draggingSaturationBrightnessSetting = null
        draggingOrderSetting = null
        draggingOrderIndex = -1
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
        draggingOrderSetting?.let { setting ->
            val layout = findSettingLayout(setting) ?: return@let
            val target = ((mouseY - layout.y - FEATURE_SETTING_ROW_HEIGHT) / FEATURE_SETTING_ROW_HEIGHT).toInt().coerceIn(0, setting.order.lastIndex)
            if (target != draggingOrderIndex) {
                setting.move(draggingOrderIndex, target)
                draggingOrderIndex = target
            }
            return true
        }

        draggingNumberSetting?.let { setting ->
            findSettingLayout(setting)?.let { layout ->
                updateNumberFromMouse(setting, numberSliderRect(layout), mouseX)
                return true
            }
        }

        draggingRange?.let { drag ->
            findSettingLayout(drag.setting)?.let { layout ->
                updateRangeFromMouse(drag.setting, drag.handle, rangeSliderRect(layout), mouseX)
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
        if (searchFocused) {
            when (keyEvent.key()) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> searchFocused = false
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    featureScrollOffset = 0
                }
            }
            return true
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && focusedFeature() != null && textInputSession == null && keybindCaptureSetting == null && openColorPickerFor == null) {
            closeInspector()
            return true
        }
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
            val registry = activeInput.setting as? RegistrySetting
            if (registry != null) {
                val suggestions = registry.filteredSuggestions(activeInput.buffer)
                when (keyEvent.key()) {
                    GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP -> {
                        if (suggestions.isNotEmpty()) {
                            registryHighlight = if (keyEvent.key() == GLFW.GLFW_KEY_DOWN)
                                (registryHighlight + 1).coerceAtMost(suggestions.lastIndex)
                            else (registryHighlight - 1).coerceAtLeast(0)
                        }
                        return true
                    }
                    GLFW.GLFW_KEY_TAB -> {
                        suggestions.getOrNull(registryHighlight.coerceAtLeast(0))?.let {
                            activeInput.buffer = it
                            registryHighlight = -1
                        }
                        return true
                    }
                    GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                        suggestions.getOrNull(registryHighlight)?.let { activeInput.buffer = it }
                        commitTextInput()
                        return true
                    }
                }
            }
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
        if (searchFocused) {
            val codepoint = characterEvent.codepoint()
            if (codepoint >= 32 && searchQuery.length < 64) {
                searchQuery += String(Character.toChars(codepoint))
                featureScrollOffset = 0
            }
            return true
        }
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
        draggingRange = null
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

    private fun renderSettingRow(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
        val setting = settingLayout.setting
        drawText(
            graphics = graphics,
            sw = sw,
            sh = sh,
            scale = scale,
            text = SkijaDraw.truncate(setting.name, settingLabelWidth(settingLayout), 9f),
            x = (settingLayout.x + 1).toFloat(),
            y = (settingLayout.y + SETTING_NAME_Y_OFFSET).toFloat(),
            size = 9f,
            color = textPrimaryColor()
        )

        when (setting) {
            is BooleanSetting -> renderBooleanSetting(graphics, settingLayout, setting)
            is NumberSetting -> renderNumberSetting(graphics, sw, sh, scale, settingLayout, setting)
            is RangeSetting -> renderRangeSetting(graphics, sw, sh, scale, settingLayout, setting)
            is SelectorSetting -> renderSelectorSetting(graphics, sw, sh, scale, settingLayout, setting)
            is KeybindSetting -> renderKeybindSetting(graphics, sw, sh, scale, settingLayout, setting)
            is StringSetting -> renderStringSetting(graphics, sw, sh, scale, settingLayout, setting)
            is ColorSetting -> renderColorSetting(graphics, settingLayout, setting)
            is ActionSetting -> renderActionSetting(graphics, sw, sh, scale, settingLayout)
            is OrderSetting -> renderOrderSetting(graphics, sw, sh, scale, settingLayout, setting)
            is RegistrySetting -> renderRegistrySetting(graphics, settingLayout, setting)
        }
    }
    private fun visibleRegistrySuggestions(setting: RegistrySetting): List<String> =
        setting.filteredSuggestions(activeTextBufferOrNull(setting).orEmpty())
            .drop((registryHighlight - 5).coerceAtLeast(0)).take(6)

    private fun registryOptionRect(layout: SettingLayout, index: Int): Rect {
        val field = stringTextRect(layout)
        return Rect(field.x, layout.y + geometry(layout).fieldHeight + index * FEATURE_SETTING_ROW_HEIGHT,
            field.width, FEATURE_SETTING_ROW_HEIGHT)
    }

    private fun renderRegistrySetting(graphics: SkijaDraw, layout: SettingLayout, setting: RegistrySetting) {
        val rect = stringTextRect(layout)
        val active = isTextInputActive(setting)
        GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(graphics, rect.x, rect.y, rect.width, rect.height, 2, 1,
            if (active) accentBrightBorderColor() else accentDimColor())
        val buffer = activeTextBufferOrNull(setting)
        val display = if (active && buffer.isNullOrEmpty()) setting.placeholder else buffer ?: setting.value
        graphics.fieldText(fieldText(display, rect.width - 14, active && !buffer.isNullOrEmpty()),
            rect.x, rect.y, rect.width, rect.height, textMutedColor(), centered = true)
        if (!active) return
        val suggestions = visibleRegistrySuggestions(setting)
        if (suggestions.isEmpty()) {
            val row = registryOptionRect(layout, 0)
            graphics.fieldText("No matches", row.x, row.y, row.width, row.height, textMutedColor(), centered = true)
        }
        suggestions.forEachIndexed { index, entry ->
            val row = registryOptionRect(layout, index)
            val highlighted = index + (registryHighlight - 5).coerceAtLeast(0) == registryHighlight
            val hovered = row.contains(pointerX.toDouble(), pointerY.toDouble())
            GuiUtils.renderRoundedRectangle(graphics, row.x, row.y, row.width, row.height - 1, 2,
                if (highlighted || hovered) sidebarSelectedColor() else fieldFillColor())
            graphics.fieldText(fieldText(entry, row.width - 14), row.x, row.y, row.width, row.height,
                if (entry == setting.value || highlighted) textPrimaryColor() else textMutedColor(), centered = true)
        }
    }

    private fun renderOrderSetting(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, layout: SettingLayout, setting: OrderSetting) {
        setting.order.forEachIndexed { index, option ->
            val rect = orderOptionRect(layout, index)
            val dragging = draggingOrderSetting === setting && draggingOrderIndex == index
            GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.width, rect.height - 1, 2, if (dragging) sidebarSelectedColor() else fieldFillColor())
            GuiUtils.renderRoundedOutline(graphics, rect.x, rect.y, rect.width, rect.height - 1, 2, 1, if (dragging) accentBrightBorderColor() else accentDimColor())
            drawText(graphics, sw, sh, scale, "=", (rect.x + 3).toFloat(), (rect.y + VALUE_TEXT_Y_OFFSET).toFloat(), 9f, textMutedColor())
            graphics.fieldText(fieldText(option, rect.width - 28), rect.x, rect.y, rect.width, rect.height - 1, textPrimaryColor(), centered = true)
        }
    }

    private fun renderBooleanSetting(graphics: SkijaDraw, settingLayout: SettingLayout, setting: BooleanSetting) {
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
            graphics,
            switchRect.x,
            switchRect.y,
            switchRect.width,
            switchRect.height,
            switchRect.height / 2,
            trackColor
        )
        GuiUtils.renderRoundedRectangle(
            graphics,
            knobX,
            knobY,
            knobSize,
            knobSize,
            knobSize / 2,
            textPrimaryColor()
        )
    }

    private fun renderNumberSetting(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: NumberSetting) {
        val textRect = numberTextRect(settingLayout)
        val sliderRect = numberSliderRect(settingLayout)

        GuiUtils.renderRoundedRectangle(
            graphics,
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
                graphics,
                sliderRect.x,
                sliderRect.y,
                fillWidth,
                sliderRect.height,
                sliderRect.height / 2,
                toggleOnColor()
            )
        }

        GuiUtils.renderRoundedOutline(
            graphics,
            textRect.x,
            textRect.y,
            textRect.width,
            textRect.height,
            3,
            1,
            if (isTextInputActive(setting)) accentBrightBorderColor() else accentDimColor()
        )

        val valueText = activeTextBufferOrNull(setting) ?: setting.textValue(includeUnit = true)
        graphics.fieldText(fieldText(valueText, textRect.width - 14, isTextInputActive(setting)), textRect.x, textRect.y, textRect.width, textRect.height, textMutedColor(), centered = true)
    }

    private fun renderRangeSetting(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        layout: SettingLayout,
        setting: RangeSetting
    ) {
        val textRect = rangeTextRect(layout)
        val sliderRect = rangeSliderRect(layout)
        val trackY = sliderRect.y + (sliderRect.height - NUMBER_SLIDER_HEIGHT) / 2

        GuiUtils.renderRoundedRectangle(
            graphics,
            sliderRect.x,
            trackY,
            sliderRect.width,
            NUMBER_SLIDER_HEIGHT,
            NUMBER_SLIDER_HEIGHT / 2,
            fieldFillColor(154)
        )

        val lowerX = rangeHandleX(sliderRect, setting.lowerSliderPosition())
        val upperX = rangeHandleX(sliderRect, setting.upperSliderPosition())
        val selectedWidth = (upperX - lowerX).coerceAtLeast(0)
        if (selectedWidth > 0) {
            GuiUtils.renderRoundedRectangle(
                graphics,
                lowerX,
                trackY,
                selectedWidth,
                NUMBER_SLIDER_HEIGHT,
                NUMBER_SLIDER_HEIGHT / 2,
                toggleOnColor()
            )
        }

        listOf(lowerX, upperX).forEach { handleX ->
            GuiUtils.renderRoundedRectangle(
                graphics,
                handleX - RANGE_HANDLE_SIZE / 2,
                sliderRect.y,
                RANGE_HANDLE_SIZE,
                RANGE_HANDLE_SIZE,
                RANGE_HANDLE_SIZE / 2,
                accentBrightBorderColor()
            )
        }

        GuiUtils.renderRoundedOutline(
            graphics,
            textRect.x,
            textRect.y,
            textRect.width,
            textRect.height,
            3,
            1,
            if (isTextInputActive(setting)) accentBrightBorderColor() else accentDimColor()
        )

        val valueText = activeTextBufferOrNull(setting) ?: setting.textValue(includeUnit = true)
        graphics.fieldText(fieldText(valueText, textRect.width - 14, isTextInputActive(setting)), textRect.x, textRect.y, textRect.width, textRect.height, textMutedColor(), centered = true)
    }

    private fun renderSelectorSetting(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: SelectorSetting) {
        val baseRect = selectorBaseRect(settingLayout)
        GuiUtils.renderRoundedRectangle(graphics, baseRect.x, baseRect.y, baseRect.width, baseRect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(graphics, baseRect.x, baseRect.y, baseRect.width, baseRect.height, 2, 1, accentDimColor())

        val selectedText = if (setting.allowMultiple) {
            setting.selected.joinToString(", ").ifBlank { "None" }
        } else {
            setting.selectedSingle
        }
        graphics.fieldText(fieldText(selectedText, baseRect.width - 32), baseRect.x, baseRect.y, baseRect.width, baseRect.height, textMutedColor(), centered = true)
        graphics.chevron(baseRect.x + baseRect.width - 9f, baseRect.y + baseRect.height / 2f, textPrimaryColor(), if (setting.dropdownOpen) 270f else 90f, 3f)
    }

    private fun renderKeybindSetting(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: KeybindSetting
    ) {
        val rect = keybindRect(settingLayout)
        val captureActive = keybindCaptureSetting === setting
        GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(
            graphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            2,
            1,
            if (captureActive) accentBrightBorderColor() else accentDimColor()
        )

        val value = if (captureActive) "Press key..." else setting.displayValue()
        graphics.fieldText(fieldText(value, rect.width - 14, isTextInputActive(setting)), rect.x, rect.y, rect.width, rect.height, textMutedColor(), centered = true)
    }

    private fun renderStringSetting(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: StringSetting
    ) {
        val rect = stringTextRect(settingLayout)
        GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
        GuiUtils.renderRoundedOutline(
            graphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            2,
            1,
            if (isTextInputActive(setting)) accentBrightBorderColor() else accentDimColor()
        )

        val raw = activeTextBufferOrNull(setting) ?: setting.value
        val value = raw
        graphics.fieldText(fieldText(value, rect.width - 14, isTextInputActive(setting)), rect.x, rect.y, rect.width, rect.height, textMutedColor(), centered = true)
    }

    private fun renderSelectorDropdownOverlay(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: SelectorSetting
    ) {
        val firstOptionRect = selectorOptionRect(settingLayout, 0)
        val overlayHeight = setting.options.size * FEATURE_SETTING_ROW_HEIGHT
        GuiUtils.renderRoundedRectangle(
            graphics,
            firstOptionRect.x,
            firstOptionRect.y,
            firstOptionRect.width,
            overlayHeight,
            2,
            fieldFillColor(214)
        )
        GuiUtils.renderRoundedOutline(
            graphics,
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
            GuiUtils.renderRoundedRectangle(graphics, optionRect.x, optionRect.y, optionRect.width, optionRect.height, 2, backgroundColor)
            graphics.fieldText(
                fieldText(option, optionRect.width - 14),
                optionRect.x, optionRect.y, optionRect.width, optionRect.height,
                if (selected) textPrimaryColor() else textMutedColor(), centered = true
            )
        }
    }

    private fun renderColorPickerOverlay(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float) {
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
            graphics,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            7,
            surfaceColor(0.035f)
        )
        GuiUtils.renderRoundedOutline(
            graphics,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            7,
            1,
            panelBorderColor(100)
        )

        graphics.text(SkijaDraw.truncate(setting.name, panel.width - 16, 11f), panel.x + 8, panel.y + 8, textPrimaryColor(), 11f)
        renderSaturationBrightnessBox(graphics, sbRect, setting)
        GuiUtils.renderRoundedOutline(graphics, sbRect.x, sbRect.y, sbRect.width, sbRect.height, 2, 1, accentDimColor())

        val sbMarkerX = sbRect.x + (setting.saturation * (sbRect.width - 1)).toInt().coerceIn(0, sbRect.width - 1)
        val sbMarkerY = sbRect.y + ((1f - setting.brightness) * (sbRect.height - 1)).toInt().coerceIn(0, sbRect.height - 1)
        GuiUtils.renderRoundedOutline(graphics, sbMarkerX - 2, sbMarkerY - 2, 5, 5, 2, 1, textPrimaryColor())

        renderHueBar(graphics, hueRect, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(graphics, hueRect.x, hueRect.y, hueRect.width, hueRect.height, 2, 1, accentDimColor())
        val hueKnobX = hueRect.x + ((setting.hue / 360f) * (hueRect.width - 1)).toInt().coerceIn(0, hueRect.width - 1)
        GuiUtils.renderRectangle(graphics, hueKnobX, hueRect.y - 1, 1, hueRect.height + 2, textPrimaryColor())

        renderAlphaBar(graphics, alphaRect, setting, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(graphics, alphaRect.x, alphaRect.y, alphaRect.width, alphaRect.height, 2, 1, accentDimColor())
        val alphaKnobX = alphaRect.x + (setting.alphaSliderPosition() * (alphaRect.width - 1)).toInt().coerceIn(0, alphaRect.width - 1)
        GuiUtils.renderRectangle(graphics, alphaKnobX, alphaRect.y - 1, 1, alphaRect.height + 2, textPrimaryColor())

        listOf(
            ColorChannel.RED,
            ColorChannel.GREEN,
            ColorChannel.BLUE,
            ColorChannel.ALPHA
        ).forEach { channel ->
            val rect = colorPickerChannelRect(picker, channel)
            graphics.centeredText(channel.name.take(1), rect.x + rect.width / 2, rect.y - 11, textMutedColor(), 8f)
            GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.width, rect.height, 2, fieldFillColor())
            GuiUtils.renderRoundedOutline(
                graphics,
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                2,
                1,
                if (isTextInputActive(setting, channel)) accentBrightBorderColor() else accentDimColor()
            )

            val text = activeTextBufferOrNull(setting, channel) ?: colorChannelValue(setting, channel).toString()
            graphics.fieldText(fieldText(text, rect.width - 10, isTextInputActive(setting, channel)), rect.x, rect.y, rect.width, rect.height, textMutedColor(), centered = true)
        }
    }

    private fun renderHueBar(graphics: SkijaDraw, rect: Rect, step: Int) {
        graphics.linearGradient(rect.x, rect.y, rect.width, rect.height,
            IntArray(7) { hsvToArgb(it * 60f, 1f, 1f, 255) })
    }

    private fun renderAlphaBar(graphics: SkijaDraw, rect: Rect, setting: ColorSetting, step: Int) {
        val rgb = (setting.red shl 16) or (setting.green shl 8) or setting.blue
        graphics.checkerboard(rect.x, rect.y, rect.width, rect.height)
        graphics.linearGradient(rect.x, rect.y, rect.width, rect.height, intArrayOf(rgb, rgb or 0xFF000000.toInt()))
    }

    private fun renderSaturationBrightnessBox(graphics: SkijaDraw, rect: Rect, setting: ColorSetting) {
        graphics.linearGradient(rect.x, rect.y, rect.width, rect.height,
            intArrayOf(Color.WHITE.rgb, hsvToArgb(setting.hue, 1f, 1f, 255)))
        graphics.linearGradient(rect.x, rect.y, rect.width, rect.height,
            intArrayOf(0x00000000, Color.BLACK.rgb), vertical = true)
    }

    private fun renderColorSetting(graphics: SkijaDraw, settingLayout: SettingLayout, setting: ColorSetting) {
        val rect = colorSwatchRect(settingLayout)
        val argb = (setting.alpha shl 24) or (setting.red shl 16) or (setting.green shl 8) or setting.blue
        graphics.roundedRect(rect.x, rect.y, rect.width, rect.height, 4, fieldFillColor())
        graphics.checkerboard(rect.x + 3, rect.y + 3, 10, 10)
        graphics.roundedRect(rect.x + 3, rect.y + 3, 10, 10, 0, argb)
        graphics.fieldText("#%02X%02X%02X".format(setting.red, setting.green, setting.blue),
            rect.x + 13, rect.y, rect.width - 13, rect.height, textPrimaryColor(), size = 8f)
        graphics.roundedRect(rect.x, rect.y, rect.width, rect.height, 4,
            if (openColorPickerFor === setting) accentBrightBorderColor() else accentDimColor(), 1)
    }

    private fun renderActionSetting(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
        val buttonRect = actionButtonRect(settingLayout)
        GuiUtils.renderRoundedRectangle(graphics, buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 3, fieldFillColor())
        GuiUtils.renderRoundedOutline(graphics, buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 3, 1, accentDimColor())
        graphics.fieldText(
            "Run", buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height,
            textPrimaryColor(), centered = true
        )
    }

    private fun buildFeatureLayouts(panelX: Int, panelY: Int): List<FeatureLayout> {
        val layouts = mutableListOf<FeatureLayout>()
        val featureArea = featureAreaRect(panelX, panelY)
        val columns = featureColumnCount(featureArea.width)
        val cardWidth = featureCardWidth(featureArea.width, columns)
        val columnHeights = IntArray(columns) { featureArea.y + featureScrollOffset }

        displayedFeatures().forEach { feature ->
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
            val height = settingHeight(setting, settingWidth)
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

    private fun settingHeight(setting: Setting, width: Int): Int = when (setting) {
        is RegistrySetting -> SettingGeometry(0, 0, width).fieldHeight + if (isTextInputActive(setting))
            visibleRegistrySuggestions(setting).size.coerceAtLeast(1) * FEATURE_SETTING_ROW_HEIGHT else 0
        is NumberSetting, is RangeSetting -> SettingGeometry(0, 0, width).sliderHeight
        is SelectorSetting -> SettingGeometry(0, 0, width).selectorHeight(setting.options.size, setting.dropdownOpen)
        is KeybindSetting, is StringSetting -> SettingGeometry(0, 0, width).fieldHeight
        is OrderSetting -> FEATURE_SETTING_ROW_HEIGHT * (setting.options.size + 1)
        else -> FEATURE_SETTING_ROW_HEIGHT
    }

    private fun settingLabelWidth(layout: SettingLayout): Int = when (layout.setting) {
        is BooleanSetting -> booleanSwitchRect(layout).x - layout.x - 8
        is ActionSetting -> actionButtonRect(layout).x - layout.x - 8
        is ColorSetting -> colorSwatchRect(layout).x - layout.x - 8
        is NumberSetting, is RangeSetting, is SelectorSetting, is KeybindSetting, is StringSetting, is RegistrySetting -> geometry(layout).label.width
        else -> layout.width
    }.coerceAtLeast(0)

    private fun fieldText(value: String, width: Int, editing: Boolean = false): String {
        if (!editing) return SkijaDraw.truncate(value, width.coerceAtLeast(0), 9f)
        var visible = value
        while (visible.isNotEmpty() && SkijaDraw.textWidth(visible + "|", 9f) > width) {
            visible = visible.substring(visible.offsetByCodePoints(0, 1))
        }
        return visible + "|"
    }

    private fun renderTooltip(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, text: String, mouseX: Int, mouseY: Int) {
        val maxWidth = minOf(300, width - 24).coerceAtLeast(40)
        val lines = mutableListOf<String>()
        var line = ""
        text.split(Regex("\\s+")).forEach { word ->
            val next = if (line.isBlank()) word else "$line $word"
            if (SkijaDraw.textWidth(next, TOOLTIP_TEXT_SIZE) > maxWidth && line.isNotEmpty()) {
                lines += SkijaDraw.truncate(line, maxWidth, TOOLTIP_TEXT_SIZE)
                line = word
            } else line = next
        }
        if (line.isNotEmpty()) lines += SkijaDraw.truncate(line, maxWidth, TOOLTIP_TEXT_SIZE)
        val visibleLines = lines.take(((height - 24) / 13).coerceAtLeast(1))
        val boxWidth = (visibleLines.maxOfOrNull { SkijaDraw.textWidth(it, TOOLTIP_TEXT_SIZE) } ?: 0f).toInt() + 12
        val boxHeight = visibleLines.size * 13 + 10
        val tx = (mouseX + 10).coerceIn(4, (width - boxWidth - 4).coerceAtLeast(4))
        val ty = (mouseY + 14).coerceIn(4, (height - boxHeight - 4).coerceAtLeast(4))
        GuiUtils.renderRoundedRectangle(graphics, tx, ty, boxWidth, boxHeight, 5, fieldFillColor(255))
        GuiUtils.renderRoundedOutline(graphics, tx, ty, boxWidth, boxHeight, 5, 1, accentDimColor())
        visibleLines.forEachIndexed { index, value ->
            graphics.text(value, tx + 6, ty + 5 + index * 13, textPrimaryColor(), TOOLTIP_TEXT_SIZE)
        }
    }

    private fun renderFeatureHeader(graphics: SkijaDraw, sw: Int, sh: Int, scale: Float, layout: FeatureLayout) {
        drawText(
            graphics,
            sw,
            sh,
            scale,
            SkijaDraw.truncate(layout.feature.name, layout.width - 60, 13f),
            (layout.x + 8).toFloat(),
            (layout.y + 8).toFloat(),
            13f,
            if (layout.feature.enabled) textPrimaryColor() else accentHighlightColor()
        )

        val categoryLabel = layout.feature.category.name.lowercase().replaceFirstChar { it.uppercase() }
        val description = if (searchQuery.isNotBlank() && !layout.expanded) {
            categoryLabel + if (layout.feature.description.isBlank()) "" else " / " + layout.feature.description.trim()
        } else layout.feature.description.trim()
        if (description.isNotEmpty()) {
            val availableWidth = layout.width - 56
            val words = description.split(Regex("\\s+"))
            var firstLine = ""
            var nextWord = 0
            while (nextWord < words.size) {
                val candidate = if (firstLine.isEmpty()) words[nextWord] else "$firstLine ${words[nextWord]}"
                if (SkijaDraw.textWidth(candidate, 9f) > availableWidth && firstLine.isNotEmpty()) break
                firstLine = candidate
                nextWord++
            }
            graphics.text(SkijaDraw.truncate(firstLine, availableWidth, 9f), layout.x + 8, layout.y + 26, textMutedColor(), 9f)
            if (nextWord < words.size) {
                graphics.text(SkijaDraw.truncate(words.drop(nextWord).joinToString(" "), availableWidth, 9f), layout.x + 8, layout.y + 38, textMutedColor(), 9f)
            }
        }
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
            graphics,
            switchRect.x,
            switchRect.y,
            switchRect.width,
            switchRect.height,
            switchRect.height / 2,
            trackColor
        )
        GuiUtils.renderRoundedRectangle(
            graphics,
            knobX,
            knobY,
            knobSize,
            knobSize,
            knobSize / 2,
            textPrimaryColor()
        )
    }

    private fun renderCategoryBar(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        panelX: Int,
        panelY: Int
    ) {
        if (categoryList.isEmpty()) return

        val sidebar = sidebarRect(panelX, panelY)
        GuiUtils.renderRoundedRectangle(graphics, sidebar.x, sidebar.y, sidebar.width, sidebar.height, 7, surfaceColor(0f))
        buildCategoryLayouts(panelX, panelY).forEach { layout ->
            val r = layout.rect
            if (layout.selected || r.contains(pointerX.toDouble(), pointerY.toDouble())) {
                GuiUtils.renderRoundedRectangle(graphics, r.x, r.y, r.width, r.height, 5, sidebarSelectedColor(240))
            }
            if (layout.selected) {
                GuiUtils.renderRoundedRectangle(graphics, r.x + 2, r.y + 6, 3, r.height - 12, 1, toggleOnColor())
            }
            val label = layout.category.name.lowercase().replaceFirstChar { it.uppercase() }
            graphics.text(label, r.x + 12, layout.textY.toInt(), if (layout.selected) textPrimaryColor() else textMutedColor())
            if (layout.selected) graphics.chevron(r.x + r.width - 12f, r.y + r.height / 2f, toggleOnColor(), size = 3f)
        }
        graphics.text("PALETTE / ${ClickGuiFeature.theme.selectedSingle}", sidebar.x + 6, sidebar.y + sidebar.height - 48, textMutedColor())
        ClickGuiFeature.themes.forEachIndexed { index, theme ->
            val r = themeRect(index)
            GuiUtils.renderRoundedRectangle(graphics, r.x, r.y, r.width, r.height, 6, Color(theme.accent).rgb)
            if (theme.name == ClickGuiFeature.theme.selectedSingle) {
                GuiUtils.renderRoundedOutline(graphics, r.x - 2, r.y - 2, r.width + 4, r.height + 4, 7, 1, textPrimaryColor())
            }
        }
    }

    private fun drawText(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        graphics.text(text, x, y, color, size)
    }

    private fun drawCenteredText(
        graphics: SkijaDraw,
        sw: Int,
        sh: Int,
        scale: Float,
        text: String,
        cx: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        graphics.centeredText(text, cx, y, color, size)
    }

    private fun selectCategory(index: Int, playSound: Boolean) {
        if (categoryList.isEmpty()) return
        val clamped = index.coerceIn(0, categoryList.lastIndex)
        val changed = clamped != selectedIndex

        selectedIndex = clamped
        persistedSelectedCategory = categoryList[selectedIndex]
        activeFeatures = featureList.filter { it.category == categoryList[selectedIndex] }
        expandedFeatures.clear()
        searchQuery = ""
        searchFocused = false
        featureScrollOffset = 0
        updateFeatureScrollBounds()
        closeAllSelectorDropdowns()
        cancelTextInput()
        openColorPickerFor = null
        draggingNumberSetting = null
        draggingRange = null
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
            TextInputKind.RANGE -> {
                val layout = findSettingLayout(session.setting) ?: return false
                rangeTextRect(layout).contains(mouseX, mouseY)
            }
            TextInputKind.COLOR_CHANNEL -> {
                val channel = session.colorChannel ?: return false
                val setting = session.setting as? ColorSetting ?: return false
                if (openColorPickerFor !== setting) return false
                val picker = colorPickerLayout()
                colorPickerChannelRect(picker, channel).contains(mouseX, mouseY)
            }
            TextInputKind.REGISTRY -> {
                val layout = findSettingLayout(session.setting) ?: return false
                val setting = session.setting as RegistrySetting
                stringTextRect(layout).contains(mouseX, mouseY) || visibleRegistrySuggestions(setting).indices.any {
                    registryOptionRect(layout, it).contains(mouseX, mouseY)
                }
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
