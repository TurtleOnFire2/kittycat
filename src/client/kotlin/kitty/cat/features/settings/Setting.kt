package kitty.cat.features.settings

interface Setting {
    val name: String
    val description: String
        get() = ""
}

private val cheatOnlySettings: MutableSet<Setting> =
    java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

val Setting.isCheatOnly: Boolean
    get() = cheatOnlySettings.contains(this)

// Hidden from GUI and config save/load when BuildFlags.CHEATS_ENABLED is false.
fun <T : Setting> T.cheat(): T {
    cheatOnlySettings.add(this)
    return this
}
