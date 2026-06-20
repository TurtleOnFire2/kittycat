package kitty.cat.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents

object LocationUtils {
    var inF7Boss = false

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            inF7Boss = false
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) {
            inF7Boss = true
        }
    }
}