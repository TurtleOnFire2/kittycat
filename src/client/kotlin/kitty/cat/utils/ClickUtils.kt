package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object ClickUtils {
    fun rightClickEntity(entity: Entity) {
        val player = mc.player ?: return

        val aabb = entity.boundingBox
        val eyePos = player.eyePosition
        val hitPos = Vec3(
            eyePos.x.coerceIn(aabb.minX, aabb.maxX),
            eyePos.y.coerceIn(aabb.minY, aabb.maxY),
            eyePos.z.coerceIn(aabb.minZ, aabb.maxZ)
        )

        val hitResult = EntityHitResult(entity, hitPos)

        interact(entity, hitResult)
    }

    fun interact(entity: Entity, entityHitResult: EntityHitResult) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        if (gameMode.playerMode == GameType.SPECTATOR) return
        val vec3: Vec3 = entityHitResult.location.subtract(entity.position())

        gameMode.startPrediction(mc.level!!) { i ->
            ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, vec3, player.isShiftKeyDown)
        }
    }
}