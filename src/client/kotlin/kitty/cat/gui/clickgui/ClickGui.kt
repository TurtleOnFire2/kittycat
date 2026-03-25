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
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import kitty.cat.utils.GuiUtils
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import org.reflections.Reflections
import java.awt.Color
import kotlin.math.abs

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

    private enum class TextInputKind { NUMBER, COLOR_CHANNEL }

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
        const val PANEL_WIDTH = 400
        const val PANEL_HEIGHT = 300
        const val DRAG_BAR_HEIGHT = 10
        const val DRAG_BAR_RADIUS = 5
        const val PANEL_BODY_RADIUS = 6
        var persistedSelectedCategory: Categories.Category? = null
        const val LEFT_MOUSE_BUTTON = 0
        const val RIGHT_MOUSE_BUTTON = 1

        const val FEATURE_START_Y = 50
        const val FEATURE_CARD_WIDTH = 190
        const val FEATURE_CARD_GAP = 6
        const val FEATURE_HEADER_HEIGHT = 20
        const val FEATURE_SETTINGS_TOP_PADDING = 6
        const val FEATURE_SETTINGS_BOTTOM_PADDING = 6
        const val FEATURE_SETTING_SIDE_PADDING = 8
        const val FEATURE_SETTING_ROW_HEIGHT = 14
        const val FEATURE_VIEW_BOTTOM_PADDING = 6
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

        const val FEATURE_SWITCH_WIDTH = 24
        const val FEATURE_SWITCH_HEIGHT = 12
        const val FEATURE_SWITCH_RIGHT_PADDING = 2
        const val FEATURE_SWITCH_KNOB_MARGIN = 2
        const val FEATURE_SWITCH_Y_OFFSET = -2

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

        const val CATEGORY_BAR_HEIGHT = 40
        const val CATEGORY_SCROLL_COOLDOWN_TICKS = 2
    }

    private var offsetX = 0
    private var offsetY = 0
    private var draggingPanel = false

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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val panelX = panelOriginX()
        val panelY = panelOriginY()
        val sw = minecraft.window.guiScaledWidth
        val sh = minecraft.window.guiScaledHeight
        val scale = minecraft.window.guiScale.toFloat()

        renderMainPanelBody(guiGraphics, panelX, panelY)
        renderTopDragBar(guiGraphics, panelX, panelY)

        renderCategoryBar(guiGraphics, sw, sh, scale, panelX, panelY)
        GuiUtils.renderRectangle(guiGraphics, panelX, panelY + 40, PANEL_WIDTH, 2, accentDarkColor())

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
                layout.feature.enabled -> accentDarkColor()
                layout.expanded -> Color.WHITE.rgb
                else -> accentHighlightColor()
            }

            GuiUtils.renderRoundedRectangle(
                guiGraphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                5,
                accentPanelColor(alpha = 31)
            )
            GuiUtils.renderRoundedOutline(
                guiGraphics,
                layout.x,
                layout.y,
                layout.width,
                layout.totalHeight,
                5,
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
                accentDarkColor()
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
                    accentHighlightColor()
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

        renderColorPickerOverlay(guiGraphics, sw, sh, scale)

        super.render(guiGraphics, mouseX, mouseY, partialTicks)
    }

    private fun panelOriginX(): Int = this.width / 2 - PANEL_WIDTH / 2 + offsetX
    private fun panelOriginY(): Int = this.height / 2 - PANEL_HEIGHT / 2 + offsetY

    private fun circularRelativeOffset(index: Int, selected: Int, size: Int): Int {
        var offset = index - selected
        val half = size / 2
        if (offset > half) offset -= size
        if (offset < -half) offset += size
        return offset
    }

    private fun buildCategoryLayouts(panelX: Int, panelY: Int): List<CategoryLayout> {
        if (categoryList.isEmpty()) return emptyList()

        val centerX = panelX + PANEL_WIDTH / 2
        val categoryY = panelY + 20
        val spacing = 70

        return categoryList.mapIndexed { index, category ->
            val offset = circularRelativeOffset(index, selectedIndex, categoryList.size)
            val distance = abs(offset)

            val fontSize = when (distance) {
                0 -> 12f
                1 -> 10.5f
                2 -> 9.5f
                else -> 8.5f
            }
            val categoryCenterX = centerX + offset * spacing
            val estimatedTextWidth = (category.name.length * fontSize * 0.58f).toInt().coerceAtLeast(18)
            val hitPaddingX = 6
            val hitPaddingY = 4
            val x = categoryCenterX - estimatedTextWidth / 2 - hitPaddingX
            val y = categoryY - hitPaddingY
            val textY = categoryY.toFloat()

            CategoryLayout(
                index = index,
                category = category,
                rect = Rect(x, y, estimatedTextWidth + hitPaddingX * 2, (fontSize + hitPaddingY * 2).toInt()),
                centerX = categoryCenterX.toFloat(),
                textY = textY,
                fontSize = fontSize,
                selected = index == selectedIndex
            )
        }
    }

    private fun featureClipRect(panelX: Int, panelY: Int): Rect {
        return Rect(
            x = panelX + 2,
            y = panelY + FEATURE_START_Y,
            width = PANEL_WIDTH - 4,
            height = PANEL_HEIGHT - FEATURE_START_Y - FEATURE_VIEW_BOTTOM_PADDING
        )
    }

    private fun updateFeatureScrollBounds() {
        val visibleHeight = (PANEL_HEIGHT - FEATURE_START_Y - FEATURE_VIEW_BOTTOM_PADDING).coerceAtLeast(0)
        val contentHeight = computeFeatureContentHeight()
        maxFeatureScroll = (contentHeight - visibleHeight).coerceAtLeast(0)
        featureScrollOffset = featureScrollOffset.coerceIn(-maxFeatureScroll, 0)
    }

    private fun computeFeatureContentHeight(): Int {
        var leftHeight = 0
        var rightHeight = 0

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

            if (leftHeight <= rightHeight) {
                leftHeight += totalHeight + FEATURE_CARD_GAP
            } else {
                rightHeight += totalHeight + FEATURE_CARD_GAP
            }
        }

        val tallestColumn = maxOf(leftHeight, rightHeight)
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

    private fun colorSwatchRect(layout: SettingLayout): Rect {
        return Rect(
            x = layout.x + layout.width - 14,
            y = layout.y + 1,
            width = 12,
            height = 12
        )
    }

    private fun colorPickerLayout(): ColorPickerLayout {
        val panelX = panelOriginX() + PANEL_WIDTH + COLOR_PICKER_OUTER_GAP
        val panelY = panelOriginY() + FEATURE_START_Y

        val sbRect = Rect(
            x = panelX + COLOR_PICKER_PADDING,
            y = panelY + COLOR_PICKER_PADDING + COLOR_PICKER_CONTENT_Y_OFFSET,
            width = COLOR_PICKER_PANEL_WIDTH - COLOR_PICKER_PADDING * 2,
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
            width = COLOR_PICKER_PANEL_WIDTH,
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
            y = layout.y + (FEATURE_SETTING_ROW_HEIGHT - FEATURE_SWITCH_HEIGHT) / 2 + FEATURE_SWITCH_Y_OFFSET,
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

    private fun baseGuiColor(): Int {
        val setting = ClickGuiFeature.baseColor
        return Color(setting.red, setting.green, setting.blue, setting.alpha).rgb
    }

    private fun accentGuiColor(): Int {
        val setting = ClickGuiFeature.accentColor
        return accentTone(1.0f, setting.alpha)
    }

    private fun accentHighlightColor(): Int {
        return accentTone(1.82f)
    }

    private fun accentDarkColor(): Int {
        return accentTone(0.91f)
    }

    private fun accentDimColor(): Int {
        return accentTone(0.86f)
    }

    private fun accentLowColor(alpha: Int = 255): Int {
        return accentTone(0.43f, alpha)
    }

    private fun accentPanelColor(alpha: Int = 255): Int {
        return accentTone(0.26f, alpha)
    }

    private fun accentPanelDarkColor(alpha: Int = 255): Int {
        return accentTone(0.20f, alpha)
    }

    private fun accentBrightBorderColor(): Int {
        return accentToneAdd(115)
    }

    private fun topBarColor(): Int {
        return accentToneAdd(1, alpha = 150)
    }

    private fun renderMainPanelBody(guiGraphics: GuiGraphics, panelX: Int, panelY: Int) {
        val bodyY = panelY + DRAG_BAR_HEIGHT
        val bodyHeight = PANEL_HEIGHT - DRAG_BAR_HEIGHT
        val straightHeight = (bodyHeight - PANEL_BODY_RADIUS).coerceAtLeast(0)

        if (straightHeight > 0) {
            GuiUtils.renderRectangle(
                guiGraphics,
                panelX,
                bodyY,
                PANEL_WIDTH,
                straightHeight,
                baseGuiColor()
            )
        }

        val roundedBandTop = bodyY + straightHeight
        guiGraphics.enableScissor(
            panelX,
            roundedBandTop,
            panelX + PANEL_WIDTH,
            bodyY + bodyHeight
        )
        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            panelX,
            bodyY,
            PANEL_WIDTH,
            bodyHeight,
            PANEL_BODY_RADIUS,
            baseGuiColor()
        )
        guiGraphics.disableScissor()
    }

    private fun renderTopDragBar(guiGraphics: GuiGraphics, panelX: Int, panelY: Int) {
        val lowerY = panelY + DRAG_BAR_RADIUS
        val lowerHeight = (DRAG_BAR_HEIGHT - DRAG_BAR_RADIUS).coerceAtLeast(0)

        if (lowerHeight > 0) {
            GuiUtils.renderRectangle(
                guiGraphics,
                panelX,
                lowerY,
                PANEL_WIDTH,
                lowerHeight,
                topBarColor()
            )
        }

        guiGraphics.enableScissor(
            panelX,
            panelY,
            panelX + PANEL_WIDTH,
            panelY + DRAG_BAR_RADIUS
        )
        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            panelX,
            panelY,
            PANEL_WIDTH,
            DRAG_BAR_HEIGHT,
            DRAG_BAR_RADIUS,
            topBarColor()
        )
        guiGraphics.disableScissor()
    }

    private fun accentTone(multiplier: Float, alpha: Int = 255): Int {
        val setting = ClickGuiFeature.accentColor
        val red = (setting.red * multiplier).toInt().coerceIn(0, 255)
        val green = (setting.green * multiplier).toInt().coerceIn(0, 255)
        val blue = (setting.blue * multiplier).toInt().coerceIn(0, 255)
        return Color(red, green, blue, alpha.coerceIn(0, 255)).rgb
    }

    private fun accentToneAdd(offset: Int, alpha: Int = 255): Int {
        val setting = ClickGuiFeature.accentColor
        return Color(
            (setting.red + offset).coerceIn(0, 255),
            (setting.green + offset).coerceIn(0, 255),
            (setting.blue + offset).coerceIn(0, 255),
            alpha.coerceIn(0, 255)
        ).rgb
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

        val titleBarRect = Rect(panelX, panelY, PANEL_WIDTH, DRAG_BAR_HEIGHT)
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
        val categoryArea = Rect(panelX, panelY, PANEL_WIDTH, CATEGORY_BAR_HEIGHT)
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

    private fun renderSettingRow(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
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
            color = Color.WHITE.rgb
        )

        when (setting) {
            is BooleanSetting -> renderBooleanSetting(guiGraphics, settingLayout, setting)
            is NumberSetting -> renderNumberSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is SelectorSetting -> renderSelectorSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is KeybindSetting -> renderKeybindSetting(guiGraphics, sw, sh, scale, settingLayout, setting)
            is ColorSetting -> renderColorSetting(guiGraphics, settingLayout, setting)
            is ActionSetting -> renderActionSetting(guiGraphics, sw, sh, scale, settingLayout)
        }
    }

    private fun renderBooleanSetting(guiGraphics: GuiGraphics, settingLayout: SettingLayout, setting: BooleanSetting) {
        val switchRect = booleanSwitchRect(settingLayout)
        val trackColor = if (setting.value) Color(89, 191, 113).rgb else Color(70, 70, 70).rgb
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
            Color.WHITE.rgb
        )
    }

    private fun renderNumberSetting(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: NumberSetting) {
        val textRect = numberTextRect(settingLayout)
        val sliderRect = numberSliderRect(settingLayout)

        GuiUtils.renderRoundedRectangle(
            guiGraphics,
            sliderRect.x,
            sliderRect.y,
            sliderRect.width,
            sliderRect.height,
            sliderRect.height / 2,
            Color(60, 60, 60).rgb
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
                Color(89, 191, 113).rgb
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
            if (isTextInputActive(setting)) Color.WHITE.rgb else Color.GRAY.rgb
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
            Color.LIGHT_GRAY.rgb
        )
    }

    private fun renderSelectorSetting(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout, setting: SelectorSetting) {
        val baseRect = selectorBaseRect(settingLayout)
        GuiUtils.renderRoundedOutline(guiGraphics, baseRect.x, baseRect.y, baseRect.width, baseRect.height, 3, 1, Color.GRAY.rgb)

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
            Color.LIGHT_GRAY.rgb
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
            Color.LIGHT_GRAY.rgb
        )
    }

    private fun renderKeybindSetting(
        guiGraphics: GuiGraphics,
        sw: Int,
        sh: Int,
        scale: Float,
        settingLayout: SettingLayout,
        setting: KeybindSetting
    ) {
        val rect = keybindRect(settingLayout)
        val captureActive = keybindCaptureSetting === setting
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            3,
            1,
            if (captureActive) Color.WHITE.rgb else Color.GRAY.rgb
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
            Color.LIGHT_GRAY.rgb
        )
    }

    private fun renderSelectorDropdownOverlay(
        guiGraphics: GuiGraphics,
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
            Color(18, 18, 22, 255).rgb
        )

        setting.options.forEachIndexed { optionIndex, option ->
            val optionRect = selectorOptionRect(settingLayout, optionIndex)
            val selected = setting.isSelected(option)
            val backgroundColor = if (selected) Color(50, 110, 70, 255).rgb else Color(30, 30, 30, 255).rgb
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
                if (selected) Color.WHITE.rgb else Color.LIGHT_GRAY.rgb
            )
        }
    }

    private fun renderColorPickerOverlay(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float) {
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
            4,
            Color(14, 14, 18, 255).rgb
        )
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            panel.x,
            panel.y,
            panel.width,
            panel.height,
            4,
            1,
            Color(185, 185, 195).rgb
        )

        renderSaturationBrightnessBox(guiGraphics, sbRect, setting)
        GuiUtils.renderRoundedOutline(guiGraphics, sbRect.x, sbRect.y, sbRect.width, sbRect.height, 2, 1, Color.GRAY.rgb)

        val sbMarkerX = sbRect.x + (setting.saturation * (sbRect.width - 1)).toInt().coerceIn(0, sbRect.width - 1)
        val sbMarkerY = sbRect.y + ((1f - setting.brightness) * (sbRect.height - 1)).toInt().coerceIn(0, sbRect.height - 1)
        GuiUtils.renderRoundedOutline(guiGraphics, sbMarkerX - 2, sbMarkerY - 2, 5, 5, 2, 1, Color.WHITE.rgb)

        renderHueBar(guiGraphics, hueRect, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(guiGraphics, hueRect.x, hueRect.y, hueRect.width, hueRect.height, 2, 1, Color.GRAY.rgb)
        val hueKnobX = hueRect.x + ((setting.hue / 360f) * (hueRect.width - 1)).toInt().coerceIn(0, hueRect.width - 1)
        GuiUtils.renderRectangle(guiGraphics, hueKnobX, hueRect.y - 1, 1, hueRect.height + 2, Color.WHITE.rgb)

        renderAlphaBar(guiGraphics, alphaRect, setting, COLOR_PICKER_BAR_STEP)
        GuiUtils.renderRoundedOutline(guiGraphics, alphaRect.x, alphaRect.y, alphaRect.width, alphaRect.height, 2, 1, Color.GRAY.rgb)
        val alphaKnobX = alphaRect.x + (setting.alphaSliderPosition() * (alphaRect.width - 1)).toInt().coerceIn(0, alphaRect.width - 1)
        GuiUtils.renderRectangle(guiGraphics, alphaKnobX, alphaRect.y - 1, 1, alphaRect.height + 2, Color.WHITE.rgb)

        listOf(
            ColorChannel.RED,
            ColorChannel.GREEN,
            ColorChannel.BLUE,
            ColorChannel.ALPHA
        ).forEach { channel ->
            val rect = colorPickerChannelRect(picker, channel)
            GuiUtils.renderRoundedOutline(
                guiGraphics,
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                3,
                1,
                if (isTextInputActive(setting, channel)) Color.WHITE.rgb else Color.GRAY.rgb
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
                Color.LIGHT_GRAY.rgb
            )
        }
    }

    private fun renderHueBar(guiGraphics: GuiGraphics, rect: Rect, step: Int) {
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

    private fun renderAlphaBar(guiGraphics: GuiGraphics, rect: Rect, setting: ColorSetting, step: Int) {
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

    private fun renderSaturationBrightnessBox(guiGraphics: GuiGraphics, rect: Rect, setting: ColorSetting) {
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

    private fun renderColorSetting(guiGraphics: GuiGraphics, settingLayout: SettingLayout, setting: ColorSetting) {
        val swatchRect = colorSwatchRect(settingLayout)

        val argb = (setting.alpha shl 24) or (setting.red shl 16) or (setting.green shl 8) or setting.blue
        GuiUtils.renderRoundedRectangle(guiGraphics, swatchRect.x, swatchRect.y, swatchRect.width, swatchRect.height, 2, argb)
        GuiUtils.renderRoundedOutline(guiGraphics, swatchRect.x, swatchRect.y, swatchRect.width, swatchRect.height, 2, 1, Color.GRAY.rgb)
    }

    private fun renderActionSetting(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float, settingLayout: SettingLayout) {
        val buttonRect = actionButtonRect(settingLayout)
        GuiUtils.renderRoundedOutline(guiGraphics, buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height, 4, 1, Color.GRAY.rgb)
        drawCenteredText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Run",
            (buttonRect.x + buttonRect.width / 2).toFloat(),
            (buttonRect.y + VALUE_TEXT_Y_OFFSET).toFloat(),
            9f,
            Color.WHITE.rgb
        )
    }

    private fun buildFeatureLayouts(panelX: Int, panelY: Int): List<FeatureLayout> {
        val layouts = mutableListOf<FeatureLayout>()

        val leftX = panelX + 7
        val rightX = leftX + FEATURE_CARD_WIDTH + FEATURE_CARD_GAP
        var leftY = panelY + FEATURE_START_Y + featureScrollOffset
        var rightY = panelY + FEATURE_START_Y + featureScrollOffset

        activeFeatures.forEach { feature ->
            val useLeftColumn = leftY <= rightY
            val cardX = if (useLeftColumn) leftX else rightX
            val cardY = if (useLeftColumn) leftY else rightY
            val expanded = feature in expandedFeatures

            val settingLayouts = if (expanded) buildSettingLayouts(feature, cardX, cardY) else emptyList()
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
                width = FEATURE_CARD_WIDTH,
                headerHeight = FEATURE_HEADER_HEIGHT,
                totalHeight = totalHeight,
                settingLayouts = settingLayouts,
                expanded = expanded
            )

            if (useLeftColumn) {
                leftY += totalHeight + FEATURE_CARD_GAP
            } else {
                rightY += totalHeight + FEATURE_CARD_GAP
            }
        }

        return layouts
    }

    private fun buildSettingLayouts(feature: Feature, cardX: Int, cardY: Int): List<SettingLayout> {
        val settingLayouts = mutableListOf<SettingLayout>()
        var cursorY = cardY + FEATURE_HEADER_HEIGHT + FEATURE_SETTINGS_TOP_PADDING

        val settingX = cardX + FEATURE_SETTING_SIDE_PADDING
        val settingWidth = FEATURE_CARD_WIDTH - FEATURE_SETTING_SIDE_PADDING * 2

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

    private fun renderFeatureHeader(guiGraphics: GuiGraphics, sw: Int, sh: Int, scale: Float, layout: FeatureLayout) {
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            layout.feature.name,
            (layout.x + 8).toFloat(),
            (layout.y + 5).toFloat(),
            12f,
            if (layout.feature.enabled) Color(170, 255, 170).rgb else Color.WHITE.rgb
        )

        val switchRect = featureSwitchRect(layout)
        val trackColor = if (layout.feature.enabled) Color(89, 191, 113).rgb else Color(70, 70, 70).rgb
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
        GuiUtils.renderRoundedRectangle(guiGraphics, knobX, knobY, knobSize, knobSize, knobSize / 2, Color.WHITE.rgb)
    }

    private fun renderCategoryBar(
        guiGraphics: GuiGraphics,
        sw: Int,
        sh: Int,
        scale: Float,
        panelX: Int,
        panelY: Int
    ) {
        if (categoryList.isEmpty()) return

        val categoryLayouts = buildCategoryLayouts(panelX, panelY)
        val activeLayout = categoryLayouts.firstOrNull { it.selected }
        if (activeLayout != null) {
            val indicatorWidth = ((activeLayout.category.name.length * activeLayout.fontSize * 0.56f) + 12f)
                .toInt()
                .coerceIn(26, 80)
            val indicatorX = (activeLayout.centerX - indicatorWidth / 2f).toInt()
            val indicatorY = panelY + CATEGORY_BAR_HEIGHT - 7

            GuiUtils.renderRoundedRectangle(
                guiGraphics,
                indicatorX,
                indicatorY,
                indicatorWidth,
                4,
                2,
                Color(255, 255, 255, 52).rgb
            )
            GuiUtils.renderRoundedRectangle(
                guiGraphics,
                indicatorX + 2,
                indicatorY + 1,
                indicatorWidth - 4,
                2,
                1,
                Color(255, 255, 255, 214).rgb
            )
        }

        categoryLayouts.forEach { layout ->
            val offset = circularRelativeOffset(layout.index, selectedIndex, categoryList.size)
            val distance = abs(offset)
            val color = when (distance) {
                0 -> Color.WHITE.rgb
                1 -> Color(214, 214, 222).rgb
                2 -> Color(184, 184, 194).rgb
                else -> Color(160, 160, 170).rgb
            }
            drawCenteredText(
                guiGraphics,
                sw,
                sh,
                scale,
                layout.category.name,
                layout.centerX,
                layout.textY,
                layout.fontSize,
                color
            )
        }
    }

    private fun drawText(
        guiGraphics: GuiGraphics,
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
        guiGraphics: GuiGraphics,
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
        val normalized = ((index % categoryList.size) + categoryList.size) % categoryList.size
        val changed = normalized != selectedIndex

        selectedIndex = normalized
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
        }
    }

    private fun playClickSound(pitch: Float) {
        minecraft.player?.playSound(SoundEvents.LEVER_CLICK, 0.1f, pitch)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}
