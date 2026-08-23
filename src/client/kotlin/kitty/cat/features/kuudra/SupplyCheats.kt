package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

object SupplyCheats : Feature("Supply Cheats", "", Categories.Category.KUUDRA) {
    val reach = booleanSetting("Reach", false)
    val range = numberSetting("Range", 3.0, 5.5, 3.0, "", 0.1)

    val aura = booleanSetting("Aura", false)
    val auraRange = numberSetting("Aura range", 3.0, 8.0, 5.5, "", 0.1)
    val checks = selectorSetting("Checks", listOf("On ground", "Fov check", "On RMB only", "Rod only"), allowMultiple = true)
    val fov = numberSetting("Fov", 10.0, 360.0, 90.0, "°")
    val delay = numberSetting("Delay", 4.0, 30.0, 20.0, "t")

    var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (ticks++ < delay.value) return@register

            if (!kuudra() || !supplies() || !aura.value) return@register

            val zombies = KuudraUtils.getSupplyZombies().takeIf { it.isNotEmpty() } ?: return@register
            val closest = zombies[0]

            when {
                checks.selected.any { it == "On ground" } && !mc.player?.onGround()!! -> return@register
                checks.selected.any { it == "Fov check" } -> { if (!fovCheck(closest, fov.value)) return@register }
                checks.selected.any { it == "On RMB only" } -> { if (!mc.options.keyUse.isDown) return@register }
                checks.selected.any { it == "Rod only" } -> { if (mc.player?.mainHandItem?.item != Items.FISHING_ROD) return@register }
            }

            if (isInRange(closest, auraRange.value)) {
                ClickUtils.rightClickEntity(closest)
                ticks = 0
            }
        }
    }

    private fun isInRange(entity: Entity, reach: Double): Boolean {
        val player = mc.player ?: return false

        val eye = player.eyePosition
        val box = entity.boundingBox

        val closest = Vec3(
            eye.x.coerceIn(box.minX, box.maxX),
            eye.y.coerceIn(box.minY, box.maxY),
            eye.z.coerceIn(box.minZ, box.maxZ)
        )

        return eye.distanceToSqr(closest) <= reach * reach
    }

    private fun fovCheck(entity: Entity, fov: Double): Boolean {
        val player = mc.player ?: return false

        val toEntity = entity.boundingBox.center
            .subtract(player.eyePosition)
            .normalize()

        val look = player.lookAngle.normalize()

        val dot = look.dot(toEntity).coerceIn(-1.0, 1.0)
        val angle = Math.toDegrees(kotlin.math.acos(dot))

        return angle <= fov / 2.0
    }

    fun changeReach(original: Double): Double {
        if (!supplies() || !kuudra() || !reach.value || !enabled) return original

        return range.value
    }
}