package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import kotlinx.serialization.descriptors.PrimitiveKind
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.telemetry.events.WorldUnloadEvent
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.inc
import kotlin.io.use

object ClickUtils {

    val queuedClicks = mutableListOf<Vec3>()
    private data class QueuedLook(val look: Pair<Float, Float>, val sneak: Boolean?, val canExecute: () -> Boolean,
                                  val resolveLook: (() -> Pair<Float, Float>?)?)
    private val queuedLooks = mutableListOf<QueuedLook>()
    private var savedSneak: Boolean? = null
    private var forcedSneak: Boolean? = null
    private var sneakWait = 0

    private fun restoreSneak() {
        savedSneak?.let { mc.options.keyShift.isDown = it }
        savedSneak = null
        forcedSneak = null
        sneakWait = 0
    }

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (client.player == null) { restoreSneak(); return@register }
            if (queuedClicks.isNotEmpty()) restoreSneak()

            val look = queuedClicks.removeFirstOrNull()?.getLook(mc.player!!.eyePosition) ?: run {
                val queued = queuedLooks.firstOrNull() ?: run { restoreSneak(); return@register }
                if (!queued.canExecute()) {
                    queuedLooks.removeFirst()
                    restoreSneak()
                    return@register
                }
                val sneak = queued.sneak
                if (sneak != null) {
                    if (savedSneak == null) savedSneak = client.options.keyShift.isDown
                    if (forcedSneak != sneak) {
                        forcedSneak = sneak
                        sneakWait = if (client.player!!.isShiftKeyDown == sneak && client.player!!.isCrouching == sneak) 0 else 2
                    }
                    client.options.keyShift.isDown = sneak
                    // Let normal player ticks update pose and send sneak input before use-item.
                    if (sneakWait > 0) { sneakWait--; return@register }
                } else restoreSneak()
                queuedLooks.removeFirst()
                if (queued.resolveLook != null) queued.resolveLook.invoke() ?: run { restoreSneak(); return@register }
                else queued.look
            }

            useItem(look.first, look.second)
            Chat.send("Fired with $look")
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            queuedClicks.clear()
            queuedLooks.clear()
            restoreSneak()
        }
    }

    fun rightClickEntity(entity: Entity): Vec3? {
        val player = mc.player ?: return null

        val aabb = entity.boundingBox
        val eyePos = player.eyePosition
        val hitPos = Vec3(
            eyePos.x.coerceIn(aabb.minX, aabb.maxX),
            eyePos.y.coerceIn(aabb.minY, aabb.maxY),
            eyePos.z.coerceIn(aabb.minZ, aabb.maxZ)
        )

        val hitResult = EntityHitResult(entity, hitPos)

        interact(entity, hitResult)
        return hitPos
    }

    fun interact(entity: Entity, entityHitResult: EntityHitResult) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        if (gameMode.playerMode == GameType.SPECTATOR) return

        gameMode.interact(player, entity, entityHitResult, InteractionHand.MAIN_HAND)
    }

    fun queueLook(look: Pair<Float, Float>, sneak: Boolean? = null,
                  resolveLook: (() -> Pair<Float, Float>?)? = null, canExecute: () -> Boolean = { true }) {
        queuedLooks.add(QueuedLook(look, sneak, canExecute, resolveLook))
    }

    fun queueClick(target: Vec3) {
        queuedClicks.add(target)
    }

    fun useItem(yaw: Float, pitch: Float) {
        val gameMode = mc.gameMode ?: return
        val player = mc.player ?: return
        if (gameMode.playerMode == GameType.SPECTATOR) return

        val interactionHand = InteractionHand.MAIN_HAND

        gameMode.startPrediction(mc.level!!) { i ->
            val packet = ServerboundUseItemPacket(interactionHand, i, yaw, pitch)
            val stack = player.getItemInHand(interactionHand)
            if (player.cooldowns.isOnCooldown(stack)) {
                return@startPrediction packet
            } else {
                val res = stack.use(mc.level!!, player, interactionHand)
                val stack2 = if (res is InteractionResult.Success) (res.heldItemTransformedTo() ?: player.getItemInHand(interactionHand)) else player.getItemInHand(interactionHand)
                if (stack2 != stack) player.setItemInHand(interactionHand, stack2)
                    return@startPrediction packet
                }
            }
    }
}
