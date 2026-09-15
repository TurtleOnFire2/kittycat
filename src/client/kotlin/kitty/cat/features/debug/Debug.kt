package kitty.cat.features.debug

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories

object Debug: Feature("Debug", "", Categories.Category.DEBUG)  {
    val sendLocation = booleanSetting("Send location")
}