package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.clickSlot
import kitty.cat.utils.getLook
import kitty.cat.utils.normalizeYaw
import kitty.cat.utils.renderPos
import kitty.cat.utils.rotate
import kitty.cat.utils.drawFilled
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.abs

object Storm: Feature("Storm", "Stuff for Storm Phase", Categories.Category.DUNGEONS) {
    val bowTint = booleanSetting("Apply tint at max pull", false, description = "Applies a red tint when the Death Bow is at max charge")
    val autoSwapCritItem = booleanSetting("Auto swap crit item", description = "Automatically swaps to the selected slot after letting go of the Death Bow")
    val swapDelay = numberSetting("Swap delay", min = 0.0, max = 10.0, 0.0, step = 1.0)
    val swapSlot = numberSetting("Item slot", 1.0, 8.0, 1.0, step = 1.0)
    val autoSwapArmor = booleanSetting("Auto swap armor")
    val clickDelay = numberSetting("Click delay", min = 0.0, max = 10.0, 1.0, step = 1.0)
    val swapWardrobeSlot = numberSetting("Wardrobe slot", 1.0, 9.0, 1.0, step = 1.0)
    val autoReleaseLB = booleanSetting("Auto release Last Breath", description = "Automatically releases the Last Breath for Storm PY")
    val tickOffset = numberSetting("Tick offset", min = 0.0, max = 10.0, 0.0, step = 1.0, description = "Tick offset. 50 = 1t")
    val autoTrack = booleanSetting("Auto track Storm", description = "Tracks Storm for you after releasing Last Breath")
    val waypointOffset = numberSetting("Waypoint offset", min = -2.0, max = 2.0, 0.0, step = 0.1)
    val autoWalkForward = booleanSetting("Auto walk forward",  description = "Walks forward for you after releasing Last Breath")
    val autoSwapTerm = booleanSetting("Auto swap term in Storm", description = "Swaps to Term for you after releasing Last Breath")
    val leftClickWithTerm = booleanSetting("Left click with term after")
    val autoSneakYellow = booleanSetting("Auto sneak at yellow edge")

    private val wardrobeRegex = Regex("Wardrobe \\((\\d)/(\\d)\\)")

    var maxor = false
    var storm = false
    var necron = false

    var swapping = false
    var aiming = false
    var useTime = 0
    var stormTicks = 0
    val aimPos = Vec3(100.0, 181.0, 64.0)
    var unSneak = false

    fun register() {
        WorldRenderEvents.END_MAIN.register { ctx ->
            if (mc.player == null) return@register
            if (storm && mc.player!!.x in 33.0..35.0 && mc.player!!.y == 169.0 && mc.player!!.z in 63.0..70.0 && autoSneakYellow.value) {
                mc.options.keyShift.isDown = true
                unSneak = true
            } else if (unSneak) {
                mc.options.keyShift.isDown = false
                unSneak = false
            }
            if (storm) ctx.drawFilled(aimPos.add(waypointOffset.value, 0.0, 0.0).aabb(0.2), Color.CYAN, false)
            if (!aiming) return@register
            rotate(getLook().first, getLook().second)
        }
        ClientTickEvents.END_CLIENT_TICK.register { ctx ->
            if (mc.player == null) return@register
            if (mc.player!!.xRot < -60f && inArea()) {
                if (autoWalkForward.value) mc.options.keyUp.isDown = false
                schedule(5) {
                    aiming = false
                }
            }
        }
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { minecraft, level ->
            maxor = false
            storm = false
            necron = false
            aiming = false
            swapping = false
        }
    }

    fun bowReleased(item: ItemStack, entity: LivingEntity) {
        if (entity != mc.player || !enabled) return
        if (maxor) {
            if (!item.hoverName.string.contains("Death Bow") || !autoSwapCritItem.value) return

            schedule(2) { maxor = false }

            schedule(swapDelay.value) {
                if (mc.player!!.inventory.selectedSlot == swapSlot.value.toInt() - 1) return@schedule
                mc.player!!.inventory.selectedSlot = swapSlot.value.toInt() - 1
            }
        }

        if (storm && stormTicks > 685 - tickOffset.value && inArea()) {
            if (!item.hoverName.string.contains("Last Breath") || !autoSwapTerm.value) return
            storm = false

                for (slot in 0 until 9) {
                    val stack = mc.player!!.inventory.getItem(slot)
                    if (stack.hoverName.string.contains("Terminator")) {
                        mc.player!!.inventory.selectedSlot = slot
                        if (leftClickWithTerm.value) {
                            schedule(1) {
                                mc.options.keyAttack.isDown = true
                            }
                        }
                        break
                    }
                }

        }
    }

