package kitty.cat.gui.features.settings

import kitty.cat.config.ConfigManager

class SelectorSetting(
    override val name: String,
    options: List<String>,
    defaultSelected: List<String> = emptyList(),
    val allowMultiple: Boolean = false
) : Setting {
    val options: List<String> = options.distinct()
    var dropdownOpen: Boolean = false

    private val defaultSelections: Set<String> = if (allowMultiple) {
        defaultSelected.filter { it in this.options }.toSet()
    } else {
        setOf(defaultSelected.firstOrNull { it in this.options } ?: this.options.first())
    }

    private val selectedOptions = linkedSetOf<String>()
    val selected: Set<String>
        get() = selectedOptions

    val selectedSingle: String
        get() = selectedOptions.firstOrNull() ?: options.first()

    init {
        require(this.options.isNotEmpty()) { "SelectorSetting requires at least one option" }

        if (allowMultiple) {
            defaultSelected.filter { it in this.options }.forEach { selectedOptions += it }
        } else {
            selectedOptions += defaultSelected.firstOrNull { it in this.options } ?: this.options.first()
        }
    }

    fun isSelected(option: String): Boolean = option in selectedOptions

    fun select(option: String) {
        if (option !in options) return

        var changed = false
        if (allowMultiple) {
            changed = selectedOptions.add(option)
        } else {
            if (selectedOptions.size == 1 && selectedOptions.firstOrNull() == option) return
            selectedOptions.clear()
            selectedOptions += option
            changed = true
        }

        if (changed) {
            ConfigManager.markDirty()
        }
    }

    fun deselect(option: String) {
        if (!allowMultiple) return
        if (option !in selectedOptions) return

        selectedOptions -= option
        ConfigManager.markDirty()
    }

    fun toggle(option: String) {
        if (option !in options) return

        if (allowMultiple) {
            if (option in selectedOptions) deselect(option) else select(option)
        } else {
            select(option)
        }
    }

    fun clearToDefault() {
        val before = selectedOptions.toSet()
        selectedOptions.clear()
        if (allowMultiple) {
            selectedOptions += defaultSelections
        } else {
            selectedOptions += defaultSelections.first()
        }
        if (before != selectedOptions) {
            ConfigManager.markDirty()
        }
    }

    fun setSelected(options: Collection<String>) {
        val validSelections = options.filter { it in this.options }
        val before = selectedOptions.toSet()

        selectedOptions.clear()
        if (allowMultiple) {
            selectedOptions += validSelections
        } else {
            selectedOptions += validSelections.firstOrNull() ?: this.options.first()
        }

        if (before != selectedOptions) {
            ConfigManager.markDirty()
        }
    }
}
