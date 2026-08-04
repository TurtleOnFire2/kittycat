package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.uuid

object Fixes : Feature("Fixes", "", Categories.Category.KUUDRA){
    val hollowFix = booleanSetting("Hollow wand fix", false)

    fun clickThrough(): Boolean {
        val uuid = mc.player?.mainHandItem?.uuid()

        if (uuid != "HOLLOW_WAND") return false

        return !(!enabled || !hollowFix.value)
    }
}