package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.settings.RangeSetting
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
import net.minecraft.commands.arguments.SlotArgument.slot
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object RendMacro : Feature("Rend Macro", "", Categories.Category.KUUDRA) {
    val autoSneak = booleanSetting("Auto sneak", false)

    val autoRotate = booleanSetting("Auto rotate", false)
    val front = rangeSetting("Front" , 50.0, 500.0, 85.0, 145.0, "", 1.0)
    val right = rangeSetting("Right" , 50.0, 500.0, 235.0, 295.0, "", 1.0)
    val left = rangeSetting("Left" , 50.0, 500.0, 235.0, 295.0, "", 1.0)
    val back = rangeSetting("Back" , 50.0, 500.0, 285.0, 345.0, "", 1.0)

    val delay = numberSetting("Rotation delay", 1.0, 20.0, 5.0, "t", 1.0)
    val autoWalk = booleanSetting("Auto walk", false)
    val autoJump = booleanSetting("Auto jump", false)
    val autoHollowWand = booleanSetting("Auto hollow wand", false)
    val firstClickDelay = numberSetting("Click delay first hollow click", 1.0, 10.0, 1.0, "t", 1.0)
    val secondClickDelay = numberSetting("Click delay second hollow click", 1.0, 10.0, 1.0, "t", 1.0)
    val clickOrder = orderSetting("Click order", listOf("Left click", "Right click"))
    val autoRod = booleanSetting("Auto rod", false)
    val offsetRight = numberSetting("Offset right",  -2.5, 2.5, 0.0)
    val offsetLeft = numberSetting("Offset left",  -2.5, 2.5, 0.0)
    val offsetBack = numberSetting("Offset back",  -2.5, 2.5, 0.0)
    val offsetFront = numberSetting("Offset front",  -2.5, 2.5, 0.0)
    val renderArea = booleanSetting("Render area", false, "Rendering is broken only for this? Idk why")
    val autoBone = booleanSetting("Auto bone", false)
    val boneDelay = numberSetting("Bone delay", 1.0, 20.0, 6.0, "t", 1.0)
    val autoHalberd = booleanSetting("Auto halberd", false)
    val autoLoadout = booleanSetting("Auto loadout", false)
    val loadoutSlot = numberSetting("Loadout slot", 1.0, 14.0, 1.0, "", 1.0)
    val clickDelay = numberSetting("Click delay", 1.0, 10.0, 1.0, "t", 1.0)
    val autoPull = booleanSetting("Auto pull on backbone", false)
    val pullItemSlot = numberSetting("Pull item slot", 1.0, 8.0, 1.0, "", 1.0)
    val pullDelay = numberSetting("Pull delay", 1.0, 20.0, 1.0, "t", 1.0)

    val debug = booleanSetting("Debug", false)
    val key = keybindSetting("Trigger")

    private var clickLoadout = false

    private var edging = false
    private var throwRod = false

    private var down = false

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (mc.level == null || mc.player == null) return@register

            checkRodAndEdge()

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
            throwRod = false
        }
    }

    fun onPositionChange(packet: ClientboundPlayerPositionPacket) {
        if (!enabled || !kuudra()) return

        if (!stun() && !dps()) return

        val pos = packet.change.position

        val x = pos.x; val y = pos.y; val z = pos.z
        if (x !in -102.0..-101.0 || y !in 5.0..7.0 || z !in -106.0..-105.0) return

        getRotationGoal()

        if (autoSneak.value) {
            dM("Sneaking")
            mc.options.keyShift.isDown = true
            schedule(2) { mc.options.keyShift.isDown = false }
        }
        if (autoJump.value) {
            dM("Jumping")
            edging = true
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
                            prepareRod()
                        }
                    }
                }
            } else {
                dM("Searching hollow")
                val slot = hotbarSlotFromID("HOLLOW_WAND")

                if (slot == null) {
                    dM("Hollow not found skipping")

                    prepareRod()
                    return
                }

                dM("Hollow found -> swapping and clicking")

                mc.player!!.inventory.selectedSlot = slot
                schedule(firstClickDelay.value) {
                    clickByString(clickOrder.options[0])
                    schedule(secondClickDelay.value) {
                        clickByString(clickOrder.options[1])
                        schedule(2) {
                            prepareRod()
                        }
                    }
                }
            }
        } else {
            prepareRod()
        }
    }

    fun prepareRod() {
        if (!enabled) return

        if (!dps() && !stun()) return

        dM("Looking for rod")

        val rodSlot = hotbarSlotFromItem(Items.FISHING_ROD) ?: return
        dM("Rod found")
        if (autoRod.value) {
            throwRod = true
            mc.player!!.inventory.selectedSlot = rodSlot
        }
    }

    fun checkRodAndEdge() {
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
            throwRod && (
                    pos.x !in (-111.0 - offsetRight.value)..(-95.0 + offsetLeft.value)
                            || pos.z !in (-118.0 - offsetBack.value)..(-97.0 + offsetFront.value))
        ) {
            if (mc.player?.mainHandItem?.item != Items.FISHING_ROD) return
            dM("Throwing rod")
            mc.options.keyUse.clickCount++
            throwRod = false
        }
    }

    fun useItem(player: Player, interactionHand: InteractionHand, result: InteractionResult) {
        if (!enabled) return

        if (!kuudra() || player.y > 20) return

        if (result !is InteractionResult.Pass && result !is InteractionResult.Success) return

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
                mc.options.keyUp.isDown = false
            }
        }

        if (item.item == Items.FISHING_ROD) {
            dM("Rod thrown")

            if (autoBone.value) {
                val boneSlot = hotbarSlotFromID("STARRED_BONE_BOOMERANG") ?: return
                mc.player!!.inventory.selectedSlot = boneSlot
                schedule(boneDelay.value) {
                    mc.options.keyUse.clickCount++
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
        dM(string)
        if (string == "Left click") {
            mc.options.keyAttack.clickCount++
        } else if (string == "Right click") {
            mc.options.keyUse.clickCount++
        }
    }

    fun getRotationGoal() {
        if (!autoRotate.value) return
        schedule(delay.value) {
            dM("Searching for kuudra...")
            val pos = mc.level?.entitiesForRendering()?.filterIsInstance<MagmaCube>()?.find { cube ->
                cube.isAlive &&
                        cube.size == 30 &&
                        cube.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100_000.0
            }?.position() ?: return@schedule

            dM("Found at ${pos.x}, ${pos.z}")

            when {
                pos.x < -128.0 -> handleRotation(Vec3(-123.5, 16.0, -105.5), right)
                pos.z > -84.0  -> handleRotation(Vec3(-104.5, 16.0, -82.5), front)
                pos.x > -72.0  -> handleRotation(Vec3(-80.5, 16.0, -104.5), left)
                pos.z < -132.0 -> handleRotation(Vec3(-98.5, 16.0, -129.5), back)
                else -> {
                    Chat.send("No valid kuudra spots found!")
                }
            }
        }
    }

    fun handleRotation(goal: Vec3, c: RangeSetting) {
        dM("Rotating")
        RotationUtils.lookAt(goal, pitch = -30f, RotationUtils.Profile(c.lowerValue.toFloat(), c.upperValue.toFloat()), walk = autoWalk.value, onComplete = {
            if (autoWalk.value) mc.options.keyUp.isDown = true
            Chat.send("yo")
        })
    }

    fun onBackbone() {
        if (!kuudra() || !enabled || !dps() || !autoPull.value || mc.screen != null) return

        mc.player?.inventory?.selectedSlot = pullItemSlot.value.toInt() - 1

        schedule(pullDelay.value) {
            mc.options.keyAttack.clickCount++
        }
    }

    fun dM(string: String) {
        if (!debug.value) return
        Chat.send(string)
    }
}
