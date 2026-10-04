package kitty.cat.features.misc

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.LocationManager
import kitty.cat.utils.skyblock.Island
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.function.Predicate

object ClickThrough : Feature("Click Through Players", "", Categories.Category.MISC) {
    val dungeonAndKuudraOnly = booleanSetting("Only work in dungeons and kuudra", true)
    val changeAlpha = booleanSetting("Change player alpha", false)
    private val clickedThrough = mutableSetOf<Entity>()

    fun clickThrough(): Boolean {
        if (dungeonAndKuudraOnly.value) {
            if (!kuudra() && !LocationManager.isCurrentArea(Island.Dungeon)) return false
        }
        return enabled
    }

    fun adjustAlpha(player: Entity): Double {
        if (!changeAlpha.value || !clickThrough() || player !in clickedThrough) return 255.0
        val dist = player.distanceToSqr(mc.player?.eyePosition ?: return 255.0)

        return (16.0 * dist).coerceAtMost(255.0)
    }

    fun modifyPicker(original: Predicate<Entity>): Predicate<Entity> {
        clickedThrough.clear()
        if (!clickThrough()) return original
        return original.and { entity ->
            if (entity is Player) {
                clickedThrough.add(entity)
                false
            } else true
        }
    }
}
