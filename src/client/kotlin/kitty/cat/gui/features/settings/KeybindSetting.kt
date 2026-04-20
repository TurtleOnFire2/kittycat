package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager
import org.lwjgl.glfw.GLFW

class KeybindSetting(
    override val name: String,
    defaultKeyCode: Int = UNBOUND,
    override val description: String = ""
) : Setting {
    var keyCode: Int = normalize(defaultKeyCode)
        private set

    fun setKeyCode(keyCode: Int) {
        val normalized = normalize(keyCode)
        if (this.keyCode == normalized) return
        this.keyCode = normalized
        ConfigManager.markDirty()
    }

    fun clear() {
        if (keyCode == UNBOUND) return
        keyCode = UNBOUND
        ConfigManager.markDirty()
    }

    fun displayValue(): String {
        if (keyCode == UNBOUND) return "None"
        return keyName(keyCode)
    }

    private fun normalize(raw: Int): Int {
        return if (raw < 0) UNBOUND else raw
    }

    private fun keyName(keyCode: Int): String {
        val localized = GLFW.glfwGetKeyName(keyCode, 0)
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
        if (localized != null) return localized

        return when {
            keyCode in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F25 -> "F${keyCode - GLFW.GLFW_KEY_F1 + 1}"
            else -> when (keyCode) {
                GLFW.GLFW_KEY_SPACE -> "Space"
                GLFW.GLFW_KEY_TAB -> "Tab"
                GLFW.GLFW_KEY_ENTER -> "Enter"
                GLFW.GLFW_KEY_KP_ENTER -> "Num Enter"
                GLFW.GLFW_KEY_BACKSPACE -> "Backspace"
                GLFW.GLFW_KEY_ESCAPE -> "Esc"
                GLFW.GLFW_KEY_LEFT_SHIFT -> "L Shift"
                GLFW.GLFW_KEY_RIGHT_SHIFT -> "R Shift"
                GLFW.GLFW_KEY_LEFT_CONTROL -> "L Ctrl"
                GLFW.GLFW_KEY_RIGHT_CONTROL -> "R Ctrl"
                GLFW.GLFW_KEY_LEFT_ALT -> "L Alt"
                GLFW.GLFW_KEY_RIGHT_ALT -> "R Alt"
                GLFW.GLFW_KEY_LEFT_SUPER -> "L Win"
                GLFW.GLFW_KEY_RIGHT_SUPER -> "R Win"
                GLFW.GLFW_KEY_UP -> "Up"
                GLFW.GLFW_KEY_DOWN -> "Down"
                GLFW.GLFW_KEY_LEFT -> "Left"
                GLFW.GLFW_KEY_RIGHT -> "Right"
                GLFW.GLFW_KEY_INSERT -> "Insert"
                GLFW.GLFW_KEY_DELETE -> "Delete"
                GLFW.GLFW_KEY_HOME -> "Home"
                GLFW.GLFW_KEY_END -> "End"
                GLFW.GLFW_KEY_PAGE_UP -> "Page Up"
                GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down"
                GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock"
                GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll Lock"
                GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock"
                GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen"
                GLFW.GLFW_KEY_PAUSE -> "Pause"
                GLFW.GLFW_KEY_KP_0 -> "Num 0"
                GLFW.GLFW_KEY_KP_1 -> "Num 1"
                GLFW.GLFW_KEY_KP_2 -> "Num 2"
                GLFW.GLFW_KEY_KP_3 -> "Num 3"
                GLFW.GLFW_KEY_KP_4 -> "Num 4"
                GLFW.GLFW_KEY_KP_5 -> "Num 5"
                GLFW.GLFW_KEY_KP_6 -> "Num 6"
                GLFW.GLFW_KEY_KP_7 -> "Num 7"
                GLFW.GLFW_KEY_KP_8 -> "Num 8"
                GLFW.GLFW_KEY_KP_9 -> "Num 9"
                GLFW.GLFW_KEY_KP_DECIMAL -> "Num ."
                GLFW.GLFW_KEY_KP_DIVIDE -> "Num /"
                GLFW.GLFW_KEY_KP_MULTIPLY -> "Num *"
                GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -"
                GLFW.GLFW_KEY_KP_ADD -> "Num +"
                else -> "Key $keyCode"
            }
        }
    }

    companion object {
        const val UNBOUND = GLFW.GLFW_KEY_UNKNOWN
    }
}
