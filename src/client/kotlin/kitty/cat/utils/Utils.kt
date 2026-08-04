package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.jvm.optionals.getOrNull
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

inline val Entity.renderPos: Vec3
    get() =
        Vec3(this.renderX, this.renderY, this.renderZ)

inline val Entity.renderX: Double
    get() =
        xo + (getX() - xo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderY: Double
    get() =
        yo + (getY() - yo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

inline val Entity.renderZ: Double
    get() =
        zo + (getZ() - zo) * mc.deltaTracker.getGameTimeDeltaPartialTick(true)

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

fun Player.lookRay(range: Double = 3.0): Pair<Vec3, Vec3> {
    return eyePosition to eyePosition.add(lookAngle.scale(range))
}

fun AABB.canInteract(range: Double = 3.0): Boolean {
    val (start, end) = mc.player?.lookRay(range) ?: return false
    return clip(start, end).isPresent
}

fun AABB.add(blockPos: BlockPos): AABB {
    return AABB(minX + blockPos.x, minY + blockPos.y, minZ + blockPos.z, maxX + blockPos.x, maxY + blockPos.y, maxZ + blockPos.z)
}

fun Vec3.intersects(aabb: AABB): Boolean {
    return x in aabb.minX..aabb.maxX && y in aabb.minY..aabb.maxY && z in aabb.minZ..aabb.maxZ
}

fun Vec3.aabb(snap: Double, width: Double, height: Double): AABB {
    val x = if (snap == 0.0) x else round(x / snap) * snap
    val y = if (snap == 0.0) y else round(y / snap) * snap
    val z = if (snap == 0.0) z else round(z / snap) * snap
    return AABB(x - width, y - height, z - width, x + width, y + height, z + width)
}

fun Player.clickSlot(containerId: Int, slotIndex: Int, button: Int = 0, clickType: ContainerInput = ContainerInput.PICKUP) {
    mc.gameMode?.handleContainerInput(containerId, slotIndex, button, clickType, this)
}

fun ItemStack.uuid(): String? {
    return getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("id").getOrNull()
}

fun String.addColor() = replace("&", "§")

fun Color.setRed(r: Int): Color {
    return Color(r, green, blue, alpha)
}

fun Color.setGreen(g: Int): Color {
    return Color(red, g, blue, alpha)
}

fun Color.setBlue(b: Int): Color {
    return Color(red, green, b, alpha)
}

fun Color.setAlpha(a: Int): Color {
    return Color(red, green, blue, a)
}

fun Int.setRed(r: Int): Int {
    return (this and 0x00FFFFFF) or (r.coerceIn(0, 255) shl 16)
}

fun Int.setGreen(g: Int): Int {
    return (this and 0x00FFFFFF) or (g.coerceIn(0, 255) shl 8)
}

fun Int.setBlue(b: Int): Int {
    return (this and 0x00FFFFFF) or b.coerceIn(0, 255)
}

fun Int.setAlpha(a: Int): Int {
    return (this and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
}

fun hotbarSlotFromID(id: String): Int? {
    for (i in 0 .. 7) {
        val item = mc.player!!.inventory.getItem(i)
        if (item.uuid() == id) return i
    }
    return null
}

fun hotbarSlotFromItem(item: Item): Int? {
    for (i in 0 .. 7) {
        val itemStack = mc.player!!.inventory.getItem(i)
        if (itemStack.item == item) return i
    }
    return null
}

fun getLoadoutIndex(num: Int): Int {
    val i = (num - 1) / 3
    val j = (num - 1) % 3
    val k = 14 + i * 9 + j

    if (k !in 14..43) return -1
    return k
}

inline val ItemStack.lore: List<Component>
    get() =
        getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines()
