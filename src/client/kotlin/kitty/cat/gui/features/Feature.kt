package kitty.cat.gui.features

import kitty.cat.config.ConfigManager
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.settings.ActionSetting
import kitty.cat.gui.features.settings.BooleanSetting
import kitty.cat.gui.features.settings.ColorSetting
import kitty.cat.gui.features.settings.KeybindSetting
import kitty.cat.gui.features.settings.NumberSetting
import kitty.cat.gui.features.settings.SelectorSetting
import kitty.cat.gui.features.settings.Setting
import kitty.cat.gui.features.settings.StringSetting

abstract class Feature {
    internal val name: String
    internal val description: String
    internal val category: Categories.Category
    var enabled: Boolean = false
        private set

    private val _settings = mutableListOf<Setting>()
    val settings: List<Setting>
        get() = _settings

    private val _booleanSettings = mutableListOf<BooleanSetting>()
    val booleanSettings: List<BooleanSetting>
        get() = _booleanSettings
    private val _keybindSettings = mutableListOf<KeybindSetting>()
    val keybindSettings: List<KeybindSetting>
        get() = _keybindSettings
    private val _numberSettings = mutableListOf<NumberSetting>()
    val numberSettings: List<NumberSetting>
        get() = _numberSettings
    private val _selectorSettings = mutableListOf<SelectorSetting>()
    val selectorSettings: List<SelectorSetting>
        get() = _selectorSettings
    private val _colorSettings = mutableListOf<ColorSetting>()
    val colorSettings: List<ColorSetting>
        get() = _colorSettings
    private val _actionSettings = mutableListOf<ActionSetting>()
    val actionSettings: List<ActionSetting>
        get() = _actionSettings
    private val _stringSettings = mutableListOf<StringSetting>()
    val stringSettings: List<StringSetting>
        get() = _stringSettings

    constructor(name: String, description: String, category: Categories.Category) {
        this.name = name
        this.description = description
        this.category = category
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        ConfigManager.markDirty()
        if (enabled) onEnable() else onDisable()
    }

    fun toggle() {
        setEnabled(!enabled)
    }

    protected open fun onEnable() {}
    protected open fun onDisable() {}
    open fun onKeybindPressed(setting: KeybindSetting) {
        toggle()
    }

    protected fun booleanSetting(name: String, defaultValue: Boolean = false, description: String = ""): BooleanSetting {
        val setting = BooleanSetting(name, defaultValue, description)
        _booleanSettings += setting
        _settings += setting
        return setting
    }

    protected fun keybindSetting(name: String, defaultKeyCode: Int = KeybindSetting.UNBOUND, description: String = ""): KeybindSetting {
        val setting = KeybindSetting(name = name, defaultKeyCode = defaultKeyCode, description = description)
        _keybindSettings += setting
        _settings += setting
        return setting
    }

    protected fun numberSetting(
        name: String,
        min: Double,
        max: Double,
        defaultValue: Double,
        unit: String = "",
        step: Double = 0.0,
        description: String = ""
    ): NumberSetting {
        val setting = NumberSetting(
            name = name,
            min = min,
            max = max,
            defaultValue = defaultValue,
            unit = unit,
            step = step,
            description = description
        )
        _numberSettings += setting
        _settings += setting
        return setting
    }

    protected fun selectorSetting(
        name: String,
        options: List<String>,
        defaultSelected: List<String> = emptyList(),
        allowMultiple: Boolean = false,
        description: String = ""
    ): SelectorSetting {
        val setting = SelectorSetting(
            name = name,
            options = options,
            defaultSelected = defaultSelected,
            allowMultiple = allowMultiple,
            description = description
        )
        _selectorSettings += setting
        _settings += setting
        return setting
    }

    protected fun colorSetting(
        name: String,
        red: Int = 255,
        green: Int = 255,
        blue: Int = 255,
        alpha: Int = 255,
        description: String = ""
    ): ColorSetting {
        val setting = ColorSetting(
            name = name,
            red = red,
            green = green,
            blue = blue,
            alpha = alpha,
            description = description
        )
        _colorSettings += setting
        _settings += setting
        return setting
    }

    protected fun actionSetting(name: String, description: String = "", action: () -> Unit): ActionSetting {
        val setting = ActionSetting(name = name, description = description, action = action)
        _actionSettings += setting
        _settings += setting
        return setting
    }

    protected fun stringSetting(
        name: String,
        defaultValue: String = "",
        maxLength: Int = 120,
        description: String = ""
    ): StringSetting {
        val setting = StringSetting(name = name, defaultValue = defaultValue, maxLength = maxLength, description = description)
        _stringSettings += setting
        _settings += setting
        return setting
    }
}
