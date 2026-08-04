package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.features.debug.ExampleFeature.number
import kitty.cat.gui.categories.Categories

object BackboneAlert : Feature("Backbone Alert", "", Categories.Category.KUUDRA) {
    val time = numberSetting("Time", 5.0, 40.0, 10.0, "t", 1.0)
}