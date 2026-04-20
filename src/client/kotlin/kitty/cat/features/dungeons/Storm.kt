package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.getLook
import kitty.cat.utils.normalizeYaw
import kitty.cat.utils.renderPos
import kitty.cat.utils.rotate
import me.cheater.legitcatmod.utils.drawFilled
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.awt.Color

object Storm: Feature("Storm", "", Categories.Category.DUNGEONS) {

    val aimPos = Vec3(100.0, 181.0, 64.0)
    var aiming = false

    fun register() {
        WorldRenderEvents.END_MAIN.register { ctx ->
            ctx.drawFilled(aimPos.aabb(0.2), Color.CYAN, false)
            if (!aiming || mc.player == null) return@register

            val pos = Vec3(mc.player!!.renderPos.x,mc.player!!.eyePosition.y, mc.player!!.renderPos.z)

            rotate(aimPos.getLook(pos).first, aimPos.getLook(pos).second)
        }
        ClientTickEvents.END_CLIENT_TICK.register { ctx ->
            serverTick()
            if (mc.player == null) return@register
            if (mc.player!!.xRot < -60f) {
                if (autoWalkForward.value) mc.options.keyUp.isDown = false
                schedule(5) {
                    aiming = false
                }
            }
        }
    }

    val bowTint = booleanSetting("Apply tint at max pull", false)
    val autoSwapCritItem = booleanSetting("Auto swap crit item")
    val swapSlot = numberSetting("Item slot", 1.0, 8.0, 1.0, step = 1.0)
    val autoReleaseLB = booleanSetting("Auto release Last Breath")
    val tickOffset = numberSetting("Tick offset", min = 0.0, max = 10.0, 0.0, step = 1.0)
    val autoTrack = booleanSetting("Auto track Storm")
    val autoWalkForward = booleanSetting("Auto walk forward")
    val autoSwapTerm = booleanSetting("Auto swap term in Storm")
    val leftClickWithTerm = booleanSetting("Left click with term after")

    var maxor = false
    var storm = false

    var stormTicks = 0

    var useTime = 0

    fun bowReleased(item: ItemStack, entity: LivingEntity) {
        if (entity != mc.player || !enabled) return
        if (maxor) {
            if (!item.hoverName.string.contains("Death Bow") || !autoSwapCritItem.value) return

            maxor = false

            if (mc.player!!.inventory.selectedSlot == swapSlot.value.toInt() - 1) return
            mc.player!!.inventory.selectedSlot = swapSlot.value.toInt() - 1
        }

        if (storm && stormTicks > 685 - tickOffset.value) {
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
        } else if (unformatted.contains("YOU TRICKED ME!")) {
            maxor = false
        } else if (unformatted.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
            storm = true
            stormTicks = 0
        } else if (unformatted.contains("[BOSS] Goldor: Who dares trespass into my domain")) {
            storm = false
        } else if (unformatted.contains("⚠ Storm is enraged! ⚠") && leftClickWithTerm.value) {
            mc.options.keyAttack.isDown = false
        }
    }

    fun serverTick() {
        if (mc.player == null || !enabled) return

        if (mc.player!!.mainHandItem.hoverName.string.contains("Death Bow") && mc.player!!.isUsingItem) {
            useTime++
        } else {
            useTime = 0
        }

        if (storm && ++stormTicks >= 690 - tickOffset.value) {
            if (autoWalkForward.value) { mc.options.keyUp.isDown = true }
            if (autoTrack.value && normalizeYaw(mc.player!!.yRot) in -137.0..-127.0 && mc.player!!.xRot in -34.0..-30.0) { aiming = true }

            if (mc.player!!.mainHandItem.hoverName.string.contains("Last Breath") && mc.player!!.isUsingItem && autoReleaseLB.value) {
                mc.options.keyUse.isDown = false
            } else {
                storm = false
                stormTicks = 0
            }
        }
    }



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