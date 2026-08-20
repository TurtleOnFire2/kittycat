package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.uuid
import net.minecraft.world.entity.player.Player

object Fixes : Feature("Fixes", "", Categories.Category.KUUDRA){
    val hollowFix = booleanSetting("Hollow wand fix", false)
    val cancelPlacingConduit = booleanSetting("Cancel placing conduit", false)

    fun cancelPlacement(player: Player): Boolean {
        val uuid = player.mainHandItem.uuid()

        if (uuid != "ETHERWARP_CONDUIT") return false

        return (enabled && cancelPlacingConduit.value)
    }

    fun clickThrough(): Boolean {
        val uuid = mc.player?.mainHandItem?.uuid()

        if (uuid != "HOLLOW_WAND") return false

        return (enabled && hollowFix.value)
    }
}