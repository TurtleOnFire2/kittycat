package kitty.cat.features.debug

import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.KeybindSetting

object ExampleFeature : Feature("Example Feature", "", Categories.Category.DEBUG) {
    val toggle = booleanSetting("Boolean Switch", true)
    val number = numberSetting("Number Setting", min = 0.0, max = 100.0, defaultValue = 50.0, unit = "k", step = 1.0)
    val modeSingle = selectorSetting(
        name = "Mode Single",
        options = listOf("None", "One", "Two"),
        defaultSelected = listOf("None"),
        allowMultiple = false
    )
    val modeMultiple = selectorSetting(
        name = "Mode Multiple",
        options = listOf("One", "Two", "Three"),
        defaultSelected = listOf("One", "Three"),
        allowMultiple = true
    )
    val color = colorSetting("Color", red = 140, green = 200, blue = 255, alpha = 200)
    val action = actionSetting("Action") {
        //Code Block
    }
    val keybind = keybindSetting("Keybind", KeybindSetting.UNBOUND)
}