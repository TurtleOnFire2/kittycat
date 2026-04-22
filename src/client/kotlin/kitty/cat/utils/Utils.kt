package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.ChatFormatting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

inline val Entity.renderPos: Vec3
    get() =
        Vec3(this.renderX, this.renderY, this.renderZ)

inline val Entity.renderX: Double
    get() =
        xo + (x - xo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderY: Double
    get() =
        yo + (y - yo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderZ: Double
    get() =
        zo + (z - zo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val String?.noFormatting: String?
    get() = ChatFormatting.stripFormatting(this)

fun Vec3.aabb(diameter: Double): AABB =
    AABB(x  - diameter / 2, y  - diameter / 2, z  - diameter / 2, x + diameter / 2, y + diameter / 2, z + diameter / 2)

fun Vec3.getLook(origin: Vec3): Pair<Float, Float> {
    val dx = this.x - origin.x
    val dy = this.y - origin.y
    val dz = this.z - origin.z

    val horizontalDist = sqrt(dx * dx + dz * dz)

    val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
    val pitch = Math.toDegrees(-atan2(dy, horizontalDist)).toFloat()

    return Pair(yaw, pitch)
}

fun rotate(yaw: Float, pitch: Float) {
    if (mc.player == null) return
    mc.player!!.yRot = yaw
    mc.player!!.xRot = pitch
}

fun normalizeYaw(yaw: Float): Float {
    var y = yaw % 360f
    if (y > 180f) y -= 360f
    if (y < -180f) y += 360f
    return y
}

fun Entity.name(): String? {
    return mc.level?.getEntity(id)?.takeIf { isAlive }?.name?.string?.noFormatting
}

fun Vec3.round(decimals: Int): Vec3 {
    val factor = 10.0.pow(decimals)
    return Vec3(
        round(x * factor) / factor,
        round(y * factor) / factor,
        round(z * factor) / factor
    )
}

fun Player.clickSlot(containerId: Int, slotIndex: Int, button: Int = 0, clickType: ClickType = ClickType.PICKUP) {
    mc.gameMode?.handleInventoryMouseClick(containerId, slotIndex, button, clickType, this)
}