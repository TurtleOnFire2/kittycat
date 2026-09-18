package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule

object Drone: Feature("Drone", "", Categories.Category.KUUDRA) {
    val freezeKeybind = keybindSetting("Toggle freeze")

    var freeze = false

    override fun onKeybindPressed(setting: KeybindSetting) {
        freeze = true
        Chat.send("Freezing: $freeze")
        schedule(200, true) {
            freeze = false
        }
    }

    fun handleMouseButton(button: Int) {
        if (button != 1 || !enabled) return
        freeze = false
        schedule(1) {
            freeze = true
        }
    }
}