package kitty.cat.gui.bestiaryesp

import kitty.cat.features.visual.BestiaryESP
import kitty.cat.features.visual.ClickGui as ClickGuiFeature
import kitty.cat.render.nanovg.NVGPIPRenderer
import kitty.cat.render.nanovg.NVGRenderer
import kitty.cat.utils.GuiUtils
import kitty.cat.utils.allMobs
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color

class BestiaryESPScreen(private val parent: Screen?) : Screen(Component.literal("Bestiary ESP")) {

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(mx: Double, my: Double) =
            mx in x.toDouble()..(x + width).toDouble() && my in y.toDouble()..(y + height).toDouble()
    }

    private data class RowLayout(
        val espSwatch: Rect, val espBtn: Rect,
        val tracerSwatch: Rect, val tracerBtn: Rect
    )

    private data class PickerState(
        val beName: String, val isTracer: Boolean,
        var hue: Float, var sat: Float, var bri: Float, var alpha: Int
    )

    private companion object {
        const val PANEL_MIN_W = 400;  const val PANEL_MIN_H = 340
        const val PANEL_W_RATIO = 0.58f; const val PANEL_H_RATIO = 0.75f
        const val PAD = 12
        const val HDR_H = 22;  const val SEARCH_H = 18;  const val SEARCH_GAP = 6
        const val ROW_H = 28;  const val ROW_GAP = 2
        const val BTN_W = 56;  const val BTN_H = 18;  const val BTN_GAP = 6
        const val SW = 14;     const val SW_GAP = 4
        const val TITLE_SZ = 13f; const val TEXT_SZ = 9f
        const val LMB = 0
        const val PICKER_W = 120; const val PICKER_PAD = 6
        const val PICKER_SB_H = 60; const val PICKER_SL_H = 8; const val PICKER_STEP = 2
    }

    // Search state
    private var scrollRows = 0
    private var searchQuery = ""
    private var caretPos = 0
    private var searchFocused = false
    private var ticks = 0

    // Picker state
    private var picker: PickerState? = null
    private var dragSB = false; private var dragHue = false; private var dragAlpha = false

    private val allNames: List<String> = allMobs.map { it.beName }.distinct()
    private val filtered: List<String>
        get() = if (searchQuery.isEmpty()) allNames
                else allNames.filter { it.contains(searchQuery, ignoreCase = true) }

    override fun tick() { ticks++; super.tick() }

    // ── Render ───────────────────────────────────────────────────────────────

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val names = filtered
        val panel = panel(); val rows = rowsRect(panel)
        val vis   = visRows(rows.height)
        scrollRows = scrollRows.coerceIn(0, (names.size - vis).coerceAtLeast(0))

        val sw = minecraft.window.guiScaledWidth
        val sh = minecraft.window.guiScaledHeight
        val sc = minecraft.window.guiScale.toFloat()

        GuiUtils.renderRectangle(guiGraphics, 0, 0, width, height, Color(0, 0, 0, 140).rgb)
        GuiUtils.renderRoundedRectangle(guiGraphics, panel.x, panel.y, panel.width, panel.height, 0, Color(24, 9, 14, 226).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, panel.x, panel.y, panel.width, panel.height, 0, 1, Color(204, 84, 116, 238).rgb)
        txt(guiGraphics, sw, sh, sc, "Bestiary ESP", (panel.x + PAD).toFloat(), (panel.y + PAD - 1).toFloat(), TITLE_SZ, Color(246, 227, 233, 255).rgb)

        renderSearch(guiGraphics, sw, sh, sc, panel)

        guiGraphics.enableScissor(rows.x, rows.y, rows.x + rows.width, rows.y + rows.height)
        for (i in scrollRows until minOf(names.size, scrollRows + vis)) {
            val beName  = names[i]
            val rowY    = rows.y + (i - scrollRows) * (ROW_H + ROW_GAP)
            val rowRect = Rect(rows.x, rowY, rows.width, ROW_H)
            val layout  = rowLayout(rowRect)

            GuiUtils.renderRoundedRectangle(guiGraphics, rowRect.x, rowRect.y, rowRect.width, rowRect.height, 2, Color(37, 10, 16, 200).rgb)
            GuiUtils.renderRoundedOutline(guiGraphics, rowRect.x, rowRect.y, rowRect.width, rowRect.height, 2, 1, Color(137, 55, 77, 150).rgb)
            txt(guiGraphics, sw, sh, sc, beName, (rowRect.x + 8).toFloat(), (rowRect.y + (ROW_H - 9) / 2).toFloat(), TEXT_SZ, Color(246, 227, 233, 255).rgb)

            val espArgb    = BestiaryESP.espColors.getOrDefault(beName, 0xFFFFFFFF.toInt())
            val tracerArgb = BestiaryESP.tracerColors.getOrDefault(beName, 0xFFFFFFFF.toInt())
            val espPick    = picker?.let { it.beName == beName && !it.isTracer } == true
            val tracerPick = picker?.let { it.beName == beName && it.isTracer } == true

            swatch(guiGraphics, layout.espSwatch, espArgb, espPick)
            swatch(guiGraphics, layout.tracerSwatch, tracerArgb, tracerPick)

            val mx = mouseX.toDouble(); val my = mouseY.toDouble()
            renderBtn(guiGraphics, sw, sh, sc, layout.espBtn, "ESP",
                BestiaryESP.enabledMobs.any { it.beName == beName }, layout.espBtn.contains(mx, my))
            renderBtn(guiGraphics, sw, sh, sc, layout.tracerBtn, "Tracer",
                BestiaryESP.tracerMobs.any { it.beName == beName }, layout.tracerBtn.contains(mx, my))
        }
        guiGraphics.disableScissor()

        renderPicker(guiGraphics, sw, sh, sc, panel)
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks)
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (event.button() != LMB) return super.mouseClicked(event, doubled)
        val mx = event.x(); val my = event.y()
        val panel = panel()
        val prevPicker = picker

        // Picker hit-test first
        picker?.let { p ->
            val anchor = pickerAnchor(p, panel)
            if (anchor != null) {
                val pp = pickerRect(anchor, panel)
                if (pp.contains(mx, my)) {
                    val sbR = sbRect(pp); val hR = hueRect(pp); val aR = alphaRect(pp)
                    when {
                        sbR.contains(mx, my)    -> { dragSB = true;    updateSB(p, sbR, mx, my);    commitColor(p) }
                        hR.contains(mx, my)     -> { dragHue = true;   updateHue(p, hR, mx);        commitColor(p) }
                        aR.contains(mx, my)     -> { dragAlpha = true; updateAlpha(p, aR, mx);      commitColor(p) }
                    }
                    return true
                }
            }
            picker = null
        }

        // Search bar
        val sb = searchRect(panel)
        if (sb.contains(mx, my)) { searchFocused = true; caretPos = searchQuery.length; return true }
        if (searchFocused) searchFocused = false

        // Rows
        val rows = rowsRect(panel); val vis = visRows(rows.height)
        for (i in scrollRows until minOf(filtered.size, scrollRows + vis)) {
            val beName  = filtered[i]
            val rowY    = rows.y + (i - scrollRows) * (ROW_H + ROW_GAP)
            val layout  = rowLayout(Rect(rows.x, rowY, rows.width, ROW_H))
            when {
                layout.espSwatch.contains(mx, my)    -> { if (prevPicker == null || prevPicker.beName != beName || prevPicker.isTracer)  picker = openPicker(beName, false); return true }
                layout.tracerSwatch.contains(mx, my) -> { if (prevPicker == null || prevPicker.beName != beName || !prevPicker.isTracer) picker = openPicker(beName, true);  return true }
                layout.espBtn.contains(mx, my)       -> { BestiaryESP.toggleEsp(beName);      return true }
                layout.tracerBtn.contains(mx, my)    -> { BestiaryESP.toggleTracer(beName);   return true }
            }
        }
        return super.mouseClicked(event, doubled)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val p = picker ?: return super.mouseDragged(event, dx, dy)
        if (!dragSB && !dragHue && !dragAlpha) return super.mouseDragged(event, dx, dy)
        val anchor = pickerAnchor(p, panel()) ?: return super.mouseDragged(event, dx, dy)
        val pp = pickerRect(anchor, panel())
        val mx = event.x(); val my = event.y()
        when {
            dragSB    -> { updateSB(p, sbRect(pp), mx, my);  commitColor(p) }
            dragHue   -> { updateHue(p, hueRect(pp), mx);    commitColor(p) }
            dragAlpha -> { updateAlpha(p, alphaRect(pp), mx); commitColor(p) }
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragSB = false; dragHue = false; dragAlpha = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        picker = null
        val rows = rowsRect(panel())
        if (!rows.contains(mx, my)) return super.mouseScrolled(mx, my, h, v)
        val vis = visRows(rows.height)
        val max = (filtered.size - vis).coerceAtLeast(0)
        val prev = scrollRows
        when { v > 0.0 -> scrollRows = (scrollRows - 1).coerceAtLeast(0); v < 0.0 -> scrollRows = (scrollRows + 1).coerceAtMost(max) }
        return scrollRows != prev || super.mouseScrolled(mx, my, h, v)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        if (searchFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { searchFocused = false; return true }
                GLFW.GLFW_KEY_BACKSPACE -> { if (caretPos > 0) { searchQuery = searchQuery.removeRange(caretPos - 1, caretPos); caretPos--; scrollRows = 0 }; return true }
                GLFW.GLFW_KEY_DELETE    -> { if (caretPos < searchQuery.length) { searchQuery = searchQuery.removeRange(caretPos, caretPos + 1); scrollRows = 0 }; return true }
                GLFW.GLFW_KEY_LEFT      -> { caretPos = (caretPos - 1).coerceAtLeast(0); return true }
                GLFW.GLFW_KEY_RIGHT     -> { caretPos = (caretPos + 1).coerceAtMost(searchQuery.length); return true }
                GLFW.GLFW_KEY_HOME      -> { caretPos = 0; return true }
                GLFW.GLFW_KEY_END       -> { caretPos = searchQuery.length; return true }
            }
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!searchFocused) return super.charTyped(event)
        val cp = event.codepoint()
        if (cp !in 32..126) return true
        searchQuery = searchQuery.substring(0, caretPos) + cp.toChar() + searchQuery.substring(caretPos)
        caretPos++; scrollRows = 0
        return true
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }
    override fun isPauseScreen() = false

    // ── Color picker ──────────────────────────────────────────────────────────

    private fun openPicker(beName: String, isTracer: Boolean): PickerState? {
        val cur = picker
        if (cur != null && cur.beName == beName && cur.isTracer == isTracer) return null
        val argb = if (isTracer) BestiaryESP.tracerColors.getOrDefault(beName, 0xFFFFFFFF.toInt())
                   else          BestiaryESP.espColors.getOrDefault(beName, 0xFFFFFFFF.toInt())
        val r = (argb ushr 16) and 0xFF; val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF;            val a = (argb ushr 24) and 0xFF
        val hsb = Color.RGBtoHSB(r, g, b, null)
        return PickerState(beName, isTracer, hsb[0] * 360f, hsb[1], hsb[2], a)
    }

    private fun pickerAnchor(p: PickerState, panel: Rect): Rect? {
        val rows = rowsRect(panel); val vis = visRows(rows.height)
        val names = filtered
        for (i in scrollRows until minOf(names.size, scrollRows + vis)) {
            if (names[i] == p.beName) {
                val rowY = rows.y + (i - scrollRows) * (ROW_H + ROW_GAP)
                val layout = rowLayout(Rect(rows.x, rowY, rows.width, ROW_H))
                return if (p.isTracer) layout.tracerSwatch else layout.espSwatch
            }
        }
        return null
    }

    private fun pickerRect(anchor: Rect, panel: Rect): Rect {
        val ph = PICKER_PAD * 4 + PICKER_SB_H + PICKER_SL_H * 2
        val x  = (anchor.x + anchor.width / 2 - PICKER_W / 2).coerceIn(panel.x + 2, panel.x + panel.width - PICKER_W - 2)
        val yBelow = anchor.y + anchor.height + 4
        val y = if (yBelow + ph <= panel.y + panel.height - 4) yBelow else anchor.y - ph - 4
        return Rect(x, y.coerceIn(panel.y + 2, panel.y + panel.height - ph - 2), PICKER_W, ph)
    }

    private fun sbRect(pp: Rect)    = Rect(pp.x + PICKER_PAD, pp.y + PICKER_PAD, pp.width - PICKER_PAD * 2, PICKER_SB_H)
    private fun hueRect(pp: Rect)   = sbRect(pp).let { Rect(it.x, it.y + it.height + PICKER_PAD, it.width, PICKER_SL_H) }
    private fun alphaRect(pp: Rect) = hueRect(pp).let { Rect(it.x, it.y + it.height + PICKER_PAD, it.width, PICKER_SL_H) }

    private fun updateSB(p: PickerState, r: Rect, mx: Double, my: Double) {
        p.sat = ((mx - r.x) / (r.width - 1).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
        p.bri = 1f - ((my - r.y) / (r.height - 1).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }
    private fun updateHue(p: PickerState, r: Rect, mx: Double) {
        p.hue = ((mx - r.x) / (r.width - 1).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) * 360f
    }
    private fun updateAlpha(p: PickerState, r: Rect, mx: Double) {
        p.alpha = (((mx - r.x) / (r.width - 1).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun commitColor(p: PickerState) {
        val argb = hsv(p.hue, p.sat, p.bri, p.alpha)
        if (p.isTracer) BestiaryESP.setTracerColor(p.beName, argb)
        else            BestiaryESP.setEspColor(p.beName, argb)
    }

    private fun hsv(hue: Float, sat: Float, bri: Float, alpha: Int): Int {
        val rgb = Color.HSBtoRGB((hue / 360f).coerceIn(0f, 1f), sat.coerceIn(0f, 1f), bri.coerceIn(0f, 1f))
        return (alpha.coerceIn(0, 255) shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun renderPicker(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, sc: Float, panel: Rect) {
        val p      = picker ?: return
        val anchor = pickerAnchor(p, panel) ?: run { picker = null; return }
        val pp     = pickerRect(anchor, panel)
        val sbR    = sbRect(pp); val hR = hueRect(pp); val aR = alphaRect(pp)

        GuiUtils.renderRoundedRectangle(guiGraphics, pp.x, pp.y, pp.width, pp.height, 2, Color(28, 10, 16, 235).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, pp.x, pp.y, pp.width, pp.height, 2, 1, Color(204, 84, 116, 210).rgb)

        // SB box
        var ly = 0
        while (ly < sbR.height) {
            val bh = minOf(PICKER_STEP, sbR.height - ly)
            val bri = 1f - ly.toFloat() / (sbR.height - 1).coerceAtLeast(1)
            var lx = 0
            while (lx < sbR.width) {
                val bw = minOf(PICKER_STEP, sbR.width - lx)
                GuiUtils.renderRectangle(guiGraphics, sbR.x + lx, sbR.y + ly, bw, bh, hsv(p.hue, lx.toFloat() / (sbR.width - 1).coerceAtLeast(1), bri, 255))
                lx += PICKER_STEP
            }
            ly += PICKER_STEP
        }
        GuiUtils.renderRoundedOutline(guiGraphics, sbR.x, sbR.y, sbR.width, sbR.height, 1, 1, Color(137, 55, 77, 160).rgb)
        val mkX = (sbR.x + p.sat * (sbR.width - 1)).toInt().coerceIn(sbR.x, sbR.x + sbR.width - 1)
        val mkY = (sbR.y + (1f - p.bri) * (sbR.height - 1)).toInt().coerceIn(sbR.y, sbR.y + sbR.height - 1)
        GuiUtils.renderRoundedOutline(guiGraphics, mkX - 2, mkY - 2, 5, 5, 2, 1, Color(246, 227, 233, 255).rgb)

        // Hue bar
        var off = 0
        while (off < hR.width) {
            val sw2 = minOf(PICKER_STEP, hR.width - off)
            GuiUtils.renderRectangle(guiGraphics, hR.x + off, hR.y, sw2, hR.height, hsv(off.toFloat() / hR.width * 360f, 1f, 1f, 255))
            off += PICKER_STEP
        }
        GuiUtils.renderRoundedOutline(guiGraphics, hR.x, hR.y, hR.width, hR.height, 1, 1, Color(137, 55, 77, 160).rgb)
        val hkX = (hR.x + (p.hue / 360f) * (hR.width - 1)).toInt().coerceIn(hR.x, hR.x + hR.width - 1)
        GuiUtils.renderRectangle(guiGraphics, hkX, hR.y - 1, 1, hR.height + 2, Color(246, 227, 233, 255).rgb)

        // Alpha bar
        val rgb = hsv(p.hue, p.sat, p.bri, 255) and 0x00FFFFFF
        off = 0
        while (off < aR.width) {
            val sw2 = minOf(PICKER_STEP, aR.width - off)
            val a   = (off.toFloat() / aR.width * 255f).toInt().coerceIn(0, 255)
            GuiUtils.renderRectangle(guiGraphics, aR.x + off, aR.y, sw2, aR.height, (a shl 24) or rgb)
            off += PICKER_STEP
        }
        GuiUtils.renderRoundedOutline(guiGraphics, aR.x, aR.y, aR.width, aR.height, 1, 1, Color(137, 55, 77, 160).rgb)
        val akX = (aR.x + (p.alpha / 255f) * (aR.width - 1)).toInt().coerceIn(aR.x, aR.x + aR.width - 1)
        GuiUtils.renderRectangle(guiGraphics, akX, aR.y - 1, 1, aR.height + 2, Color(246, 227, 233, 255).rgb)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun swatch(guiGraphics: GuiGraphicsExtractor, rect: Rect, argb: Int, selected: Boolean) {
        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, argb)
        GuiUtils.renderRoundedOutline(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, 1,
            if (selected) Color(234, 110, 146, 240).rgb else Color(137, 55, 77, 180).rgb)
    }

    private fun renderBtn(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, sc: Float, rect: Rect, label: String, active: Boolean, hovered: Boolean) {
        val fill   = when { active && hovered -> Color(18, 72, 22, 226).rgb;  active -> Color(14, 55, 17, 212).rgb;  hovered -> Color(72, 18, 18, 200).rgb;  else -> Color(55, 14, 14, 170).rgb }
        val border = when { active && hovered -> Color(90, 220, 100, 240).rgb; active -> Color(60, 180, 70, 224).rgb; hovered -> Color(210, 65, 65, 200).rgb;  else -> Color(160, 45, 45, 140).rgb }
        val text   = when { active && hovered -> Color(185, 255, 190, 255).rgb; active -> Color(160, 240, 165, 255).rgb; hovered -> Color(255, 170, 170, 255).rgb; else -> Color(190, 115, 115, 255).rgb }
        GuiUtils.renderRoundedRectangle(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, fill)
        GuiUtils.renderRoundedOutline(guiGraphics, rect.x, rect.y, rect.width, rect.height, 2, 1, border)
        ctxt(guiGraphics, sw, sh, sc, label, (rect.x + rect.width / 2).toFloat(), (rect.y + 5).toFloat(), 9f, text)
    }

    private fun renderSearch(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, sc: Float, panel: Rect) {
        val r = searchRect(panel)
        GuiUtils.renderRoundedRectangle(guiGraphics, r.x, r.y, r.width, r.height, 2, Color(31, 11, 17, 214).rgb)
        GuiUtils.renderRoundedOutline(guiGraphics, r.x, r.y, r.width, r.height, 2, 1,
            if (searchFocused) Color(234, 110, 146, 240).rgb else Color(147, 60, 83, 190).rgb)
        val tx = (r.x + 6).toFloat(); val ty = (r.y + 5).toFloat()
        if (searchQuery.isEmpty() && !searchFocused) {
            txt(guiGraphics, sw, sh, sc, "Search...", tx, ty, TEXT_SZ, Color(155, 115, 128, 255).rgb); return
        }
        txt(guiGraphics, sw, sh, sc, searchQuery, tx, ty, TEXT_SZ, Color(246, 227, 233, 255).rgb)
        if (searchFocused && (ticks / 8) % 2 == 0) {
            val prefix = searchQuery.substring(0, caretPos.coerceIn(0, searchQuery.length))
            val cx = if (prefix.isEmpty()) 0f else NVGRenderer.textWidth(prefix, TEXT_SZ * sc, ClickGuiFeature.selectedFont) / sc
            GuiUtils.renderRectangle(guiGraphics, (tx + cx).toInt(), r.y + 4, 1, r.height - 8, Color(246, 227, 233, 255).rgb)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private fun panel(): Rect {
        val pw = (width * PANEL_W_RATIO).toInt().coerceIn(PANEL_MIN_W.coerceAtMost(width - 16), width - 16)
        val ph = (height * PANEL_H_RATIO).toInt().coerceIn(PANEL_MIN_H.coerceAtMost(height - 16), height - 16)
        return Rect(width / 2 - pw / 2, height / 2 - ph / 2, pw, ph)
    }

    private fun searchRect(p: Rect) = Rect(p.x + PAD, p.y + PAD + HDR_H - 2, p.width - PAD * 2, SEARCH_H)

    private fun rowsRect(p: Rect) = Rect(
        p.x + PAD, p.y + PAD + HDR_H + SEARCH_H + SEARCH_GAP,
        p.width - PAD * 2,
        p.height - PAD * 2 - HDR_H - SEARCH_H - SEARCH_GAP - 4
    )

    private fun visRows(h: Int) = (h / (ROW_H + ROW_GAP)).coerceAtLeast(1)

    private fun rowLayout(r: Rect): RowLayout {
        val btnY    = r.y + (ROW_H - BTN_H) / 2
        val swY     = r.y + (ROW_H - SW) / 2
        val right   = r.x + r.width
        val tBtnX   = right - BTN_GAP - BTN_W
        val tSwX    = tBtnX - SW_GAP - SW
        val eBtnX   = tSwX - BTN_GAP - BTN_W
        val eSwX    = eBtnX - SW_GAP - SW
        return RowLayout(
            espSwatch    = Rect(eSwX, swY, SW, SW),
            espBtn       = Rect(eBtnX, btnY, BTN_W, BTN_H),
            tracerSwatch = Rect(tSwX, swY, SW, SW),
            tracerBtn    = Rect(tBtnX, btnY, BTN_W, BTN_H)
        )
    }

    // ── Text ──────────────────────────────────────────────────────────────────

    private fun txt(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, sc: Float, text: String, x: Float, y: Float, size: Float, color: Int) {
        NVGPIPRenderer.draw(guiGraphics, 0, 0, sw, sh) {
            NVGRenderer.text(text = text, x = x * sc, y = y * sc, size = size * sc, color = color, font = ClickGuiFeature.selectedFont)
        }
    }

    private fun ctxt(guiGraphics: GuiGraphicsExtractor, sw: Int, sh: Int, sc: Float, text: String, cx: Float, y: Float, size: Float, color: Int) {
        NVGPIPRenderer.draw(guiGraphics, 0, 0, sw, sh) {
            NVGRenderer.textCentered(text = text, cx = cx * sc, y = y * sc, size = size * sc, color = color, font = ClickGuiFeature.selectedFont)
        }
    }
}
