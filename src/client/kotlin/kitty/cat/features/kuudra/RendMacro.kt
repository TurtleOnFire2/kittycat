package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.gui.features.settings.BooleanSetting
import kitty.cat.gui.features.settings.NumberSetting
import kitty.cat.gui.features.settings.OrderSetting
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.hotbarSlotFromItem
import kitty.cat.utils.uuid
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.item.Items

object RendMacro : Feature("Rend Macro", "", Categories.Category.KUUDRA) {
    val autoSneak = booleanSetting("Auto sneak", false)
    val autoJump = booleanSetting("Auto jump", false)
    val autoHollowWand = booleanSetting("Auto hollow wand", false)
    val clickOrder = orderSetting("Click order", listOf("Left click", "Right click"))
    val autoRod = booleanSetting("Auto rod", false)
    val boneDelay = numberSetting("Bone delay", 1.0, 20.0, 6.0, "", 1.0)
    val autoThrowBone = booleanSetting("Auto throw bone", false)

    private val wardrobeRegex = Regex("Wardrobe \\((\\d)/(\\d)\\)")
    private var clickWardrobe = false

    private var edging = false
    private var throwBone = false
    private var rodThrow = System.currentTimeMillis()

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        val pos = packet.change.position
        val x = pos.x; val y = pos.y; val z = pos.z

        if (x !in -102.0..-101.0 || y !in 5.0..7.0 || z !in -106.0..-105.0) return

        if (autoSneak.value) {
            mc.options.keyShift.isDown = true
            schedule(2) { mc.options.keyShift.isDown = false }
        }
        if (autoJump.value) {
            edging = true
        }
        if (autoThrowBone.value) {
            throwBone = true
        }
        if (autoHollowWand.value) {
            if (mc.player?.mainHandItem?.uuid() == "HOLLOW_WAND") {
                clickByString(clickOrder.options[0])
                schedule(2) {
                    clickByString(clickOrder.options[1])
                    schedule(2) {
                        startSequence()
                    }
                }
            } else {
                val slot = hotbarSlotFromID("HOLLOW_WAND")

                if (slot == null) {
                    startSequence()
                    return
                }

                mc.player!!.inventory.selectedSlot = slot
                schedule(1) {
                    clickByString(clickOrder.options[0])
                    schedule(1) {
                        clickByString(clickOrder.options[1])
                        schedule(1) {
                            startSequence()
                        }
                    }
                }
            }
        }
    }

    fun startSequence() {
        val rodSlot = hotbarSlotFromItem(Items.FISHING_ROD) ?: return
        val boneSlot = hotbarSlotFromID("STARRED_BONE_BOOMERANG") ?: return
        if (autoRod.value) mc.player!!.inventory.selectedSlot = rodSlot
        schedule(1) {
            if (autoRod.value) {
                mc.options.keyUse.clickCount++
                rodThrow = System.currentTimeMillis()
            }
            schedule(2) {
                mc.player!!.inventory.selectedSlot = boneSlot
                }
        }
    }

    fun backBone() {

    }

    private fun clickByString(string: String) {
        if (string == "Left click") {
            mc.options.keyAttack.clickCount++
        } else if (string == "Right click") {
            mc.options.keyUse.clickCount++
        }
    }
}