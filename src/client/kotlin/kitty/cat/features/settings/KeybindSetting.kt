package kitty.cat.features.settings

import kitty.cat.config.ConfigManager
import kitty.cat.compat.LegacyKeyCodes
import org.lwjgl.sdl.SDLKeyboard

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
        return if (raw == UNBOUND || raw < -1 || raw in 32..348) raw else UNBOUND
    }

    private fun keyName(keyCode: Int): String {
        val localized = SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(LegacyKeyCodes.toScanCode(keyCode), 0, false))
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
        if (localized != null) return localized

        return when {
            keyCode in 290..314 -> "F${keyCode - 290 + 1}"
            else -> when (keyCode) {
                32 -> "Space"
                258 -> "Tab"
                257 -> "Enter"
                335 -> "Num Enter"
                259 -> "Backspace"
                256 -> "Esc"
                340 -> "L Shift"
                344 -> "R Shift"
                341 -> "L Ctrl"
                345 -> "R Ctrl"
                342 -> "L Alt"
                346 -> "R Alt"
                343 -> "L Win"
                347 -> "R Win"
                265 -> "Up"
                264 -> "Down"
                263 -> "Left"
                262 -> "Right"
                260 -> "Insert"
                261 -> "Delete"
                268 -> "Home"
                269 -> "End"
                266 -> "Page Up"
                267 -> "Page Down"
                280 -> "Caps Lock"
                281 -> "Scroll Lock"
                282 -> "Num Lock"
                283 -> "Print Screen"
                284 -> "Pause"
                320 -> "Num 0"
                321 -> "Num 1"
                322 -> "Num 2"
                323 -> "Num 3"
                324 -> "Num 4"
                325 -> "Num 5"
                326 -> "Num 6"
                327 -> "Num 7"
                328 -> "Num 8"
                329 -> "Num 9"
                330 -> "Num ."
                331 -> "Num /"
                332 -> "Num *"
                333 -> "Num -"
                334 -> "Num +"
                else -> "Key $keyCode"
            }
        }
    }

    companion object {
        const val UNBOUND = -1
    }
}
