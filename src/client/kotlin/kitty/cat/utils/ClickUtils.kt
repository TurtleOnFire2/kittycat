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
    val queuedLooks = mutableListOf<Pair<Float, Float>>()

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (client.player == null) return@register

            val look = queuedClicks.removeFirstOrNull()?.getLook(mc.player!!.eyePosition) ?: queuedLooks.removeFirstOrNull() ?: return@register

            useItem(look.first, look.second)
            Chat.send("Fired with $look")
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            queuedClicks.clear()
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

    fun queueLook(look: Pair<Float, Float>) {
        queuedLooks.add(look)
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