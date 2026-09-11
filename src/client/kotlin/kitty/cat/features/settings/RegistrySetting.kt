package kitty.cat.features.settings

import kitty.cat.config.ConfigManager
import net.minecraft.core.Registry
import java.util.Locale

/** A single valid registry ID (or entry from a fixed list), with searchable suggestions. */
class RegistrySetting(
    override val name: String,
    defaultValue: String,
    allowedValues: List<String>,
    val placeholder: String = "Search entries...",
    override val description: String = ""
) : Setting {
    val options = allowedValues.map(String::trim).filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
    private val canonical = options.associateBy { it.lowercase(Locale.ROOT) }
    private val defaultValue = requireNotNull(exactMatch(defaultValue)) { "Default must be an allowed registry entry" }
    val maxLength = options.maxOf(String::length)
    var value: String = this.defaultValue
        private set

    constructor(
        name: String,
        defaultValue: String,
        registry: Registry<*>,
        placeholder: String = "Search entries...",
        description: String = ""
    ) : this(name, defaultValue, registry.keySet().map { it.toString() }.sorted(), placeholder, description)

    fun exactMatch(input: String): String? = canonical[input.trim().lowercase(Locale.ROOT)]

    fun filteredSuggestions(query: String): List<String> {
        val search = query.trim().lowercase(Locale.ROOT)
        return options.filter { it.lowercase(Locale.ROOT).contains(search) }.sortedBy {
            val entry = it.lowercase(Locale.ROOT)
            when {
                entry == search -> 0
                entry.startsWith(search) -> 1
                else -> 2
            }
        }
    }

    /** Invalid input leaves the last valid selection intact, including during config loading. */
    fun setValue(input: String): Boolean {
        val next = exactMatch(input) ?: return false
        if (next != value) {
            value = next
            ConfigManager.markDirty()
        }
        return true
    }

    fun clearToDefault() { setValue(defaultValue) }
}
