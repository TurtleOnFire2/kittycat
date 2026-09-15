package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.huds.SupplyAlertHud
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.getSupplyZombies
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

object Supplies : Feature("Supplies", "", Categories.Category.KUUDRA) {
    val pickUpHud = booleanSetting("Hud for pickup progress", false)
    val alertHud = booleanSetting("Hud for already picking and someone already picking alert", false)
    val giantAlert = booleanSetting("Standing in giant alert")

    val renderGiantHitboxTopPlane = booleanSetting("Render top of giant", false)

    val supplyBeacons = booleanSetting("Supply beacon", false)
    val supplyBeaconColor = colorSetting("Supply beacon color")
    val hoveredColor = colorSetting("Color when hovered")
    val dropOffBeacons = booleanSetting("Drop off beacons", false)
    val dropOffBeaconColor = colorSetting("Drop off beacon color")

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register { ctx ->
            if (!enabled || !supplies()) return@register

            if (dropOffBeacons.value) {
                KuudraUtils.activeDropOffs.forEach { dropOff ->
                    ctx.renderBeaconBeam(dropOff.second, dropOffBeaconColor.color)
                }
            }


            mc.level?.entitiesForRendering()?.forEach { e ->
                if (e is Giant) {
                    if (supplyBeacons.value) {
                        val center = Vec3(
                            e.x + (2.7 * cos((e.yRot + 130) * (Math.PI / 180))),
                            75.5,
                            e.z + (5.2 * sin((e.yRot + 130) * (Math.PI / 180)))
                        )
                        ctx.renderBeaconBeam(center, supplyBeaconColor.color)
                    }
                }
            }

        }

        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !supplies()) return@register

            if (renderGiantHitboxTopPlane.value) {
                mc.level?.entitiesForRendering()?.filterIsInstance<Giant>()?.forEach { giant ->
                    ctx.renderBoxBounds(giant.boundingBox.setMinY(giant.boundingBox.maxY - 0.05), Color.RED)
                }
            }

            if (!supplyBeacons.value) return@register

            val hr = mc.hitResult as? EntityHitResult
            getSupplyZombies().forEach { zombie ->
                val color = if (hr?.entity === zombie) hoveredColor.color else supplyBeaconColor.color
                ctx.renderBoxBounds(zombie.boundingBox, color, color.setAlpha(64))
            }
        }
    }

    fun handleChat(unformatted: String) {
        if (!enabled) return

        when (unformatted) {
            "You are already currently picking up some supplies!" -> {
                SupplyAlertHud.text = "Already picking!"
            }
            "Someone else is currently trying to pick up these supplies!" -> {
                SupplyAlertHud.text = "Someone picking!"
            }
            else -> {
                return
            }
        }
        SupplyAlertHud.time = System.currentTimeMillis()

    }
}
