package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.KuudraUtils.kuudra
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand

object HideTags : Feature("Hide Tags", "", Categories.Category.KUUDRA) {

    fun cancel(entity: Entity): Boolean {
        if (!kuudra()) return false

        if (entity !is ArmorStand) return false
        if (!entity.isAlive) return false

        if (entity.name.string.startsWith("[Lv")) return true

        return false
    }
}