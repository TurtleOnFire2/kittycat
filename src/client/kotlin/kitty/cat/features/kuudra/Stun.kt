package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.stun
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.lore
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3
import java.awt.Color

object Stun : Feature("Stun", "", Categories.Category.KUUDRA) {
    val autoOpenShop = booleanSetting("Auto open shop", false)
    val autoCloseShop = booleanSetting("Auto close shop", false)
    val clickThroughCannon = booleanSetting("Click through cannon", false)
    val blindnessAlert = booleanSetting("Blindness alert", false, "Doesnt do anything yet...")
    val stunWaypoint = booleanSetting("Stun waypoint", false)
    val autoPickobulus = booleanSetting("Auto pickobulus", false)

    var purchased = false
    var clickThrough = false

    val offset = Vec3(5.5, -20.5, 29.5)

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            purchased = false
            clickThrough = true
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null || !enabled) return@register

            if (!stun() && !build()) return@register

            if (autoOpenShop.value) {
                ctx.renderBoxBounds(-75.0, 79.0, -104.0, -71.0, 79.05, -101.0, Color.CYAN)
            }

            if (stunWaypoint.value) {
                ctx.renderBoxBounds(mc.player!!.position().add(offset).aabb(0.5), Color.CYAN, phase = true)
            }
        }
    }

    fun handleChat(unformatted: String) {
        if (unformatted == "You purchased Human Cannonball!") {
            purchased = true
            clickThrough = false
            if (mc.player?.containerMenu != null && autoCloseShop.value) {
                mc.player!!.closeContainer()
            }
            schedule(40) {
                purchased = false
            }
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return

        if (!build() && !stun()) return

        val pos = packet.change.position

        RendMacro.dM(pos.toString())

        if (pos.x in -75.0..-71.0 &&
            pos.y == 79.05 &&
            pos.z in -104.0..-101.0
        ) {
            if (clickThroughCannon.value) {
                clickThrough = true
            }

            if (!autoOpenShop.value) return

            if (mc.player?.mainHandItem?.uuid() == "KUUDRA_SHOP_ITEM") {
                mc.options.keyUse.clickCount++
                return
            }

            val slot = hotbarSlotFromID("KUUDRA_SHOP_ITEM") ?: return

            mc.player?.inventory?.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }

            if (pos == Vec3(-155.5, 29.05, -156.5) && stun() && autoPickobulus.value) {
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
    }

    fun openScreen(packet: ClientboundOpenScreenPacket): Boolean {
        if (!enabled) return false

        if (!stun()) return false

        if (!purchased) return false

        return autoCloseShop.value
    }

    fun clickThrough(): Boolean {
        return clickThrough && clickThroughCannon.value && enabled
    }
}