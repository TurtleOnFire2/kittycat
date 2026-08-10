package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.dps
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.stun
import kitty.cat.utils.RotationUtils
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.clickSlot
import kitty.cat.utils.getLoadoutIndex
import kitty.cat.utils.hotbarSlotFromID
import kitty.cat.utils.hotbarSlotFromItem
import kitty.cat.utils.renderPos
import kitty.cat.utils.setAlpha
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.min

object RendMacro : Feature("Rend Macro", "", Categories.Category.KUUDRA) {
    val autoSneak = booleanSetting("Auto sneak", false)

    val autoRotate = booleanSetting("Auto rotate", false)
    val minSpeed = numberSetting("Min speed", 100.0, 400.0, 165.0, "", 1.0)
    val maxSpeed = numberSetting("Max speed", 100.0, 400.0, 165.0, "", 1.0)

    val delay =  numberSetting("Rotation delay", 1.0, 20.0, 5.0, "t", 1.0)
    val autoJump = booleanSetting("Auto jump", false)
    val autoHollowWand = booleanSetting("Auto hollow wand", false)
    val firstClickDelay = numberSetting("Click delay first hollow click", 1.0, 10.0, 1.0, "t", 1.0)
    val secondClickDelay = numberSetting("Click delay second hollow click", 1.0, 10.0, 1.0, "t", 1.0)
    val clickOrder = orderSetting("Click order", listOf("Left click", "Right click"))
    val autoRod = booleanSetting("Auto rod", false)
    val triggerOnRod = booleanSetting("Trigger rest of macro on rod", false)
    val autoThrowBone = booleanSetting("Auto throw bone", false)
    val offsetRight = numberSetting("Offset right",  -2.5, 2.5, 0.0)
    val offsetLeft = numberSetting("Offset left",  -2.5, 2.5, 0.0)
    val offsetBack = numberSetting("Offset back",  -2.5, 2.5, 0.0)
    val offsetFront = numberSetting("Offset front",  -2.5, 2.5, 0.0)
    val renderArea = booleanSetting("Render area", false, "Rendering is broken only for this? Idk why")
    val boneDelay = numberSetting("Bone delay", 1.0, 20.0, 6.0, "t", 1.0)
    val autoHalberd = booleanSetting("Auto halberd", false)
    val autoLoadout = booleanSetting("Auto loadout", false)
    val loadoutSlot = numberSetting("Loadout slot", 1.0, 14.0, 1.0, "", 1.0)
    val clickDelay = numberSetting("Click delay", 1.0, 10.0, 1.0, "t", 1.0)
    val autoPull = booleanSetting("Auto pull on ice spray", false)
    val pullItemSlot = numberSetting("Pull item slot", 1.0, 8.0, 1.0, "", 1.0)

    val debug = booleanSetting("Debug", false)
    val key = keybindSetting("Trigger")

    override fun onKeybindPressed(setting: KeybindSetting) {
        if (!enabled) return
        triggerMacro()
    }

    private var clickLoadout = false

    private var edging = false
    private var throwBone = false
    private var rodThrow = System.currentTimeMillis()

    private var down = false

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
            ctx.renderBoxBounds(aabb, Color.WHITE.setAlpha(0), Color.RED.setAlpha(64), phase = true)
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            clickLoadout = false

            edging = false
            throwBone = false
            rodThrow = System.currentTimeMillis()

