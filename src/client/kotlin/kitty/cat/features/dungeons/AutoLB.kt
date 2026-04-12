package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BowItem

object AutoLB : Feature("Auto LB", "", Categories.Category.DUNGEONS) {

    val ticks = numberSetting("Ticks", 1.0, 20.0, 11.0, "t", 1.0)
    val rechargeDelay = numberSetting("Recharge delay", 1.0, 5.0, 2.0, "t", 1.0)

    var useTime = 0
    var delay = -1.0

    var p3 = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled || p3) return@register

            if (mc.player == null) return@register

            delay--

            if (!mc.player!!.mainHandItem.hoverName.string.lowercase().contains("last breath")) return@register

            if (useTime >= ticks.value) {
                mc.options.keyUse.isDown = false
                delay = rechargeDelay.value
            } else if (delay == 0.0) {
                mc.options.keyUse.isDown = true
            }
        }
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { _, _ ->
            p3 = false
        }

        ClientReceiveMessageEvents.GAME.register { message, _ ->
            val text = message.string
            if (text.contains("[BOSS] Goldor: Who dares trespass into my domain")) p3 = true
            if (text.contains("The Core entrance is opening!")) p3 = false
        }
    }

    fun serverTick() {
        if (mc.player?.isUsingItem == true) {
            useTime++
        } else {
            useTime = 0
        }
    }
}