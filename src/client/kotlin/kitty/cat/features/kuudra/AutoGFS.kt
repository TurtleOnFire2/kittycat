package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

object AutoGFS : Feature("Auto GFS", "", Categories.Category.KUUDRA) {
    val enderPearls = booleanSetting("Ender pearls", true)
    val threshold = numberSetting("Threshold", 2.0, 16.0, 8.0, "", 1.0)
    val toxicArrowPoison = booleanSetting("Toxic arrow poison", true)
    val amountTap = numberSetting("Tap amount", 16.0, 32.0, 64.0, "", 1.0)
    val twilightArrowPoison = booleanSetting("Twilight arrow poison", true)
    val amountTwap = numberSetting("Twap amount", 2.0, 32.0, 8.0, "", 1.0)

    var lastGFS = System.currentTimeMillis()

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!enabled || !enderPearls.value) return

        if (player.mainHandItem.item != Items.ENDER_PEARL) return

        val count = player.mainHandItem.count

        if (count <= threshold.value && System.currentTimeMillis() - lastGFS > 1000) {
            mc.connection?.sendCommand("gfs ender_pearl ${16 - count}")
            lastGFS = System.currentTimeMillis()
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("The Ballista is finally ready!") && enabled) {
            if (twilightArrowPoison.value) { mc.connection?.sendCommand("gfs twilight_arrow_poison ${amountTwap.value.toInt()}") }
            if (toxicArrowPoison.value) { mc.connection?.sendCommand("gfs toxic_arrow_poison ${amountTap.value.toInt()}") }
        }
    }
}