            down = false
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled) return

        if (!stun() && !dps()) return

        if (!kuudra()) return

        if (down) return
        down = true

        getRotationGoal()

        val pos = packet.change.position
        if (pos.y > 10) return

        if (autoSneak.value) {
            dM("Sneaking")
            mc.options.keyShift.isDown = true
            schedule(2) { mc.options.keyShift.isDown = false }
        }
        if (autoJump.value) {
            dM("Jumping")
            edging = true
        }
        if (autoThrowBone.value) {
            dM("Preparing throw bone")
            throwBone = true
        }
        if (autoHollowWand.value) {
            dM("Auto hollow start")
            if (mc.player?.mainHandItem?.uuid() == "HOLLOW_WAND") {
                dM("Hollow in hand -> clicking")
                schedule(firstClickDelay.value) {
                    clickByString(clickOrder.options[0])
                    schedule(secondClickDelay.value) {
                        clickByString(clickOrder.options[1])
                        schedule(2) {
                            startSequence()
                        }
                    }
                }
            } else {
                dM("Searching hollow")
                val slot = hotbarSlotFromID("HOLLOW_WAND")

                if (slot == null) {
                    dM("Hollow not found skipping")

                    startSequence()
                    return
                }

                dM("Hollow found -> swapping and clicking")

                mc.player!!.inventory.selectedSlot = slot
                schedule(firstClickDelay.value) {
                    clickByString(clickOrder.options[0])
                    schedule(secondClickDelay.value) {
                        clickByString(clickOrder.options[1])
                        schedule(2) {
                            startSequence()
                        }
                    }
                }
            }
        } else {
            startSequence()
        }
    }

    fun startSequence() {
        if (!enabled) return

        if (!dps() || !stun()) return

        dM("Sequence starting")

        dM("Looking for rod and bone")

        val rodSlot = hotbarSlotFromItem(Items.FISHING_ROD) ?: return
        dM("Rod found")
        val boneSlot = hotbarSlotFromID("STARRED_BONE_BOOMERANG") ?: return
        dM("Bone found")
        if (autoRod.value) mc.player!!.inventory.selectedSlot = rodSlot
        schedule(1) {
            if (autoRod.value) {
                dM("Throwing rod")
                mc.options.keyUse.clickCount++
                rodThrow = System.currentTimeMillis()
            }
        }
    }

    fun checkBoneAndEdge() {
        if (!enabled) return

        if (!dps()) return

        val pos = mc.player?.renderPos ?: return
        val blockPos = BlockPos.containing(pos)

        if (mc.level?.getBlockState(blockPos.below())?.isAir == true && edging) {
            dM("Jumping")
            mc.options.keyJump.isDown = true
            edging = false
            schedule(3) { mc.options.keyJump.isDown = false }
        }

        if (
            throwBone && (
                    pos.x !in (-111.0 - offsetRight.value)..(-95.0 + offsetLeft.value)
                            || pos.z !in (-118.0 - offsetBack.value)..(-97.0 + offsetFront.value))
        ) {
            if (System.currentTimeMillis() - rodThrow < boneDelay.value * 50) return
            if (mc.player?.mainHandItem?.uuid() != "STARRED_BONE_BOOMERANG") return
            dM("Throwing Bone")
            mc.options.keyUse.clickCount++
            throwBone = false
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!enabled) return

        if (!dps()) return

        if (result !is InteractionResult.Pass) return

        val item = player.getItemInHand(interactionHand)
        if (item.uuid() == "STARRED_BONE_BOOMERANG" && autoHalberd.value) {
            dM("Threw bone")
            val slot = hotbarSlotFromID("AXE_OF_THE_SHREDDED") ?: return
            mc.player?.inventory?.selectedSlot = slot
            schedule(1) {
                dM("Throwing aots")
                mc.options.keyUse.clickCount++
            }
        }

        if (item.uuid() == "AXE_OF_THE_SHREDDED") {
            dM("Threw aots")
            if (autoLoadout.value) {
                dM("Opening loadout")
                mc.connection?.sendCommand("loadout")
                clickLoadout = true
            }
        }

        if (item.uuid() == "STARRED_ICE_SPRAY_WAND") {
            dM("Used ice spray")
            if (autoPull.value) {
               mc.player?.inventory?.selectedSlot = pullItemSlot.value.toInt() - 1
                schedule(2) {
                    mc.options.keyAttack.clickCount++
                }
            }
        }

        if (item.item == Items.FISHING_ROD) {
            if (autoRod.value && System.currentTimeMillis() - rodThrow > 500) {
                schedule(2) {
                    val boneSlot = hotbarSlotFromID("STARRED_BONE_BOOMERANG") ?: return@schedule
                    mc.player!!.inventory.selectedSlot = boneSlot
                }
            } else if (triggerOnRod.value) {
                rodThrow = System.currentTimeMillis()
                schedule(2) {
                    val boneSlot = hotbarSlotFromID("STARRED_BONE_BOOMERANG") ?: return@schedule
                    mc.player!!.inventory.selectedSlot = boneSlot
                }
            }
        }
    }

    fun openScreen(packet: ClientboundOpenScreenPacket) {
        if (!enabled) return

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
            dM("Sneaking")
            mc.options.keyShift.isDown = true
            schedule(2) { mc.options.keyShift.isDown = false }
        }
        if (autoJump.value) {
            dM("Jumping")
            edging = true
        }
        if (autoThrowBone.value) {
            dM("Preparing throw bone")
            throwBone = true
        }
        if (autoHollowWand.value) {
            dM("Auto hollow start")
            if (mc.player?.mainHandItem?.uuid() == "HOLLOW_WAND") {
                dM("Hollow in hand -> clicking")
                schedule(firstClickDelay.value) {
                    clickByString(clickOrder.options[0])
                    schedule(secondClickDelay.value) {
                        clickByString(clickOrder.options[1])
                        schedule(2) {
                            startSequence()
                        }
                    }
                }
            } else {
                dM("Searching hollow")
                val slot = hotbarSlotFromID("HOLLOW_WAND")

                if (slot == null) {
                    dM("Hollow not found skipping")

                    startSequence()
                    return
                }

                dM("Hollow found -> swapping and clicking")

                mc.player!!.inventory.selectedSlot = slot
                schedule(firstClickDelay.value) {
                    clickByString(clickOrder.options[0])
                    schedule(secondClickDelay.value) {
                        clickByString(clickOrder.options[1])
                        schedule(2) {
                            startSequence()
                        }
                    }
                }
            }
        } else {
            startSequence()
        }
    }

    fun getRotationGoal() {
        if (!autoRotate.value) return
        schedule(delay.value) {
            val pos = mc.level?.entitiesForRendering()?.filterIsInstance<MagmaCube>()?.find { cube -> cube.isAlive && cube.size != 30 }?.position() ?: return@schedule

            dM("Found")

            when {
                pos.x < -128.0 -> {handleRotation(Vec3(-80.5, 13.0, -104.5), -30f)} //Left
                pos.z > -84.0 -> {handleRotation(Vec3(-102.5, 13.0, -82.5), -30f)} //Front
                pos.x > -72 -> {handleRotation(Vec3(-123.5, 13.0, -105.5), 0f)} //Right
                pos.z < -132 -> {handleRotation(Vec3(-99.5, 13.0, -129.5), +30f)} //Back
                else -> {
                    Chat.send("No valid kuudra spots found!")
                }
            }
        }
    }

    fun handleRotation(goal: Vec3, adjustment: Float) {
        if (minSpeed.value > maxSpeed.value) {
            Chat.send("Lock in twin")
            return
        }
        dM("Rotating")
        RotationUtils.lookAt(goal, RotationUtils.Profile(minSpeed.value.toFloat() + adjustment, maxSpeed.value.toFloat() + adjustment))
    }

    fun dM(string: String) {
        if (!debug.value) return
        Chat.send(string)
    }
}