package kitty.cat.features.visual

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.clickgui.ClickGui as ClickGuiScreen
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.KeybindSetting
import kitty.cat.render.nanovg.NVGFont
import kitty.cat.render.nanovg.NVGRenderer
import org.lwjgl.glfw.GLFW

object ClickGui : Feature("Click Gui", "", Categories.Category.VISUAL) {
    private const val DEFAULT_BASE_RED = 20
    private const val DEFAULT_BASE_GREEN = 8
    private const val DEFAULT_BASE_BLUE = 15
    private const val DEFAULT_BASE_ALPHA = 168

    private const val DEFAULT_ACCENT_RED = 204
    private const val DEFAULT_ACCENT_GREEN = 84
    private const val DEFAULT_ACCENT_BLUE = 116
    private const val DEFAULT_ACCENT_ALPHA = 220

    private val availableFontModes = NVGRenderer.availableFontModes()

    val keybind = keybindSetting("Open Gui", GLFW.GLFW_KEY_RIGHT_SHIFT)
    val fontMode = selectorSetting(
        name = "Font Mode",
        options = availableFontModes,
        defaultSelected = listOf(availableFontModes.first()),
        allowMultiple = false
    )
    val baseColor = colorSetting(
        name = "Base Color",
        red = DEFAULT_BASE_RED,
        green = DEFAULT_BASE_GREEN,
        blue = DEFAULT_BASE_BLUE,
        alpha = DEFAULT_BASE_ALPHA
    )
    val accentColor = colorSetting(
        name = "Accent Color",
        red = DEFAULT_ACCENT_RED,
        green = DEFAULT_ACCENT_GREEN,
        blue = DEFAULT_ACCENT_BLUE,
        alpha = DEFAULT_ACCENT_ALPHA
    )
    val resetColors = actionSetting("Reset Colors") {
        baseColor.setRgba(
            red = DEFAULT_BASE_RED,
            green = DEFAULT_BASE_GREEN,
            blue = DEFAULT_BASE_BLUE,
            alpha = DEFAULT_BASE_ALPHA
        )
        accentColor.setRgba(
            red = DEFAULT_ACCENT_RED,
            green = DEFAULT_ACCENT_GREEN,
            blue = DEFAULT_ACCENT_BLUE,
            alpha = DEFAULT_ACCENT_ALPHA
        )
    }

    val selectedFont: NVGFont
        get() = NVGRenderer.fontForMode(fontMode.selectedSingle)

    private fun canUseGuiScreens(): Boolean {
        return runCatching {
            mc.window.screenWidth > 0 && mc.window.screenHeight > 0
        }.getOrDefault(false)
    }

    fun openGui() {
        if (!enabled) {
            setEnabled(true)
            return
        }

        if (!canUseGuiScreens()) return

        if (mc.gui.screen() !is ClickGuiScreen) {
            mc.gui.setScreen(ClickGuiScreen())
        }
    }

    override fun onEnable() {
        if (!canUseGuiScreens()) {
            // Config load can call this before Minecraft has initialized the window/input stack.
            setEnabled(false)
            return
        }
        mc.gui.setScreen(ClickGuiScreen())
    }

    override fun onDisable() {
        if (!canUseGuiScreens()) return
        if (mc.gui.screen() is ClickGuiScreen) {
            mc.gui.setScreen(null)
        }
    }

    override fun onKeybindPressed(setting: KeybindSetting) {
        openGui()
    }
}
