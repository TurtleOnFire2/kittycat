package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.minecraft.world.entity.EquipmentSlot

object BackboneAlert : Feature("Backbone Alert", "", Categories.Category.KUUDRA) {
    val time = numberSetting("Time", 5.0, 40.0, 10.0, "t", 1.0)
    val showGear = booleanSetting("Show time, item and helmet on backbone", false)

    var p4Start = 0

    fun serverTick() {
        p4Start++
    }

    fun alert() {
        if (!showGear.value || !enabled) return

        val item = mc.player?.mainHandItem
        val helmet = mc.player?.getItemBySlot(EquipmentSlot.HEAD)

        val seconds = (p4Start / 20.0)

        Chat.send("Backbone hit with ", item?.displayName ?: "null", " and ", helmet?.displayName ?: "null", " at ${seconds}s.")
    }
}
