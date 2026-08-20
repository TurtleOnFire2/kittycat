package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.ColorSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

object Supplies : Feature("Supplies", "", Categories.Category.KUUDRA) {
    val pickUpHud = booleanSetting("Hud for pickup progress", false)

    val supplyBeacons = booleanSetting("Supply beacon", false)
    val supplyBeaconColor = colorSetting("Supply beacon color")
    val hoveredColor = colorSetting("Color when hovered")
    val dropOffBeacons = booleanSetting("Drop off beacons", false)
    val dropOffBeaconColor = colorSetting("Drop off beacon color")

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !supplies()) return@register

            val hr = mc.hitResult as? EntityHitResult

            if (dropOffBeacons.value) {
                KuudraUtils.activeDropOffs.forEach { dropOff ->
                    ctx.renderBeaconBeam(dropOff.second, dropOffBeaconColor.color)
                }
            }

            if (!supplyBeacons.value) return@register

            mc.level?.entitiesForRendering()?.forEach { e ->
                if (e is Giant) {
                    val center = Vec3(
                        e.x + (2.7 * cos((e.yRot + 130) * (Math.PI / 180))),
                        75.5,
                        e.z + (5.2 * sin((e.yRot + 130) * (Math.PI / 180)))
                    )
                    ctx.renderBeaconBeam(center, supplyBeaconColor.color)
                } else if (e is Zombie) {
                    if (e.y in 74.0..77.0) {
                        val color = if (hr?.entity === e) hoveredColor.color else supplyBeaconColor.color
                        ctx.renderBoxBounds(e.boundingBox, color, color.setAlpha(64))
                    }
                }
            }
        }
    }
}
