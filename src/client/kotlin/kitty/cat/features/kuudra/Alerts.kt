package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories

object Alerts : Feature("Alerts", "", Categories.Category.KUUDRA) {

    var count = 0
    var text = ""

    fun handleChat(unformatted: String) {
        when (unformatted) {
            "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> {
                count = 177
                text = "Supplies:"
            }
            "[NPC] Elle: OMG! Great work collecting my supplies!" -> {
                count = 80
                text = "Build:"
            }
        }
    }

    fun serverTick() {
        count--
    }
}