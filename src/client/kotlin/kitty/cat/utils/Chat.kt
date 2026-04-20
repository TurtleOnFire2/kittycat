package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.network.chat.Component

object Chat {
    fun send(string: Any) {
        try {
            val msg = string as String
            mc.player?.displayClientMessage(Component.literal(msg), false)
        } catch (e: Exception) {}
    }
}