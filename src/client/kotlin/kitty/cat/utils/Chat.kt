package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object Chat {
    const val PREFIX = "[KC] "

    fun send(string: Any) {
        try {
            val msg = string as String
            mc.player?.displayClientMessage(Component.literal(PREFIX).append(Component.literal(msg)), false)
        } catch (e: Exception) {}
    }

    fun sendWithClickable(message: String, vararg buttons: Clickable) {
        val player = Minecraft.getInstance().player ?: return
        var text = Component.literal(PREFIX).append(Component.literal(message))

        buttons.forEach {
            val buttonComponent = Component.literal(it.text)
                .withStyle(
                    Style.EMPTY
                        .withClickEvent(it.action)
                )

            text = text
                .append(Component.literal(" "))
                .append(buttonComponent)
        }

        player.displayClientMessage(text, false)
    }

    data class Clickable(
        val text: String,
        val action: ClickEvent
    )
}