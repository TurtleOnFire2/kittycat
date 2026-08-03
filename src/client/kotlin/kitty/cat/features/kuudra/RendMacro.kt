package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.dungeons.Storm.clickDelay
import kitty.cat.features.dungeons.Storm.swapWardrobeSlot
import kitty.cat.features.dungeons.Storm.swapping
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.Chat
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.clickSlot
import kitty.cat.utils.getLoadoutIndex
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.hotbarSlotFromItem
import kitty.cat.utils.renderPos
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import java.awt.Color

object RendMacro : Feature("Rend Macro", "", Categories.Category.KUUDRA) {
    val autoSneak = booleanSetting("Auto sneak", false)
    val autoJump = booleanSetting("Auto jump", false)
    val autoHollowWand = booleanSetting("Auto hollow wand", false)
    val clickOrder = orderSetting("Click order", listOf("Left click", "Right click"))
    val autoRod = booleanSetting("Auto rod", false)
    val autoThrowBone = booleanSetting("Auto throw bone", false)
    val offsetRight = numberSetting("Offset right",  -2.5, 2.5, 0.0)
    val offsetLeft = numberSetting("Offset left",  -2.5, 2.5, 0.0)
    val offsetBack = numberSetting("Offset back",  -2.5, 2.5, 0.0)
    val offsetFront = numberSetting("Offset front",  -2.5, 2.5, 0.0)
    val renderArea = booleanSetting("Render area", false, "Rendering is broken only for this? Idk why")
    val boneDelay = numberSetting("Bone delay", 1.0, 20.0, 6.0, "", 1.0)
    val autoHalberd = booleanSetting("Auto halberd", false)
    val autoLoadout = booleanSetting("Auto loadout", false)
    val loadoutSlot = numberSetting("Loadout slot", 1.0, 14.0, 1.0, "", 1.0)
    val clickDelay = numberSetting("Click delay", 1.0, 10.0, 1.0, "", 1.0)
    val autoPull = booleanSetting("Auto pull on ice spray", false)
    val pullItemSlot = numberSetting("Pull item slot", 1.0, 8.0, 1.0, "", 1.0)

    val key = keybindSetting("Trigger")

    override fun onKeybindPressed(setting: KeybindSetting) {
        if (!enabled) return
        triggerMacro()
    }

    private var clickLoadout = false

    private var edging = false
    private var throwBone = false
    private var rodThrow = System.currentTimeMillis()

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null) return@register

            checkBoneAndEdge()

            if (!renderArea.value) return@register

            val aabb = AABB(
                -111.0 - offsetRight.value,
                6.0,
                -118.0 - offsetBack.value,
                -95.0 + offsetLeft.value,
                0.0,
                -97.0 + offsetFront.value,
            )
            ctx.renderBoxBounds(aabb, Color.RED, phase = true)
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return

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

    fun checkBoneAndEdge() {
        val pos = mc.player?.renderPos ?: return
        val blockPos = BlockPos.containing(pos)

        if (mc.level?.getBlockState(blockPos.below())?.isAir == true && edging) {
            mc.options.keyJump.isDown = true
            edging = false
            schedule(3) { mc.options.keyJump.isDown = false }
        }

        if (
            throwBone && (
                    pos.x !in (-111.0 - offsetRight.value)..(-95.0 + offsetLeft.value)
                            || pos.z !in (-118.0 - offsetBack.value)..(-97.0 + offsetFront.value))
            && pos.y == 6.0
        ) {
            if (System.currentTimeMillis() - rodThrow < boneDelay.value * 50) return
            if (mc.player?.mainHandItem?.uuid() != "STARRED_BONE_BOOMERANG") return
            mc.options.keyUse.clickCount++
            throwBone = false
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!enabled) return

        if (result !is InteractionResult.Pass) return

        if (mc.player?.y != 6.0) return

        val item = player.getItemInHand(interactionHand)
        if (item.uuid() == "STARRED_BONE_BOOMERANG" && autoHalberd.value) {
            val slot = hotbarSlotFromID("AXE_OF_THE_SHREDDED") ?: return
            mc.player?.inventory?.selectedSlot = slot
            schedule(1) {
                mc.options.keyUse.clickCount++
            }
        }

        if (item.uuid() == "AXE_OF_THE_SHREDDED") {
            if (autoLoadout.value) {
                mc.connection?.sendCommand("loadout")
                clickLoadout = true
            }
        }

        if (item.uuid() == "STARRED_ICE_SPRAY_WAND") {
            if (autoPull.value) {
               mc.player?.inventory?.selectedSlot = pullItemSlot.value.toInt() - 1
                schedule(2) {
                    mc.options.keyAttack.clickCount++
                }
            }
        }
    }

    fun openScreen(packet: ClientboundOpenScreenPacket) {
        if (!packet.title.string.contains("Loadout") || !clickLoadout || mc.player == null) return
        clickLoadout = false

        schedule(clickDelay.value, true) {
            val sc = mc.screen as? AbstractContainerScreen<*> ?: return@schedule
            if (!packet.title.string.contains("Loadout")) return@schedule

            mc.player!!.clickSlot(sc.menu.containerId, getLoadoutIndex(loadoutSlot.value.toInt()))
            schedule(0) {
                if (mc.player?.containerMenu != null) {
                    mc.player!!.closeContainer()
                }
            }
        }
    }
    private fun clickByString(string: String) {
        if (string == "Left click") {
            mc.options.keyAttack.clickCount++
        } else if (string == "Right click") {
            mc.options.keyUse.clickCount++
        }
    }

    fun triggerMacro() {
        if (!enabled) return

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
}