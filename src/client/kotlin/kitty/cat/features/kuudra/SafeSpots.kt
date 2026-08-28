package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.aabb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import java.awt.Color
import java.util.UUID

object SafeSpots : Feature("Safe Spots", "", Categories.Category.KUUDRA) {

    val magmaCubeDebug = booleanSetting("Magma cube debug")

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
        SafeSpot(BlockPos(-142, 76, -88), false) {
            isSafe(-158.0, -96.0, -137.0, -68.0)
        },
        SafeSpot(BlockPos(-142, 76, -87), false) {
            isSafe(-158.0, -90.0, -137.0, -68.0)
        },
    )

    val magmaCubeBounds = mutableMapOf<UUID, MagmaCubeBounds>()

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            safeSpots.forEach { spot ->
                val color = if (spot.safe) Color.GREEN else Color.RED
                ctx.renderBoxBounds(spot.loc.aabb(), color)
            }
            val now = System.currentTimeMillis()

            magmaCubeBounds.entries.removeIf { (_, bounds) ->
                now - bounds.lastSeenAt > 4_000L
            }

            magmaCubeBounds.values.forEach { bounds ->
                ctx.renderBoxBounds(
                    bounds.minX,
                    75.0,
                    bounds.minZ,
                    bounds.maxX,
                    75.05,
                    bounds.maxZ,
                    Color.WHITE
                )
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            val level = mc.level ?: return@register
            val allCubes = level.entitiesForRendering().filterIsInstance<MagmaCube>()
            val now = System.currentTimeMillis()

            safeSpots.forEach { spot ->
                spot.safe = spot.check()
            }

            allCubes.forEach { cube ->
                if (!magmaCubeDebug.value) return@register
                if (cube.y !in 65.0..75.00) return@forEach
                magmaCubeBounds.getOrPut(cube.uuid) {
                    MagmaCubeBounds(cube.x, cube.x, cube.z, cube.z, now)
                }.include(cube.x, cube.z, now)
            }
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            magmaCubeBounds.clear()
        }
    }

    fun isSafe(minX: Double, minZ: Double, maxX: Double, maxZ: Double): Boolean {
        val level = mc.level ?: return true
        val allCubes = level.entitiesForRendering().filterIsInstance<MagmaCube>()

        return !allCubes.any { cube -> cube.x in minX..maxX && cube.z in minZ..maxZ && cube.y < 76}
    }
    data class MagmaCubeBounds(
        var minX: Double,
        var maxX: Double,
        var minZ: Double,
        var maxZ: Double,
        var lastSeenAt: Long
    ) {
        fun include(x: Double, z: Double, seenAt: Long) {
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minZ = minOf(minZ, z)
            maxZ = maxOf(maxZ, z)
            lastSeenAt = seenAt
        }
    }

    data class SafeSpot(val loc: BlockPos, var safe: Boolean, val check: () -> Boolean)
}
