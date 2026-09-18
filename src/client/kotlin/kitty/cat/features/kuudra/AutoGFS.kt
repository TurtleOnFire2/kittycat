package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.LocationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.item.Items

object AutoGFS : Feature("Auto GFS", "", Categories.Category.KUUDRA) {

    init {
        cheat()
    }

    val enderPearls = booleanSetting("Ender pearls", true)
    val toxicArrowPoison = booleanSetting("Toxic arrow poison", true)
    val amountTap = numberSetting("Tap amount", 16.0, 32.0, 64.0, "", 1.0)
    val twilightArrowPoison = booleanSetting("Twilight arrow poison", true)
    val amountTwap = numberSetting("Twap amount", 2.0, 32.0, 8.0, "", 1.0)

    var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (ticks++ < 30 || !enderPearls.value || !enabled || !LocationManager.isInSkyblock) return@register

            val inv = client.player?.inventory ?: return@register

            val count = inv.find { it.item == Items.ENDER_PEARL }?.count ?: return@register

            if (count > 15) return@register

            ticks = 0

            client.connection?.sendCommand("gfs ender_pearl ${16 - count}")
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            ticks = -100
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("The Ballista is finally ready!") && enabled) {
            if (twilightArrowPoison.value) { mc.connection?.sendCommand("gfs twilight_arrow_poison ${amountTwap.value.toInt()}") }
            if (toxicArrowPoison.value) { mc.connection?.sendCommand("gfs toxic_arrow_poison ${amountTap.value.toInt()}") }
        }
    }
}