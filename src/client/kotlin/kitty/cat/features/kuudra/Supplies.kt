package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.ColorSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.function.Predicate
import kotlin.math.cos
import kotlin.math.sin

object Supplies : Feature("Supplies", "", Categories.Category.KUUDRA) {
    val pickUpHud = booleanSetting("Hud for pickup progress", false)

    val supplyBeacons = booleanSetting("Supply beacon", false)
    val supplyBeaconColor = colorSetting("Supply beacon color")
    val hoveredColor = colorSetting("Color when hovered")
    val dropOffBeacons = booleanSetting("Drop off beacons", false)
    val dropOffBeaconColor = colorSetting("Drop off beacon color")

    val reach = booleanSetting("Reach", false)
    val range = numberSetting("Range", 3.0, 5.5, 3.0, "", 0.1)

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

    fun changeReach(original: Double): Double {
        if (!supplies() || !kuudra() || !reach.value || !enabled) return original

        val player = mc.player ?: return original

        val start = mc.player?.eyePosition ?: return original
        val end = start.add(mc.player?.lookAngle?.scale(range.value) ?: return original)

        val searchBox = player.boundingBox
            .expandTowards(player.lookAngle.scale(range.value))
            .inflate(1.0)

        val ehr = ProjectileUtil.getEntityHitResult(
            player,
            start,
            end,
            searchBox,
            { entity -> entity.isPickable },
            range.value * range.value
        ) ?: return original

        if (ehr.entity is Zombie && ehr.entity.y in 75.2..76.8) return range.value

        return original
    }
}
