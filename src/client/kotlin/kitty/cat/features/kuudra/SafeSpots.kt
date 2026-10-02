package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.aabb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object SafeSpots : Feature("Safe Spots", "", Categories.Category.KUUDRA) {
    val debugAreas = booleanSetting("Debug safe areas", false)

    private data class SafeArea(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double) {
        fun isClear() = SafeSpots.isSafe(minX, minZ, maxX, maxZ)
    }

    private val rightShopArea = SafeArea(-70.0, -139.0, -62.0, -126.0)
    private val middleShopArea = SafeArea(-77.0, -155.0, -62.0, -139.0)

    private val closeArea = SafeArea(-94.0, -138.0, -77.0, -126.0)
    private val farArea = SafeArea(-94.0, -155.0, -77.0, -138.0)

    private val westArea = SafeArea(-155.0, -132.0, -137.0, -124.0)

    private val square1 = SafeArea(-140.0, -87.0, -125.0, -68.0)
    private val square2 = SafeArea(-158.0, -87.0, -140.0, -68.0)
    private val square3 = SafeArea(-158.0, -100.0, -140.0, -87.0)
    private val checkedAreas = listOf(
        rightShopArea, middleShopArea, farArea, closeArea, westArea, square1, square2, square3
    )

    val safeSpots = listOf(
        SafeSpot(Vec3(-74.5, 78.5, -135.5), false) {
            rightShopArea.isClear() && closeArea.isClear() && middleShopArea.isClear() && farArea.isClear()
        },
        SafeSpot(Vec3(-73.5, 78.5, -135.5), false) {
            rightShopArea.isClear() && closeArea.isClear() && middleShopArea.isClear() && farArea.isClear()
        },
        SafeSpot(Vec3(-72.5, 78.5, -135.5), false) {
            rightShopArea.isClear() && closeArea.isClear() && middleShopArea.isClear()
        },
        SafeSpot(Vec3(-74.5, 78.55, -135.0), false) {
            rightShopArea.isClear() && closeArea.isClear()
        },
        SafeSpot(Vec3(-73.5, 78.55, -135.0), false) {
            rightShopArea.isClear() && closeArea.isClear()
        },
        SafeSpot(Vec3(-72.5, 78.55, -135.0), false) {
            rightShopArea.isClear()
        },
        SafeSpot(Vec3(-70.5, 78.5, -134.5), false) {
            rightShopArea.isClear()
        },
        SafeSpot(Vec3(-71.5, 77.5, -135.5), false) {
            rightShopArea.isClear() && middleShopArea.isClear()
        },
        SafeSpot(Vec3(-89.5, 77.5, -127.5), false) {
            closeArea.isClear()
        },
        SafeSpot(Vec3(-85.5, 77.5, -128.5), false) {
            closeArea.isClear()
        },
        SafeSpot(Vec3(-133.5, 77.5, -128.5), false) {
            westArea.isClear()
        },
        SafeSpot(Vec3(-140.5, 77.5, -90.5), false) {
            true
        },
        SafeSpot(Vec3(-140.5, 76.5, -89.5), false) {
            true
        },
        SafeSpot(Vec3(-140.5, 76.5, -85.5), false) {
            square1.isClear()
        },
        SafeSpot(Vec3(-141.5, 76.5, -86.5), false) {
            square2.isClear()
        },
        SafeSpot(Vec3(-141.5, 76.5, -87.5), false) {
            square3.isClear()
        }
    )

    private var tickCubes: List<MagmaCube> = emptyList()


    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !kuudra() || !supplies()) return@register
            val boxes = safeSpots.map { spot ->
                BoxRender(spot.loc.aabb(1.0), if (spot.safe) Color.GREEN else Color.RED)
            }.toMutableList()
            if (debugAreas.value) {
                checkedAreas.forEachIndexed { index, area ->
                    val color = if (area.isClear()) Color.GREEN else Color.RED
                    val y = 75.9 + index * 0.025
                    boxes += BoxRender(
                        AABB(area.minX, y, area.minZ, area.maxX, y + 0.01, area.maxZ),
                        color,
                        Color(color.red, color.green, color.blue, 24),
                    )
                }
            }
            ctx.renderBoxesBounds(boxes)
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled || !kuudra() || !supplies()) return@register

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

    data class SafeSpot(val loc: Vec3, var safe: Boolean, val check: () -> Boolean)
}
