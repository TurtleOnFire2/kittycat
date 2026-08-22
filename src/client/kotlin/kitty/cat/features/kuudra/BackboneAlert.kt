package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.minecraft.world.entity.EquipmentSlot

object BackboneAlert : Feature("Backbone Alert", "", Categories.Category.KUUDRA) {
    val time = numberSetting("Time", 5.0, 40.0, 10.0, "t", 1.0)
    val showGear = booleanSetting("Show item and helmet on backbone", false)

    fun alert() {
        if (!showGear.value) return

        val item = mc.player?.mainHandItem
        val helmet = mc.player?.getItemBySlot(EquipmentSlot.HEAD)

        Chat.send("Backbone hit with ", item?.displayName ?: "null", " and ", helmet?.displayName ?: "null")
    }
}
