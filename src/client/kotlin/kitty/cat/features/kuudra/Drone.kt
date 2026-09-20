package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.features.settings.cheat
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule

object Drone: Feature("Drone", "", Categories.Category.KUUDRA) {

    init {
        cheat()
    }

}