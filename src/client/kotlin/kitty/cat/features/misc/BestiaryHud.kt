package kitty.cat.features.misc

import kitty.cat.features.huds.BestiaryHud
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature

object BestiaryHud: Feature("Bestiary Hud", "",Categories.Category.MISC) {
    val timeout = numberSetting("Timeout", 0.0, 60.0, 1.0, "m", 0.1)
    val showActiveOnly = booleanSetting("Show active only", false)
    val resetOnWorldChange = booleanSetting("Reset on world change", false)
    val action = actionSetting("Reset session") {
        BestiaryHud.resetSession()
    }
}