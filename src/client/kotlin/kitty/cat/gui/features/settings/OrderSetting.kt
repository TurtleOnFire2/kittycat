package kitty.cat.gui.features.settings
import kitty.cat.config.ConfigManager
class OrderSetting(override val name: String, options: List<String>, defaultOrder: List<String> = options, override val description: String = "") : Setting {
    val options = options.distinct()
    private val defaultOrder: List<String>
    private val values = mutableListOf<String>()
    val order: List<String> get() = values.toList()
    init { require(this.options.isNotEmpty()); this.defaultOrder = normalized(defaultOrder); values += this.defaultOrder }
    fun move(from: Int, to: Int) { if (from !in values.indices || to !in values.indices || from == to) return; values.add(to, values.removeAt(from)); ConfigManager.markDirty() }
    fun setOrder(order: Collection<String>) { val next = normalized(order); if (values == next) return; values.clear(); values += next; ConfigManager.markDirty() }
    fun clearToDefault() = setOrder(defaultOrder)
    private fun normalized(order: Collection<String>): List<String> { val result = order.filter { it in options }.distinct().toMutableList(); result += options.filter { it !in result }; return result }
}