    fun handleChat(unformatted: String) {
        if (!enabled) return
        if (unformatted.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) {
            maxor = true
        } else if (unformatted.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
            swapping = false
            maxor = false
            storm = true
            stormTicks = 0
        } else if (unformatted.contains("[BOSS] Goldor: Who dares trespass into my domain")) {
            storm = false
        } else if (unformatted.contains("⚠ Storm is enraged! ⚠") && leftClickWithTerm.value && inArea()) {
            aiming = false
            mc.options.keyUp.isDown = false
            mc.options.keyAttack.isDown = false
        } else if (unformatted.contains("[BOSS] Necron: You went further than any human before, congratulations.")) {
            necron = true
        } else if (unformatted.contains("[BOSS] Necron: All this, for nothing...")) {
            necron = false
        }
    }

    fun handleScreen(packet: ClientboundOpenScreenPacket) {
        if (!wardrobeRegex.matches(packet.title.string) || !swapping) return
        swapping = false

        schedule(clickDelay.value, true) {
            val sc = mc.screen as? AbstractContainerScreen<*> ?: return@schedule
            if (!wardrobeRegex.matches(sc.title.string)) return@schedule

            mc.player!!.clickSlot(sc.menu.containerId, swapWardrobeSlot.value.toInt() + 35)
            schedule(0) {
                if (mc.player?.containerMenu != null) {
                    mc.player!!.closeContainer()
                }
            }
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (player.mainHandItem.uuid() == "STARRED_BONE_BOOMERANG" && necron && autoSwapCritItem.value) {
            mc.connection?.sendCommand("wd")
            swapping = true
            schedule(swapDelay.value) {
                if (mc.player!!.inventory.selectedSlot == swapSlot.value.toInt() - 1) return@schedule
                mc.player!!.inventory.selectedSlot = swapSlot.value.toInt() - 1
            }
        }
    }

    fun serverTick() {
        if (mc.player == null || !enabled) return

        if (mc.player!!.mainHandItem.hoverName.string.contains("Death Bow") && mc.player!!.isUsingItem) {
            useTime++
        } else {
            if (useTime >= 20 && autoSwapArmor.value && maxor) {
                mc.connection?.sendCommand("wd")
                swapping = true
            }
            useTime = 0
        }

        if (storm && ++stormTicks >= 690 - tickOffset.value) {
            if (!inArea()) return
            if (autoWalkForward.value) { mc.options.keyUp.isDown = true }

            val (yaw, pitch) = getLook()
            if (autoTrack.value && abs(normalizeYaw(mc.player!!.yRot) - yaw) < 5.0 && abs(mc.player!!.xRot - pitch) < 2.0) { aiming = true }

            if (mc.player!!.mainHandItem.hoverName.string.contains("Last Breath") && mc.player!!.isUsingItem && autoReleaseLB.value) {
                mc.options.keyUse.isDown = false
            } else {
                storm = false
                stormTicks = 0
            }
        }
    }

    private fun inArea(): Boolean {
        val pos = mc.player?.position() ?: return false
        return (pos.x in 86.0..110.00 && pos.y == 169.0 && pos.z in 60.0..78.0)
    }

    private fun getLook(): Pair<Float, Float> =
        aimPos.add(waypointOffset.value, 0.0, 0.0).getLook(Vec3(mc.player!!.renderPos.x,mc.player!!.eyePosition.y, mc.player!!.renderPos.z))

    var tintActive = false

    @JvmStatic
    fun tintBow(): Boolean {
        if (!enabled) return false
        if (BowItem.getPowerForTime(useTime) == 1f && bowTint.value && tintActive) return true
        return false
    }

    @JvmStatic
    fun tintArgb(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (((argb ushr 8) and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * 0.3f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}