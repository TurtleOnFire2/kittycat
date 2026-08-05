package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories

object TinyMobs : Feature("Tiny kuudra mobs", "", Categories.Category.KUUDRA) {
    val scale = numberSetting("Scale", 0.0, 1.0, 0.4, "", 0.05)
}