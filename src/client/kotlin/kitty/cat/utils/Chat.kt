package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object Chat {
    const val PREFIX = "[KC] "

    fun send(component: Component) {
        try {
            mc.player?.sendSystemMessage(Component.literal(PREFIX).append(component))
        } catch (e: Exception) {}
    }

    fun send(vararg parts: Any?) {
        val message = Component.empty()
        parts.forEach { part ->
            message.append(
                when (part) {
                    is Component -> part
                    null -> Component.literal("null")
                    else -> Component.literal(part.toString())
                }
            )
        }
        send(message)
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

        player.sendSystemMessage(text)
    }

    data class Clickable(
        val text: String,
        val action: ClickEvent
    )
}
