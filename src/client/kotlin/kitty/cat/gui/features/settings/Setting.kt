package kitty.cat.gui.features.settings

interface Setting {
    val name: String
    val description: String
        get() = ""
}
