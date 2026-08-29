package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.Supply
import kitty.cat.utils.KuudraUtils.getSupplyZombies
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

object Supplies : Feature("Supplies", "", Categories.Category.KUUDRA) {
    val pickUpHud = booleanSetting("Hud for pickup progress", false)

    val supplyBeacons = booleanSetting("Supply beacon", false)
    val supplyBeaconColor = colorSetting("Supply beacon color")
    val hoveredColor = colorSetting("Color when hovered")
    val dropOffBeacons = booleanSetting("Drop off beacons", false)
    val dropOffBeaconColor = colorSetting("Drop off beacon color")

    val autoWalk = booleanSetting("Auto walk after pearl lands", false)
    val range = numberSetting("Range", 0.0, 5.0, 2.0, "", 0.1)

    private const val PEARL_TIMEOUT_TICKS = 100L
    private const val STOP_DISTANCE_SQR = 0.2

    private data class MovementKeys(val forward: Boolean, val back: Boolean, val left: Boolean, val right: Boolean)
    private data class PendingPearl(val supply: Supply, val throwTick: Long)

    private var pendingPearl: PendingPearl? = null
    private var walkTarget: Vec3? = null
    private var previousMovement: MovementKeys? = null

    fun register() {
        UseItemCallback.EVENT.register { player, level, hand ->
            if (enabled && autoWalk.value && supplies() && player.getItemInHand(hand).item == Items.ENDER_PEARL) {
                KuudraUtils.getSupply().takeUnless { it == Supply.None }?.let {
                    pendingPearl = PendingPearl(it, level.gameTime)
                }
            }
            InteractionResult.PASS
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            val player = mc.player
            if (!enabled || !autoWalk.value || !supplies() || player == null) {
                stopWalking()
                pendingPearl = null
                return@register
            }

            if (pendingPearl?.let { player.level().gameTime - it.throwTick > PEARL_TIMEOUT_TICKS } == true) {
                pendingPearl = null
            }

            val target = walkTarget ?: return@register
            val dx = target.x - player.x
            val dz = target.z - player.z
            val distanceSqr = dx * dx + dz * dz
            if (distanceSqr <= STOP_DISTANCE_SQR || distanceSqr > range.value * range.value) {
                stopWalking()
                return@register
            }

            val targetYaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
            val direction = round(Mth.wrapDegrees(targetYaw - player.yRot) / 45f).toInt()
            setMovement(
                forward = direction in -1..1,
                back = direction <= -3 || direction >= 3,
                left = direction in -3..-1,
                right = direction in 1..3,
            )
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            stopWalking()
            pendingPearl = null
        }

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
                }
            }

            getSupplyZombies().forEach { zombie ->
                val color = if (hr?.entity === zombie) hoveredColor.color else supplyBeaconColor.color
                ctx.renderBoxBounds(zombie.boundingBox, color, color.setAlpha(64))
            }
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !autoWalk.value || !supplies()) return

        val pearl = pendingPearl ?: return
        pendingPearl = null

        val player = mc.player ?: return
        if (player.inventory.getItem(8).item != Items.CHEST) return

        val dropOffName = if (pearl.supply == Supply.Square) KuudraUtils.square.name else pearl.supply.name
        val target = KuudraUtils.activeDropOffs.firstOrNull { it.first == dropOffName }?.second ?: return
        val landing = packet.change.position
        val dx = target.x - landing.x
        val dz = target.z - landing.z
        val distanceSqr = dx * dx + dz * dz
        if (distanceSqr > range.value * range.value || distanceSqr <= STOP_DISTANCE_SQR) return

        previousMovement = MovementKeys(
            mc.options.keyUp.isDown,
            mc.options.keyDown.isDown,
            mc.options.keyLeft.isDown,
            mc.options.keyRight.isDown,
        )
        walkTarget = target
    }

    private fun stopWalking() {
        previousMovement?.let { setMovement(it.forward, it.back, it.left, it.right) }
        previousMovement = null
        walkTarget = null
    }

    private fun setMovement(forward: Boolean, back: Boolean, left: Boolean, right: Boolean) {
        mc.options.keyUp.isDown = forward
        mc.options.keyDown.isDown = back
        mc.options.keyLeft.isDown = left
        mc.options.keyRight.isDown = right
    }
}
