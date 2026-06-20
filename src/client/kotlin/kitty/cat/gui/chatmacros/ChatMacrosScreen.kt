package kitty.cat.gui.chatmacros

import kitty.cat.features.misc.ChatMacros
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import kitty.cat.utils.GuiUtils
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs

class ChatMacrosScreen(private val parent: Screen?) : Screen(Component.literal("Chat Macro Manager")) {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX in x.toDouble()..(x + width).toDouble() &&
                mouseY in y.toDouble()..(y + height).toDouble()
        }
    }

    private data class FieldViewport(
        val startIndex: Int,
        val endIndex: Int,
        val visibleText: String
    )

    private enum class InputField {
        TRIGGER,
        COMMAND,
        TEST
    }

    private companion object {
        const val PANEL_MIN_WIDTH = 680
        const val PANEL_MIN_HEIGHT = 380
        const val PANEL_WIDTH_RATIO = 0.82f
        const val PANEL_HEIGHT_RATIO = 0.78f
        const val PANEL_PADDING = 12

        const val LIST_WIDTH = 252
        const val LIST_HEADER_HEIGHT = 22
        const val ROW_HEIGHT = 32

        const val DETAIL_BUTTON_WIDTH = 92
        const val DETAIL_BUTTON_HEIGHT = 18
        const val DETAIL_BUTTON_GAP = 8

        const val FIELD_HEIGHT = 18
        const val FIELD_TEXT_MAX_LENGTH = 160

        const val TITLE_SIZE = 13f
        const val TEXT_SIZE = 9f
        const val LEFT_MOUSE_BUTTON = 0
    }

    private var selectedIndex = -1
    private var focusedField: InputField? = null
    private var listScrollRows = 0
    private var tickCounter = 0
    private val testInputByIndex = mutableMapOf<Int, String>()
    private var caretIndex = 0
    private var selectionAnchorIndex: Int? = null

    override fun init() {
        val count = ChatMacros.listMacros().size
        selectedIndex = when {
            count <= 0 -> -1
            selectedIndex in 0 until count -> selectedIndex
            else -> 0
        }
    }

    override fun tick() {
        tickCounter++
        super.tick()
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val macros = ChatMacros.listMacros()
        pruneTestInputs(macros.size)
        clampSelectedIndex(macros.size)

        val panel = panelRect()
        val listPanel = listPanelRect(panel)
        val detailPanel = detailPanelRect(panel)

        val listRows = listRowsRect(listPanel)
        val visibleRows = visibleRows(listRows.height)
        clampListScroll(macros.size, visibleRows)
        ensureSelectionVisible(visibleRows)

        GuiUtils.renderRectangle(guiGraphics, 0, 0, width, height, Color(0, 0, 0, 140).rgb)
        GuiUtils.renderRoundedRectangle(guiGraphics, panel.x, panel.y, panel.width, panel.height, 0, Color(24, 9, 14, 226).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, panel.x, panel.y, panel.width, panel.height, 0, 1, Color(204, 84, 116, 238).rgb)

        val sw = minecraft.window.guiScaledWidth
        val sh = minecraft.window.guiScaledHeight
        val scale = minecraft.window.guiScale.toFloat()

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Chat Macro Manager",
            (panel.x + PANEL_PADDING).toFloat(),
            (panel.y + PANEL_PADDING - 1).toFloat(),
            TITLE_SIZE,
            Color(246, 227, 233, 255).rgb
        )

        renderListPanel(guiGraphics, sw, sh, scale, listPanel, listRows, macros)
        renderDetailPanel(guiGraphics, sw, sh, scale, detailPanel, macros, mouseX.toDouble(), mouseY.toDouble())

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubled: Boolean): Boolean {
        if (mouseButtonEvent.button() != LEFT_MOUSE_BUTTON) return super.mouseClicked(mouseButtonEvent, doubled)

        val mouseX = mouseButtonEvent.x()
        val mouseY = mouseButtonEvent.y()
        val macros = ChatMacros.listMacros()
        clampSelectedIndex(macros.size)

        val panel = panelRect()
        val listPanel = listPanelRect(panel)
        val detailPanel = detailPanelRect(panel)
        val listRows = listRowsRect(listPanel)
        val visibleRows = visibleRows(listRows.height)
        clampListScroll(macros.size, visibleRows)

        val addRect = addButtonRect(detailPanel)
        if (addRect.contains(mouseX, mouseY)) {
            selectedIndex = ChatMacros.addMacro()
            focusField(InputField.TRIGGER, "")
            ensureSelectionVisible(visibleRows)
            return true
        }

        val selectedMacro = macros.getOrNull(selectedIndex)
        if (selectedMacro != null) {
            val deleteRect = deleteButtonRect(detailPanel)
            if (deleteRect.contains(mouseX, mouseY)) {
                shiftTestInputsAfterDeletion(selectedIndex)
                ChatMacros.deleteMacro(selectedIndex)
                val newCount = ChatMacros.listMacros().size
                clampSelectedIndex(newCount)
                clearFieldFocus()
                clampListScroll(newCount, visibleRows)
                return true
            }

            val toggleRect = toggleButtonRect(detailPanel)
            if (toggleRect.contains(mouseX, mouseY)) {
                ChatMacros.toggleMacroActive(selectedIndex)
                return true
            }

            val triggerRect = triggerFieldRect(detailPanel)
            if (triggerRect.contains(mouseX, mouseY)) {
                focusFieldWithClickCaret(InputField.TRIGGER, selectedMacro.trigger, triggerRect, mouseX)
                return true
            }

            val commandRect = commandFieldRect(detailPanel)
            if (commandRect.contains(mouseX, mouseY)) {
                focusFieldWithClickCaret(InputField.COMMAND, selectedMacro.command, commandRect, mouseX)
                return true
            }

            val testRect = testFieldRect(detailPanel)
            if (testRect.contains(mouseX, mouseY)) {
                focusFieldWithClickCaret(InputField.TEST, testInputFor(selectedIndex), testRect, mouseX)
                return true
            }
        }

        if (listRows.contains(mouseX, mouseY)) {
            val clickedIndex = listIndexAt(mouseY, listRows, macros.size)
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex
                clearFieldFocus()
                return true
            }
        }

        clearFieldFocus()
        return super.mouseClicked(mouseButtonEvent, doubled)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double, g: Double): Boolean {
        val macros = ChatMacros.listMacros()
        if (macros.isEmpty()) return super.mouseScrolled(d, e, f, g)

        val listRows = listRowsRect(listPanelRect(panelRect()))
        if (!listRows.contains(d, e)) return super.mouseScrolled(d, e, f, g)

        val visibleRows = visibleRows(listRows.height)
        val maxScroll = (macros.size - visibleRows).coerceAtLeast(0)
        val previous = listScrollRows
        when {
            g > 0.0 -> listScrollRows = (listScrollRows - 1).coerceAtLeast(0)
            g < 0.0 -> listScrollRows = (listScrollRows + 1).coerceAtMost(maxScroll)
        }

        return listScrollRows != previous || super.mouseScrolled(d, e, f, g)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        val key = keyEvent.key()
        val macros = ChatMacros.listMacros()
        clampSelectedIndex(macros.size)
        val selectedMacro = macros.getOrNull(selectedIndex)
        val activeField = focusedField

        if (selectedMacro != null && activeField != null) {
            val current = currentFieldValue(selectedMacro, activeField)

            when {
                isControlKeyDown() && key == GLFW.GLFW_KEY_A -> {
                    selectionAnchorIndex = 0
                    caretIndex = current.length
                    return true
                }
                isControlKeyDown() && key == GLFW.GLFW_KEY_C -> {
                    val selectedText = selectionBounds(current)?.let { (start, end) ->
                        current.substring(start, end)
                    }
                    writeClipboardText(selectedText ?: current)
                    return true
                }
                isControlKeyDown() && key == GLFW.GLFW_KEY_V -> {
                    val clipboard = readClipboardText()
                    if (clipboard.isNotEmpty()) {
                        val (nextText, nextCaret) = insertAtCaret(current, clipboard)
                        updateFocusedField(activeField, nextText)
                        caretIndex = nextCaret
                    }
                    selectionAnchorIndex = null
                    return true
                }
            }

            when (key) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val (nextText, nextCaret) = backspaceAtCaret(current, deleteWord = isControlKeyDown())
                    updateFocusedField(activeField, nextText)
                    caretIndex = nextCaret
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_DELETE -> {
                    val (nextText, nextCaret) = deleteAtCaret(current)
                    updateFocusedField(activeField, nextText)
                    caretIndex = nextCaret
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_LEFT -> {
                    val selection = selectionBounds(current)
                    caretIndex = if (selection != null) selection.first else (caretIndex - 1).coerceAtLeast(0)
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_RIGHT -> {
                    val selection = selectionBounds(current)
                    caretIndex = if (selection != null) selection.second else (caretIndex + 1).coerceAtMost(current.length)
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_HOME -> {
                    caretIndex = 0
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_END -> {
                    caretIndex = current.length
                    selectionAnchorIndex = null
                    return true
                }
                GLFW.GLFW_KEY_TAB -> {
                    val nextField = when (activeField) {
                        InputField.TRIGGER -> InputField.COMMAND
                        InputField.COMMAND -> InputField.TEST
                        InputField.TEST -> InputField.TRIGGER
                    }
                    val nextValue = when (nextField) {
                        InputField.TRIGGER -> selectedMacro.trigger
                        InputField.COMMAND -> selectedMacro.command
                        InputField.TEST -> testInputFor(selectedIndex)
                    }
                    focusField(nextField, nextValue)
                    return true
                }
                GLFW.GLFW_KEY_ENTER,
                GLFW.GLFW_KEY_KP_ENTER -> {
                    clearFieldFocus()
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    clearFieldFocus()
                    return true
                }
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        return super.keyPressed(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        val activeField = focusedField ?: return super.charTyped(characterEvent)
        val macro = ChatMacros.listMacros().getOrNull(selectedIndex) ?: return true
        val codepoint = characterEvent.codepoint()
        if (codepoint !in 32..126) return true

        val current = currentFieldValue(macro, activeField)
        val (nextText, nextCaret) = insertAtCaret(current, codepoint.toChar().toString())
        updateFocusedField(activeField, nextText)
        caretIndex = nextCaret
        selectionAnchorIndex = null
        return true
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    private fun renderListPanel(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        listPanel: Rect,
        listRows: Rect,
        macros: List<ChatMacros.MacroView>
    ) {
        GuiUtils.renderRoundedRectangle(guiGraphics, listPanel.x, listPanel.y, listPanel.width, listPanel.height, 0, Color(37, 10, 16, 218).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, listPanel.x, listPanel.y, listPanel.width, listPanel.height, 0, 1, Color(204, 84, 116, 186).rgb)

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Macros (${macros.size})",
            (listPanel.x + 8).toFloat(),
            (listPanel.y + 6).toFloat(),
            10.5f,
            Color(246, 227, 233, 255).rgb
        )

        if (macros.isEmpty()) {
            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                "Click Add New to create your first macro",
                (listRows.x + 8).toFloat(),
                (listRows.y + 10).toFloat(),
                TEXT_SIZE,
                Color(193, 152, 165, 255).rgb
            )
            return
        }

        val visibleRows = visibleRows(listRows.height)
        val start = listScrollRows
        val endExclusive = minOf(macros.size, start + visibleRows)

        for (index in start until endExclusive) {
            val rowY = listRows.y + (index - start) * ROW_HEIGHT
            val rowRect = Rect(listRows.x, rowY, listRows.width, ROW_HEIGHT - 2)
            val selected = index == selectedIndex
            val macro = macros[index]
            val inactive = !macro.active

            val rowColor = when {
                selected && inactive -> Color(50, 50, 50, 220).rgb
                selected -> Color(66, 10, 23, 228).rgb
                inactive -> Color(39, 39, 39, 178).rgb
                else -> Color(46, 9, 17, 184).rgb
            }
            val rowBorder = when {
                selected && inactive -> Color(145, 145, 145, 210).rgb
                selected -> Color(204, 84, 116, 234).rgb
                inactive -> Color(110, 110, 110, 170).rgb
                else -> Color(137, 55, 77, 170).rgb
            }
            GuiUtils.renderRoundedRectangle(guiGraphics, rowRect.x, rowRect.y, rowRect.width, rowRect.height, 2, rowColor)
            GuiUtils.renderRoundedOutline(guiGraphics, rowRect.x, rowRect.y, rowRect.width, rowRect.height, 2, 1, rowBorder)

            val triggerColor = if (inactive) Color(176, 176, 176, 255).rgb else Color(246, 227, 233, 255).rgb
            val commandColor = if (inactive) Color(145, 145, 145, 255).rgb else Color(193, 152, 165, 255).rgb
            val previewLineGap = 8
            val previewBlockHeight = 16
            val previewStartY = rowRect.y + ((rowRect.height - previewBlockHeight) / 2)

            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                fitRowText(macro.trigger.ifBlank { "<empty trigger>" }),
                (rowRect.x + 6).toFloat(),
                previewStartY.toFloat(),
                TEXT_SIZE,
                triggerColor
            )

            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                fitRowText(macro.command.ifBlank { "<empty command>" }),
                (rowRect.x + 6).toFloat(),
                (previewStartY + previewLineGap).toFloat(),
                TEXT_SIZE,
                commandColor
            )
        }
    }

    private fun renderDetailPanel(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        detailPanel: Rect,
        macros: List<ChatMacros.MacroView>,
        mouseX: Double,
        mouseY: Double
    ) {
        GuiUtils.renderRoundedRectangle(guiGraphics, detailPanel.x, detailPanel.y, detailPanel.width, detailPanel.height, 0, Color(32, 9, 15, 208).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, detailPanel.x, detailPanel.y, detailPanel.width, detailPanel.height, 0, 1, Color(204, 84, 116, 186).rgb)

        val addRect = addButtonRect(detailPanel)
        val deleteRect = deleteButtonRect(detailPanel)
        val toggleRect = toggleButtonRect(detailPanel)

        renderButton(guiGraphics, sw, sh, scale, addRect, "Add New", enabled = true, hovered = addRect.contains(mouseX, mouseY))

        val selectedMacro = macros.getOrNull(selectedIndex)
        val hasSelection = selectedMacro != null

        renderButton(
            guiGraphics,
            sw,
            sh,
            scale,
            deleteRect,
            "Delete",
            enabled = hasSelection,
            hovered = hasSelection && deleteRect.contains(mouseX, mouseY)
        )

        val toggleLabel = if (selectedMacro?.active == true) "Deactivate" else "Reactivate"
        renderButton(
            guiGraphics,
            sw,
            sh,
            scale,
            toggleRect,
            toggleLabel,
            enabled = hasSelection,
            hovered = hasSelection && toggleRect.contains(mouseX, mouseY)
        )

        if (!hasSelection) {
            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                "No macro selected",
                (detailPanel.x + 12).toFloat(),
                (detailPanel.y + 56).toFloat(),
                11f,
                Color(246, 227, 233, 255).rgb
            )
            return
        }

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Trigger Regex",
            (detailPanel.x + 12).toFloat(),
            (detailPanel.y + 46).toFloat(),
            10f,
            Color(246, 227, 233, 255).rgb
        )
        val triggerRect = triggerFieldRect(detailPanel)
        renderField(
            guiGraphics,
            sw,
            sh,
            scale,
            triggerRect,
            selectedMacro.trigger,
            placeholder = "example: .*You have slain.*",
            focused = focusedField == InputField.TRIGGER,
            field = InputField.TRIGGER
        )

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Command",
            (detailPanel.x + 12).toFloat(),
            (detailPanel.y + 88).toFloat(),
            10f,
            Color(246, 227, 233, 255).rgb
        )
        val commandRect = commandFieldRect(detailPanel)
        renderField(
            guiGraphics,
            sw,
            sh,
            scale,
            commandRect,
            selectedMacro.command,
            placeholder = "example: p warp",
            focused = focusedField == InputField.COMMAND,
            field = InputField.COMMAND
        )

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Test Text",
            (detailPanel.x + 12).toFloat(),
            (detailPanel.y + 130).toFloat(),
            10f,
            Color(246, 227, 233, 255).rgb
        )
        val testRect = testFieldRect(detailPanel)
        renderField(
            guiGraphics,
            sw,
            sh,
            scale,
            testRect,
            testInputFor(selectedIndex),
            placeholder = "paste a chat line here to test",
            focused = focusedField == InputField.TEST,
            field = InputField.TEST
        )

        val (matchText, matchColor) = regexMatchStatus(selectedMacro.trigger, testInputFor(selectedIndex))
        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            "Result: $matchText",
            (detailPanel.x + 12).toFloat(),
            (detailPanel.y + 172).toFloat(),
            TEXT_SIZE,
            matchColor
        )
    }

    private fun renderButton(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        rect: Rect,
        label: String,
        enabled: Boolean,
        hovered: Boolean
    ) {
        val fill = when {
            !enabled -> Color(56, 23, 31, 170).rgb
            hovered -> Color(82, 18, 33, 226).rgb
            else -> Color(65, 14, 27, 212).rgb
        }
        val border = when {
            !enabled -> Color(116, 48, 66, 168).rgb
            hovered -> Color(234, 110, 146, 240).rgb
            else -> Color(204, 84, 116, 224).rgb
        }
        val text = if (enabled) Color(246, 227, 233, 255).rgb else Color(155, 115, 128, 255).rgb

        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, fill)
        GuiUtils.renderRoundedOutline(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, 1, border)
        drawCenteredText(
            guiGraphics,
            sw,
            sh,
            scale,
            label,
            (rect.x + rect.width / 2).toFloat(),
            (rect.y + 5).toFloat(),
            9f,
            text
        )
    }

    private fun renderField(
        guiGraphics: GuiGraphicsExtractor,
        sw: Int,
        sh: Int,
        scale: Float,
        rect: Rect,
        value: String,
        placeholder: String,
        focused: Boolean,
        field: InputField
    ) {
        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, Color(31, 11, 17, 214).rgb)
        GuiUtils.renderRoundedOutline(
            guiGraphics,
            rect.x,
            rect.y,
            rect.width,
            rect.height,
            2,
            1,
            if (focused) Color(234, 110, 146, 240).rgb else Color(147, 60, 83, 190).rgb
        )

        val textX = rect.x + 6
        val textY = rect.y + 6
        val textWidthMax = (rect.width - 12).coerceAtLeast(1).toFloat()
        val showCaret = focused && (tickCounter / 8) % 2 == 0

        if (value.isEmpty() && !focused) {
            drawText(
                guiGraphics,
                sw,
                sh,
                scale,
                placeholder,
                textX.toFloat(),
                textY.toFloat(),
                TEXT_SIZE,
                Color(155, 115, 128, 255).rgb
            )
            return
        }

        val safeCaret = caretIndex.coerceIn(0, value.length)
        val viewport = if (focused && focusedField == field) {
            viewportForCaret(value, textWidthMax, safeCaret, scale)
        } else {
            viewportFromStart(value, textWidthMax, scale)
        }

        if (focused && focusedField == field) {
            selectionBounds(value)?.let { (selStartRaw, selEndRaw) ->
                val selStart = maxOf(selStartRaw, viewport.startIndex)
                val selEnd = minOf(selEndRaw, viewport.endIndex)
                if (selEnd > selStart) {
                    val beforeText = value.substring(viewport.startIndex, selStart)
                    val selectedText = value.substring(selStart, selEnd)
                    val selectionX = textX + measureTextWidth(beforeText, scale)
                    val selectionWidth = measureTextWidth(selectedText, scale).coerceAtLeast(1f)
                    GuiUtils.renderRectangle(
                        guiGraphics,
                        selectionX.toInt(),
                        rect.y + 3,
                        selectionWidth.toInt(),
                        rect.height - 6,
                        Color(129, 79, 98, 160).rgb
                    )
                }
            }
        }

        val textColor = if (value.isEmpty() && !focused) {
            Color(155, 115, 128, 255).rgb
        } else {
            Color(246, 227, 233, 255).rgb
        }

        drawText(
            guiGraphics,
            sw,
            sh,
            scale,
            viewport.visibleText,
            textX.toFloat(),
            textY.toFloat(),
            TEXT_SIZE,
            textColor
        )

        if (focused && focusedField == field && showCaret) {
            val caretInViewport = safeCaret.coerceIn(viewport.startIndex, viewport.endIndex)
            val prefix = value.substring(viewport.startIndex, caretInViewport)
            val caretX = textX + measureTextWidth(prefix, scale)
            GuiUtils.renderRectangle(
                guiGraphics,
                caretX.toInt(),
                rect.y + 4,
                1,
                rect.height - 8,
                Color(246, 227, 233, 255).rgb
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
                y = y * scale,
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
                y = y * scale,
                size = size * scale,
                color = color,
                font = font
            )
        }
    }

    private fun panelRect(): Rect {
        val proposedWidth = (width * PANEL_WIDTH_RATIO).toInt()
        val proposedHeight = (height * PANEL_HEIGHT_RATIO).toInt()
        val panelWidth = proposedWidth.coerceIn(PANEL_MIN_WIDTH.coerceAtMost(width - 16), width - 16)
        val panelHeight = proposedHeight.coerceIn(PANEL_MIN_HEIGHT.coerceAtMost(height - 16), height - 16)
        val x = width / 2 - panelWidth / 2
        val y = height / 2 - panelHeight / 2
        return Rect(x, y, panelWidth, panelHeight)
    }

    private fun listPanelRect(panel: Rect): Rect {
        return Rect(
            x = panel.x + PANEL_PADDING,
            y = panel.y + PANEL_PADDING + 16,
            width = LIST_WIDTH,
            height = panel.height - PANEL_PADDING * 2 - 16
        )
    }

    private fun detailPanelRect(panel: Rect): Rect {
        val list = listPanelRect(panel)
        val gap = 10
        return Rect(
            x = list.x + list.width + gap,
            y = list.y,
            width = panel.x + panel.width - PANEL_PADDING - (list.x + list.width + gap),
            height = list.height
        )
    }

    private fun listRowsRect(listPanel: Rect): Rect {
        return Rect(
            x = listPanel.x + 4,
            y = listPanel.y + LIST_HEADER_HEIGHT + 2,
            width = listPanel.width - 8,
            height = listPanel.height - LIST_HEADER_HEIGHT - 6
        )
    }

    private fun addButtonRect(detailPanel: Rect): Rect {
        return Rect(
            x = detailPanel.x + 12,
            y = detailPanel.y + 8,
            width = DETAIL_BUTTON_WIDTH,
            height = DETAIL_BUTTON_HEIGHT
        )
    }

    private fun deleteButtonRect(detailPanel: Rect): Rect {
        return Rect(
            x = addButtonRect(detailPanel).x + DETAIL_BUTTON_WIDTH + DETAIL_BUTTON_GAP,
            y = detailPanel.y + 8,
            width = DETAIL_BUTTON_WIDTH,
            height = DETAIL_BUTTON_HEIGHT
        )
    }

    private fun toggleButtonRect(detailPanel: Rect): Rect {
        return Rect(
            x = deleteButtonRect(detailPanel).x + DETAIL_BUTTON_WIDTH + DETAIL_BUTTON_GAP,
            y = detailPanel.y + 8,
            width = DETAIL_BUTTON_WIDTH,
            height = DETAIL_BUTTON_HEIGHT
        )
    }

    private fun triggerFieldRect(detailPanel: Rect): Rect {
        return Rect(
            x = detailPanel.x + 12,
            y = detailPanel.y + 60,
            width = detailPanel.width - 24,
            height = FIELD_HEIGHT
        )
    }

    private fun commandFieldRect(detailPanel: Rect): Rect {
        return Rect(
            x = detailPanel.x + 12,
            y = detailPanel.y + 102,
            width = detailPanel.width - 24,
            height = FIELD_HEIGHT
        )
    }

    private fun testFieldRect(detailPanel: Rect): Rect {
        return Rect(
            x = detailPanel.x + 12,
            y = detailPanel.y + 144,
            width = detailPanel.width - 24,
            height = FIELD_HEIGHT
        )
    }

    private fun clampSelectedIndex(size: Int) {
        selectedIndex = when {
            size <= 0 -> {
                clearFieldFocus()
                -1
            }
            selectedIndex < 0 -> 0
            selectedIndex >= size -> size - 1
            else -> selectedIndex
        }
    }

    private fun visibleRows(rowsHeight: Int): Int {
        return (rowsHeight / ROW_HEIGHT).coerceAtLeast(1)
    }

    private fun clampListScroll(size: Int, visibleRows: Int) {
        val maxScroll = (size - visibleRows).coerceAtLeast(0)
        listScrollRows = listScrollRows.coerceIn(0, maxScroll)
    }

    private fun ensureSelectionVisible(visibleRows: Int) {
        if (selectedIndex < 0) return
        if (selectedIndex < listScrollRows) {
            listScrollRows = selectedIndex
        } else if (selectedIndex >= listScrollRows + visibleRows) {
            listScrollRows = selectedIndex - visibleRows + 1
        }
    }

    private fun listIndexAt(mouseY: Double, listRows: Rect, size: Int): Int {
        if (size <= 0) return -1
        val relative = (mouseY - listRows.y).toInt()
        if (relative < 0) return -1
        val rowOffset = relative / ROW_HEIGHT
        val index = listScrollRows + rowOffset
        return if (index in 0 until size) index else -1
    }

    private fun fitRowText(raw: String): String {
        return if (raw.length > 34) "${raw.take(34)}..." else raw
    }

    private fun focusField(field: InputField, currentValue: String) {
        focusedField = field
        caretIndex = currentValue.length.coerceIn(0, currentValue.length)
        selectionAnchorIndex = null
    }

    private fun focusFieldWithClickCaret(field: InputField, currentValue: String, rect: Rect, mouseX: Double) {
        focusedField = field
        val scale = minecraft.window.guiScale.toFloat()
        val localX = (mouseX - (rect.x + 6)).coerceAtLeast(0.0).toFloat()
        val maxWidth = (rect.width - 12).coerceAtLeast(1).toFloat()
        val viewport = viewportFromStart(currentValue, maxWidth, scale)
        val clickInViewport = localX.coerceIn(0f, maxWidth)
        caretIndex = caretIndexForLocalX(currentValue, viewport.startIndex, viewport.endIndex, clickInViewport, scale)
        selectionAnchorIndex = null
    }

    private fun clearFieldFocus() {
        focusedField = null
        selectionAnchorIndex = null
        caretIndex = 0
    }

    private fun measureTextWidth(text: String, scale: Float): Float {
        if (text.isEmpty()) return 0f
        val font = ClickGuiFeature.selectedFont
        val widthPx = NVGRenderer.textWidth(text, TEXT_SIZE * scale, font)
        return widthPx / scale
    }

    private fun viewportFromStart(value: String, maxWidth: Float, scale: Float): FieldViewport {
        if (value.isEmpty()) return FieldViewport(0, 0, "")
        var end = 0
        while (end < value.length) {
            val candidate = value.substring(0, end + 1)
            if (measureTextWidth(candidate, scale) > maxWidth) break
            end++
        }
        return FieldViewport(0, end, value.substring(0, end))
    }

    private fun viewportForCaret(value: String, maxWidth: Float, caret: Int, scale: Float): FieldViewport {
        if (value.isEmpty()) return FieldViewport(0, 0, "")
        val safeCaret = caret.coerceIn(0, value.length)
        var start = 0
        while (start < safeCaret) {
            if (measureTextWidth(value.substring(start, safeCaret), scale) <= maxWidth) break
            start++
        }
        var end = safeCaret
        while (end < value.length) {
            val candidate = value.substring(start, end + 1)
            if (measureTextWidth(candidate, scale) > maxWidth) break
            end++
        }
        return FieldViewport(start, end, value.substring(start, end))
    }

    private fun caretIndexForLocalX(
        value: String,
        startIndex: Int,
        endIndex: Int,
        localX: Float,
        scale: Float
    ): Int {
        var bestIndex = startIndex
        var bestDistance = Float.MAX_VALUE
        for (idx in startIndex..endIndex) {
            val prefix = value.substring(startIndex, idx)
            val width = measureTextWidth(prefix, scale)
            val distance = abs(width - localX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = idx
            }
        }
        return bestIndex
    }

    private fun selectionBounds(value: String): Pair<Int, Int>? {
        val anchor = selectionAnchorIndex ?: return null
        val safeAnchor = anchor.coerceIn(0, value.length)
        val safeCaret = caretIndex.coerceIn(0, value.length)
        if (safeAnchor == safeCaret) return null
        return if (safeAnchor < safeCaret) safeAnchor to safeCaret else safeCaret to safeAnchor
    }

    private fun insertAtCaret(value: String, text: String): Pair<String, Int> {
        val insertion = sanitizeClipboardText(text)
        if (insertion.isEmpty()) return value to caretIndex.coerceIn(0, value.length)

        val selection = selectionBounds(value)
        val replaceStart = selection?.first ?: caretIndex.coerceIn(0, value.length)
        val replaceEnd = selection?.second ?: replaceStart
        val prefix = value.substring(0, replaceStart)
        val suffix = value.substring(replaceEnd)
        val maxInsert = (FIELD_TEXT_MAX_LENGTH - prefix.length - suffix.length).coerceAtLeast(0)
        val clippedInsert = insertion.take(maxInsert)
        val nextValue = prefix + clippedInsert + suffix
        val nextCaret = prefix.length + clippedInsert.length
        return nextValue to nextCaret
    }

    private fun backspaceAtCaret(value: String, deleteWord: Boolean): Pair<String, Int> {
        selectionBounds(value)?.let { (start, end) ->
            return value.removeRange(start, end) to start
        }

        val safeCaret = caretIndex.coerceIn(0, value.length)
        if (safeCaret <= 0) return value to 0

        if (deleteWord) {
            var start = safeCaret
            while (start > 0 && value[start - 1].isWhitespace()) start--
            while (start > 0 && !value[start - 1].isWhitespace()) start--
            return value.removeRange(start, safeCaret) to start
        }

        return value.removeRange(safeCaret - 1, safeCaret) to (safeCaret - 1)
    }

    private fun deleteAtCaret(value: String): Pair<String, Int> {
        selectionBounds(value)?.let { (start, end) ->
            return value.removeRange(start, end) to start
        }

        val safeCaret = caretIndex.coerceIn(0, value.length)
        if (safeCaret >= value.length) return value to safeCaret
        return value.removeRange(safeCaret, safeCaret + 1) to safeCaret
    }

    private fun isControlKeyDown(): Boolean {
        return InputConstants.isKeyDown(minecraft.window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
            InputConstants.isKeyDown(minecraft.window, GLFW.GLFW_KEY_RIGHT_CONTROL)
    }

    private fun readClipboardText(): String {
        val raw = runCatching {
            GLFW.glfwGetClipboardString(minecraft.window.handle())
        }.getOrNull() ?: return ""
        return sanitizeClipboardText(raw)
    }

    private fun writeClipboardText(text: String) {
        if (text.isEmpty()) return
        runCatching {
            GLFW.glfwSetClipboardString(minecraft.window.handle(), text)
        }
    }

    private fun sanitizeClipboardText(raw: String): String {
        return raw
            .replace('\r', ' ')
            .replace('\n', ' ')
            .filter { it.code in 32..126 }
    }

    private fun regexMatchStatus(trigger: String, testText: String): Pair<String, Int> {
        if (trigger.isBlank()) {
            return Pair("Enter a trigger regex", Color(155, 115, 128, 255).rgb)
        }
        if (testText.isBlank()) {
            return Pair("Enter test text", Color(155, 115, 128, 255).rgb)
        }

        val matches = runCatching { Regex(trigger).containsMatchIn(testText) }
        val outcome = matches.getOrNull()
        return when {
            outcome == true -> Pair("Match", Color(131, 220, 140, 255).rgb)
            outcome == false -> Pair("No match", Color(193, 152, 165, 255).rgb)
            else -> Pair("Invalid regex", Color(226, 163, 130, 255).rgb)
        }
    }

    private fun currentFieldValue(macro: ChatMacros.MacroView, field: InputField): String {
        return when (field) {
            InputField.TRIGGER -> macro.trigger
            InputField.COMMAND -> macro.command
            InputField.TEST -> testInputFor(selectedIndex)
        }
    }

    private fun updateFocusedField(field: InputField, nextValue: String) {
        val limited = nextValue.take(FIELD_TEXT_MAX_LENGTH)
        when (field) {
            InputField.TRIGGER -> ChatMacros.setTrigger(selectedIndex, limited)
            InputField.COMMAND -> ChatMacros.setCommand(selectedIndex, limited)
            InputField.TEST -> if (selectedIndex >= 0) testInputByIndex[selectedIndex] = limited
        }
    }

    private fun testInputFor(index: Int): String {
        if (index < 0) return ""
        return testInputByIndex[index] ?: ""
    }

    private fun pruneTestInputs(size: Int) {
        testInputByIndex.keys.removeIf { it < 0 || it >= size }
    }

    private fun shiftTestInputsAfterDeletion(deletedIndex: Int) {
        if (deletedIndex < 0) return
        val shifted = mutableMapOf<Int, String>()
        testInputByIndex.forEach { (index, value) ->
            when {
                index < deletedIndex -> shifted[index] = value
                index > deletedIndex -> shifted[index - 1] = value
            }
        }
        testInputByIndex.clear()
        testInputByIndex.putAll(shifted)
    }
}
