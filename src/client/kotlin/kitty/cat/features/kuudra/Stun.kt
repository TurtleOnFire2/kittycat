package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.AimAssist
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.build
import kitty.cat.utils.KuudraUtils.stun
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.lore
import kitty.cat.utils.renderPos
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.awt.Color

object Stun : Feature("Stun", "", Categories.Category.KUUDRA) {
    val autoOpenShop = booleanSetting("Auto open shop", false)
    val renderArea = booleanSetting("Render area for auto open", false)
    val showWaypoint = booleanSetting("Show a waypoint", false, "Shows a waypoint on where to etherwarp to to also insta mount cannon.")
    val shopAimAssist = booleanSetting("Shop waypoint aim assist", false)
    val onlyOnLeftSide = booleanSetting("Only work on left side of ballista", false)
    val shopAimAssistFov = numberSetting("Shop waypoint aim assist FOV", 5.0, 180.0, 20.0, "°", 1.0)
    val shopAimAssistStrength = numberSetting("Shop waypoint aim assist strength", 0.01, 1.0, 0.5, "", 0.005)
    val autoSetCursor = booleanSetting("Auto set cursor on shop open", false)
    val autoCloseShop = booleanSetting("Auto close shop", false)
    val noBlind = booleanSetting("No blindness", false)
    val stunWaypoint = booleanSetting("Stun waypoint", false)
    val pod = selectorSetting("Pod", listOf("Left", "Back", "Right"), listOf("Back"), false)
    val aimAssist = booleanSetting("Aim assist", false)
    val aimAssistFov = numberSetting("Aim assist FOV", 5.0, 180.0, 20.0, "°", 1.0)
    val aimAssistStrength = numberSetting("Aim assist strength", 0.01, 1.0, 0.5, "", 0.005)
    val autoPickobulus = booleanSetting("Auto pickobulus", false)
    val earlyPicko = booleanSetting("Pickobulus early", false, "Pickos when entering belly (Requires you to spam etherwarp)")

    var purchased = false
    private var podDestroyed = false

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            purchased = false
            podDestroyed = false
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null || !enabled) return@register

            if (!stun() && !build()) return@register

            if (autoOpenShop.value && renderArea.value) {
                ctx.renderBoxBounds(-74.0, 79.0, -104.0, -70.0, 79.05, -101.0, Color.CYAN)
            }

            if (showWaypoint.value) {
                ctx.renderBoxBounds(
                    SHOP_WAYPOINT.x - 0.5,
                    SHOP_WAYPOINT.y,
                    SHOP_WAYPOINT.z - 0.5,
                    SHOP_WAYPOINT.x + 0.5,
                    SHOP_WAYPOINT.y + 0.05,
                    SHOP_WAYPOINT.z + 0.5,
                    Color.RED
                )
            }

            if (stunWaypoint.value && !podDestroyed) {
                val pos = mc.player!!.renderPos.add(getOffset())
                ctx.renderBoxBounds(pos.aabb(1.0), Color.CYAN, depthTest = false)
            }
        }
    }

    private fun getOffset(): Vec3 {
        return when (pod.selected.first()) {
            "Left" -> Vec3(8.5, -21.5, 13.5)
            "Back" -> Vec3(5.5, -20.5, 29.5)
            else -> Vec3(-6.5, -21.5, 18.5)
        }
    }

    fun onTurn(accumulatedDX: Double, accumulatedDY: Double): DoubleArray? {
        val player = mc.player ?: return null
        if (!enabled) return null
        if (!stun() && !build()) return null

        val candidates = buildList {
            if (showWaypoint.value && shopAimAssist.value) {

                if (onlyOnLeftSide.value && player.x < -102) return@buildList

                add(AimAssist.candidate(SHOP_WAYPOINT, shopAimAssistFov.value, shopAimAssistStrength.value))
            }
            if (stun() && !podDestroyed && stunWaypoint.value && aimAssist.value) {
                add(AimAssist.candidate(player.position().add(getOffset()), aimAssistFov.value, aimAssistStrength.value))
            }
        }

        val candidate = candidates.filterNotNull().minByOrNull { it.distance } ?: return null
        return AimAssist.adjustMouse(accumulatedDX, accumulatedDY, candidate)
    }

    fun handleChat(unformatted: String) {
        if (unformatted.contains("You equipped")) {
            Chat.send(System.nanoTime(), unformatted)
        }

        if (unformatted.endsWith(" destroyed one of Kuudra's pods!")) {
            podDestroyed = true
        }

        if (unformatted == "You purchased Human Cannonball!") {
            purchased = true
            if (mc.player?.containerMenu != null && autoCloseShop.value && enabled) {
                mc.player!!.closeContainer()
            }
            schedule(40) {
                purchased = false
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!autoOpenShop.value || !build() && !stun() || !enabled) return

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

        if (pos.x in -74..-70 && pos.y == 78 && pos.z in -104..-101) {
            val slot = hotbarSlotFromID("KUUDRA_SHOP_ITEM") ?: return
            player.inventory.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !autoPickobulus.value) return

        if (!stun()) return

        val pos = packet.change.position

        RendMacro.dM(pos.toString())

        if (earlyPicko.value) {
            if (pos != Vec3(-161.0, 49.0, -186.0)) return
        } else if (!podDestroyed) {
            if (!(pos.x in -171.0..-148.0 && pos.y in 26.0..31.0 && pos.z in -174.0..-151.0)) return
        } else {
            return
        }
        var slot: Int? = null

        for (i in 0..7) {
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

    fun openScreen(packet: ClientboundOpenScreenPacket): Boolean {
        if (!enabled) return false

        if (packet.title.string.contains("Loadout")) Chat.send(System.nanoTime(), packet.title.string)

        if (!stun() && !build()) return false

        if (!purchased) return false

        return autoCloseShop.value
    }

    fun handleSetSlot(packet: ClientboundContainerSetSlotPacket) {
        if (packet.item.hoverName.string != "Human Cannonball" || !autoSetCursor.value) return

        if (!build() && !stun()) return

        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        if (packet.containerId != screen.menu.containerId) return
        if (packet.slot !in screen.menu.slots.indices) return

        val slot = screen.menu.getSlot(packet.slot)

        val relativeX = slot.x
        val relativeY = slot.y

        val guiX = screen.leftPos + relativeX + 8.0
        val guiY = screen.topPos + relativeY + 8.0

        val window = mc.window
        val windowX = guiX * window.screenWidth / window.guiScaledWidth
        val windowY = guiY * window.screenHeight / window.guiScaledHeight

        GLFW.glfwSetCursorPos(window.handle(), windowX, windowY)
    }

    private val SHOP_WAYPOINT = Vec3(-71.5, 79.0, -102.5)
}
