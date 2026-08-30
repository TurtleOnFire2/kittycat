package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.Supply
import kitty.cat.utils.KuudraUtils.getSupplyZombies
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.setAlpha
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.player.Player
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
    private const val DROP_OFF_BOX_HALF_SIZE = 0.25

    private data class MovementKeys(val forward: Boolean, val back: Boolean, val left: Boolean, val right: Boolean)
    private data class PendingPearl(val target: Vec3, val throwTick: Long)

    private var preparedPearl: PendingPearl? = null
    private var pendingPearl: PendingPearl? = null
    private var walkTarget: Vec3? = null
    private var previousMovement: MovementKeys? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            val player = mc.player
            if (!enabled || !autoWalk.value || !supplies() || player == null) {
                stopWalking()
                preparedPearl = null
                pendingPearl = null
                return@register
            }

            if (pendingPearl?.let { player.level().gameTime - it.throwTick > PEARL_TIMEOUT_TICKS } == true) {
                pendingPearl = null
            }

            val target = walkTarget ?: return@register
            if (player.inventory.getItem(8).item != Items.CHEST || shouldStopWalking(player.position(), target)) {
                stopWalking()
                return@register
            }

            updateMovement(player, target)
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            stopWalking()
            preparedPearl = null
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

            KuudraUtils.entitiesForRendering().forEach { e ->
                if (e is Giant) {
                    val center = Vec3(
                        e.x + (2.7 * cos((e.yRot + 130) * (Math.PI / 180))),
                        75.5,
                        e.z + (5.2 * sin((e.yRot + 130) * (Math.PI / 180)))
                    )
                    ctx.renderBeaconBeam(center, supplyBeaconColor.color)
                }
            }

            val boxes = getSupplyZombies().map { zombie ->
                val color = if (hr?.entity === zombie) hoveredColor.color else supplyBeaconColor.color
                BoxRender(zombie.boundingBox, color, color.setAlpha(64))
            }
            ctx.renderBoxesBounds(boxes)
        }
    }

    fun onPositionChange(@Suppress("UNUSED_PARAMETER") packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !autoWalk.value || !supplies()) return

        val pearl = pendingPearl ?: return
        pendingPearl = null

        val player = mc.player ?: return
        if (player.inventory.getItem(8).item != Items.CHEST) return

        val target = pearl.target
        if (shouldStopWalking(player.position(), target)) return

        stopWalking()
        previousMovement = MovementKeys(
            mc.options.keyUp.isDown,
            mc.options.keyDown.isDown,
            mc.options.keyLeft.isDown,
            mc.options.keyRight.isDown,
        )
        walkTarget = target
        updateMovement(player, target)
    }

    fun prepareUseItem(player: Player, interactionHand: InteractionHand) {
        preparedPearl = null
        if (!enabled || !autoWalk.value || !supplies()) return
        if (player.getItemInHand(interactionHand).item != Items.ENDER_PEARL) return

        val dropOffName = when (val supply = KuudraUtils.getSupply()) {
            Supply.Square -> KuudraUtils.square.name
            Supply.None -> return
            else -> supply.name
        }
        val target = KuudraUtils.dropOffs.firstOrNull { it.first == dropOffName }?.second ?: return
        preparedPearl = PendingPearl(target, player.level().gameTime)
    }

    fun useItem(
        @Suppress("UNUSED_PARAMETER") player: Player,
        @Suppress("UNUSED_PARAMETER") interactionHand: InteractionHand,
        result: InteractionResult,
    ) {
        val pearl = preparedPearl
        preparedPearl = null
        if (!enabled || !autoWalk.value || !supplies() || !result.consumesAction() || pearl == null) return

        stopWalking()
        pendingPearl = pearl
    }

    private fun shouldStopWalking(position: Vec3, target: Vec3): Boolean {
        val dx = target.x - position.x
        val dz = target.z - position.z
        val insideDropOff = kotlin.math.abs(dx) <= DROP_OFF_BOX_HALF_SIZE &&
            kotlin.math.abs(dz) <= DROP_OFF_BOX_HALF_SIZE
        return insideDropOff || dx * dx + dz * dz > range.value * range.value
    }

    private fun updateMovement(player: Player, target: Vec3) {
        val dx = target.x - player.x
        val dz = target.z - player.z
        val targetYaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        val direction = round(Mth.wrapDegrees(targetYaw - player.yRot) / 45f).toInt()
        setMovement(
            forward = direction in -1..1,
            back = direction <= -3 || direction >= 3,
            left = direction in -3..-1,
            right = direction in 1..3,
        )
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

    override fun onDisable() {
        stopWalking()
        preparedPearl = null
        pendingPearl = null
    }

}
