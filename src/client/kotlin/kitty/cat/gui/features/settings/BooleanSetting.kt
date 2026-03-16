package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager

class BooleanSetting(
    override val name: String,
    defaultValue: Boolean
) : Setting {
    var value: Boolean = defaultValue
        set(newValue) {
            if (field == newValue) return
            field = newValue
            ConfigManager.markDirty()
        }

    fun toggle() {
        this.value = !this.value
    }
}
