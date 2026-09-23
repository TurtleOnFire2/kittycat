package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import net.minecraft.world.phys.AABB
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.aabb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import java.awt.Color
import java.util.UUID

object SafeSpots : Feature("Safe Spots", "", Categories.Category.KUUDRA) {
    val safeSpots = listOf(
        SafeSpot(BlockPos(-71, 78, -135), false) {
            isSafe(-70.0, -136.0, -63.0, -126.0)
        },
        SafeSpot(BlockPos(-72, 77, -136), false) {
            isSafe(-70.0, -136.0, -63.0, -126.0) &&
            isSafe(-77.0, -151.0, -62.0, -136.0)
        },
        SafeSpot(BlockPos(-90, 77, -128), false) {
            isSafe(-94.0, -136.0, -81.0, -126.0)
        },
        SafeSpot(BlockPos(-86, 77, -129), false) {
            isSafe(-94.0, -136.0, -81.0, -126.0)
        },
        SafeSpot(BlockPos(-134, 77, -129), false) {
            isSafe(-149.0, -132.0, -137.0, -126.0)
        },
        SafeSpot(BlockPos(-141, 77, -91), false) {
            true
        },
        SafeSpot(BlockPos(-141, 76, -90), false) {
            true
        },
        SafeSpot(BlockPos(-142, 76, -88), false) {
            isSafe(-158.0, -96.0, -137.0, -68.0)
        },
        SafeSpot(BlockPos(-142, 76, -87), false) {
            isSafe(-158.0, -90.0, -137.0, -68.0)
        },
    )

    private var tickCubes: List<MagmaCube> = emptyList()


    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !kuudra() || !supplies()) return@register
            val boxes = safeSpots.map { spot ->
                BoxRender(spot.loc.aabb(), if (spot.safe) Color.GREEN else Color.RED)
            }.toMutableList()
            ctx.renderBoxesBounds(boxes)
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!kuudra() || !supplies()) return@register

            val level = mc.level ?: return@register
            tickCubes = level.entitiesForRendering().filterIsInstance<MagmaCube>()

            safeSpots.forEach { spot ->
                spot.safe = spot.check()
            }
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            tickCubes = emptyList()
        }
    }

    fun isSafe(minX: Double, minZ: Double, maxX: Double, maxZ: Double): Boolean {
        return !tickCubes.any { cube -> cube.x in minX..maxX && cube.z in minZ..maxZ && cube.y < 76}
    }

    data class SafeSpot(val loc: BlockPos, var safe: Boolean, val check: () -> Boolean)
}
