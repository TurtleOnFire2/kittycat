package kitty.cat.gui.features.settings

class ActionSetting(
    override val name: String,
    private val action: () -> Unit
) : Setting {
    fun trigger() {
        action()
    }
}
