package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack

object Deathbow: Feature("Deathbow", "", Categories.Category.DUNGEONS) {

    val bowTint = booleanSetting("Apply tint at max pull", false)
    val autoSwapItem = booleanSetting("Auto swap item")
    val swapSlot = numberSetting("Item slot", 1.0, 8.0, 1.0, step = 1.0)

    val action = actionSetting("Debug") {
        active = true
    }

    var active = false
    var useTime = 0

    fun bowReleased(item: ItemStack, entity: LivingEntity) {
        if (entity != mc.player || !active) return
        if (!item.hoverName.string.contains("Death Bow") || !autoSwapItem.value) return

        active = false

        if (mc.player!!.inventory.selectedSlot == swapSlot.value.toInt() - 1) return
        mc.player!!.inventory.selectedSlot = swapSlot.value.toInt() - 1
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("WELL WELL WELL LOOK WHO'S HERE!")) {
            active = true
        } else if (unformatted.contains("YOU TRICKED ME!")) {
            active = false
        }
    }

    fun serverTick() {
        if (mc.player == null) return
        if (mc.player!!.mainHandItem.hoverName.string.contains("Death Bow") && mc.player!!.isUsingItem) {
            useTime++
        } else {
            useTime = 0
        }
    }

    var tintActive = false

    @JvmStatic
    fun tintBow(): Boolean {
        if (BowItem.getPowerForTime(useTime) == 1f && bowTint.value && tintActive) return true
        return false
    }

    @JvmStatic
    fun tintArgb(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (((argb ushr 8) and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}