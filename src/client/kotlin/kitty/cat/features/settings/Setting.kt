package kitty.cat.features.settings

interface Setting {
    val name: String
    val description: String
        get() = ""
}
