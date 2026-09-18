package kitty.cat.features

import kitty.cat.config.BuildFlags
import kitty.cat.config.ConfigManager
import kitty.cat.gui.categories.Categories
import kitty.cat.features.settings.ActionSetting
import kitty.cat.features.settings.BooleanSetting
import kitty.cat.features.settings.ColorSetting
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.features.settings.NumberSetting
import kitty.cat.features.settings.OrderSetting
import kitty.cat.features.settings.RangeSetting
import kitty.cat.features.settings.SelectorSetting
import kitty.cat.features.settings.RegistrySetting
import net.minecraft.core.Registry
import kitty.cat.features.settings.Setting
import kitty.cat.features.settings.StringSetting
import kitty.cat.features.settings.isCheatOnly

abstract class Feature {
    internal val name: String
    internal val description: String
    internal val category: Categories.Category
    var enabled: Boolean = false
        private set

    var isCheatOnly: Boolean = false
        private set

    // Marks the whole feature (not just individual settings) as cheat-only.
    protected fun cheat() {
        isCheatOnly = true
    }

    private fun <T : Setting> List<T>.visible(): List<T> {
        if (isCheatOnly && !BuildFlags.CHEATS_ENABLED) return emptyList()
        return if (BuildFlags.CHEATS_ENABLED) this else filterNot { it.isCheatOnly }
    }

    private val _settings = mutableListOf<Setting>()
    val settings: List<Setting>
        get() = _settings.visible()

    private val _booleanSettings = mutableListOf<BooleanSetting>()
    val booleanSettings: List<BooleanSetting>
        get() = _booleanSettings.visible()
    private val _keybindSettings = mutableListOf<KeybindSetting>()
    val keybindSettings: List<KeybindSetting>
        get() = _keybindSettings.visible()
    private val _numberSettings = mutableListOf<NumberSetting>()
    val numberSettings: List<NumberSetting>
        get() = _numberSettings.visible()
    private val _rangeSettings = mutableListOf<RangeSetting>()
    val rangeSettings: List<RangeSetting>
        get() = _rangeSettings.visible()
    private val _selectorSettings = mutableListOf<SelectorSetting>()
    private val _registrySettings = mutableListOf<RegistrySetting>()
    val registrySettings: List<RegistrySetting> get() = _registrySettings.visible()
    val selectorSettings: List<SelectorSetting>
        get() = _selectorSettings.visible()
    private val _colorSettings = mutableListOf<ColorSetting>()
    val colorSettings: List<ColorSetting>
        get() = _colorSettings.visible()
    private val _actionSettings = mutableListOf<ActionSetting>()
    val actionSettings: List<ActionSetting>
        get() = _actionSettings.visible()
    private val _stringSettings = mutableListOf<StringSetting>()
    val stringSettings: List<StringSetting>
        get() = _stringSettings.visible()
    private val _orderSettings = mutableListOf<OrderSetting>()
    val orderSettings: List<OrderSetting> get() = _orderSettings.visible()

    constructor(name: String, description: String, category: Categories.Category) {
        this.name = name
        this.description = description
        this.category = category
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && isCheatOnly && !BuildFlags.CHEATS_ENABLED) return
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

    protected fun rangeSetting(
        name: String,
        min: Double,
        max: Double,
        defaultLowerValue: Double,
        defaultUpperValue: Double,
        unit: String = "",
        step: Double = 0.0,
        description: String = "",
        legacyLowerName: String? = null,
        legacyUpperName: String? = null
    ): RangeSetting {
        val setting = RangeSetting(
            name = name,
            min = min,
            max = max,
            defaultLowerValue = defaultLowerValue,
            defaultUpperValue = defaultUpperValue,
            unit = unit,
            step = step,
            description = description,
            legacyLowerName = legacyLowerName,
            legacyUpperName = legacyUpperName
        )
        _rangeSettings += setting
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

    protected fun registrySetting(
        name: String,
        defaultValue: String,
        allowedValues: List<String>,
        placeholder: String = "Search entries...",
        description: String = ""
    ): RegistrySetting = RegistrySetting(name, defaultValue, allowedValues, placeholder, description).also {
        _registrySettings += it
        _settings += it
    }

    protected fun registrySetting(
        name: String,
        defaultValue: String,
        registry: Registry<*>,
        placeholder: String = "Search entries...",
        description: String = ""
    ): RegistrySetting = registrySetting(name, defaultValue, registry.keySet().map { it.toString() }.sorted(), placeholder, description)

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
    protected fun orderSetting(name: String, options: List<String>, defaultOrder: List<String> = options, description: String = ""): OrderSetting {
        val setting = OrderSetting(name, options, defaultOrder, description)
        _orderSettings += setting
        _settings += setting
        return setting
    }
}

fun List<Feature>.visible(): List<Feature> =
    if (BuildFlags.CHEATS_ENABLED) this else filterNot { it.isCheatOnly }
