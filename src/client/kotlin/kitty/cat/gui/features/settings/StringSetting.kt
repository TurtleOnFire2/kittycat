package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager

class StringSetting(
    override val name: String,
    defaultValue: String = "",
    val maxLength: Int = 120,
    override val description: String = ""
) : Setting {
    var value: String = sanitize(defaultValue)
        private set

    fun setValue(newValue: String) {
        val sanitized = sanitize(newValue)
        if (value == sanitized) return
        value = sanitized
        ConfigManager.markDirty()
    }

    fun setFromText(input: String): Boolean {
        setValue(input)
        return true
    }

    private fun sanitize(raw: String): String {
        return raw.take(maxLength.coerceAtLeast(1))
    }
}
