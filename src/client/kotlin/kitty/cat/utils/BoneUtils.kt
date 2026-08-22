package kitty.cat.utils

import kitty.cat.features.huds.BackboneHud
import kitty.cat.features.kuudra.BackboneAlert
import kitty.cat.features.kuudra.RendMacro
import kitty.cat.utils.Schedule.schedule
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

object BoneUtils {
    var boneStand: Display.ItemDisplay? = null
    var prev: Vec3? = null
    var curr: Vec3? = null
    var initialDir: Vec3? = null
    var returning: Boolean = false

    var awaitBone = false
    var throwOrigin: Vec3? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            handleBone()
        }
    }

    fun addEntity(entity: Entity) {
        if (!awaitBone) return
        if (entity is Display.ItemDisplay) {
            val relPos = throwOrigin?.subtract(entity.position()) ?: return
            if (relPos.x in -0.3..0.3 && relPos.z in -0.3..0.3 && awaitBone) { schedule(1) {
                    addBone(entity)
                }
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (result !is InteractionResult.Pass) return

        val item = player.getItemInHand(interactionHand)
        if (item.uuid() != "STARRED_BONE_BOOMERANG") return

        awaitBone = true
        throwOrigin = player.position()

        Schedule.schedule(12) {
            awaitBone = false
        }
    }

    fun addBone(entity: Display.ItemDisplay) {
        if (entity.itemStack.item == Items.GHAST_TEAR) {
            boneStand = entity
            prev = entity.position()
            curr = null
            initialDir = null
            returning = false
        }
    }

    fun handleBone() {
        val bs = boneStand ?: return

        if (!bs.isAlive) {
            prev = null
            curr = null
            initialDir = null
            returning = false
            return
        }

        curr = bs.position()

        val move = curr?.subtract(prev!!)
        val moveLen2 = move?.lengthSqr()

        if (moveLen2 != null) {
            if (moveLen2 > 1.0E-6) {
                if (initialDir == null) {
                    initialDir = move.normalize()
                } else if (!returning) {
                    val dot = move.normalize().dot(initialDir!!)
                    if (dot < 0.995) {
                        returning = true
                        if (curr == null) return
                        RendMacro.onBackbone()
                        BackboneAlert.alert()
                        BackboneHud.render = true
                        schedule(BackboneAlert.time.value) {
                            BackboneHud.render = false
                        }
                    }
                }
            }
        }

        prev = curr
    }
}