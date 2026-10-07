package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.LocationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

object AutoGFS : Feature("Auto GFS", "", Categories.Category.KUUDRA) {

    init {
        cheat()
    }

    val enderPearls = booleanSetting("Ender pearls", true)
    val threshold = numberSetting("Threshold", 1.0, 15.0, 4.0, "", 1.0)
    val toxicArrowPoison = booleanSetting("Toxic arrow poison", true)
    val amountTap = numberSetting("Tap amount", 16.0, 32.0, 64.0, "", 1.0)
    val twilightArrowPoison = booleanSetting("Twilight arrow poison", true)
    val amountTwap = numberSetting("Twap amount", 2.0, 32.0, 8.0, "", 1.0)


    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
        }
    }

    var cooldown = System.currentTimeMillis()
    var lastThrow = System.currentTimeMillis()

    fun serverTick() {
        if (!enabled || !enderPearls.value || !kuudra() || mc.isLocalServer || mc.currentServer == null) return
        val player = mc.player ?: return
        if (mc.connection == null) return

        if (cooldown > System.currentTimeMillis() - 2000) return

        if (refillPearls(player)) cooldown = System.currentTimeMillis()
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("The Ballista is finally ready!") && enabled) {
            if (twilightArrowPoison.value) { mc.connection?.sendCommand("gfs twilight_arrow_poison ${amountTwap.value.toInt()}") }
            if (toxicArrowPoison.value) { mc.connection?.sendCommand("gfs toxic_arrow_poison ${amountTap.value.toInt()}") }
        }
    }

    private fun refillPearls(player: Player): Boolean {
        var count = 16

        for (i in 0..7) {
            val itemStack = player.inventory.getItem(i)
            if (itemStack.item == Items.AIR) {
                count = 0
                break
            }
            if (itemStack.item == Items.ENDER_PEARL) {
                count = itemStack.count
                break
            }
        }

        val guard = if (lastThrow > System.currentTimeMillis() - 1000) threshold.value.toInt() else 15

        if (count > guard) return false

        mc.connection?.sendCommand("gfs ender_pearl ${16 - count}")

        return true
    }

    fun prepareUseItem(player: Player, interactionHand: InteractionHand) {
        if (player.mainHandItem.item == Items.ENDER_PEARL) lastThrow = System.currentTimeMillis()
    }
}
