package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.stun
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.getRotation
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.lookinAt
import kitty.cat.utils.lore
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import java.awt.Color

object Stun : Feature("Stun", "", Categories.Category.KUUDRA) {
    val autoOpenShop = booleanSetting("Auto open shop", false)
    val autoCloseShop = booleanSetting("Auto close shop", false)
    val noBlind = booleanSetting("No blindness", false)
    val stunWaypoint = booleanSetting("Stun waypoint", false)
    val snapToWaypoint = booleanSetting("Snap waypoint", false)
    val snapRange = numberSetting("Snap range", 0.0, 2.0, 0.2, "", 0.1)
    val autoPickobulus = booleanSetting("Auto pickobulus", false)

    var purchased = false
    var snap = false

    val offset = Vec3(5.5, -20.5, 29.5)

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            purchased = false
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null || !enabled) return@register

            if (!stun() && !build()) return@register

            if (autoOpenShop.value) {
                ctx.renderBoxBounds(-74.0, 79.0, -104.0, -70.0, 79.05, -102.0, Color.CYAN)
            }

            if (stunWaypoint.value) {
                val pos = mc.player!!.position().add(offset)
                ctx.renderBoxBounds(pos.aabb(0.5), Color.CYAN, depthTest = false)
                if (snapToWaypoint.value) {
                    if (pos.lookinAt(snapRange.value, 50.0)) {
                        RotationUtils.applyGcd(pos.getRotation().first, pos.getRotation().second)
                    }
                }
            }
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted == "You purchased Human Cannonball!") {
            purchased = true
            if (mc.player?.containerMenu != null && autoCloseShop.value) {
                mc.player!!.closeContainer()
            }
            schedule(40) {
                purchased = false
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!autoOpenShop.value || !build() && !stun()) return

        if (!player.isCrouching) return
        if (player.mainHandItem.uuid() !in listOf("ETHERWARP_CONDUIT", "ASPECT_OF_THE_VOID")) return

        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(50.0))

        val pos = mc.level?.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
                )
        )?.blockPos ?: return

        if (pos.x in -74..-70 && pos.y == 78 && pos.z in -104..-102) {
            val slot = hotbarSlotFromID("KUUDRA_SHOP_ITEM") ?: return
            player.inventory.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return

        if (!build() && !stun()) return

        val pos = packet.change.position

        RendMacro.dM(pos.toString())

        if (autoPickobulus.value) {
            if (pos !in listOf(
                    Vec3(-155.5, 29.05, -156.5),
                    Vec3(-152.5, 28.05, -172.5),
                    Vec3(-153.5, 28.05, -172.5)
                )
            ) return

            var slot: Int? = null

            for (i in 1..7) {
                val lore = mc.player!!.inventory.getItem(i).lore

                lore.forEach {
                    if (it.string.contains("Ability: Pickobulus")) {
                        slot = i
                    }
                }
            }

            slot ?: return

            if (mc.player?.inventory?.selectedSlot == slot) {
                mc.options.keyUse.clickCount++
                return
            }

            mc.player?.inventory?.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }
    }

    fun openScreen(packet: ClientboundOpenScreenPacket): Boolean {
        if (!enabled) return false

        if (!stun() && !build()) return false

        if (!purchased) return false

        return autoCloseShop.value
    }

}
