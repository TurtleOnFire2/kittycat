package kitty.cat.features.misc

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.aabb
import kitty.cat.utils.flatten
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.Display
import net.minecraft.world.item.Items

object Safari : Feature("Safari", "", Categories.Category.MISC) {
    val floorDropEsp = booleanSetting("Floor drop esp", false)
    val highlightColor = colorSetting("Color")

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!floorDropEsp.value || !enabled) return@register

            mc.level?.entitiesForRendering()?.filterIsInstance<Display.ItemDisplay>()?.forEach { display ->
                if (display.itemStack.item == Items.STRING) {
                    ctx.renderBoxBounds(display.blockPosition().aabb().move(0.0, 1.0, 0.0).flatten(0.1), highlightColor.color.setAlpha(0), highlightColor.color, phase = true)
                }
            }
        }
    }
}