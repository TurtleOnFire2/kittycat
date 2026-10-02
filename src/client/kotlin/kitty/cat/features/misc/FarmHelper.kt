package kitty.cat.features.misc

import com.mojang.blaze3d.platform.InputConstants
import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import kitty.cat.utils.LocationManager
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.clickSlot
import kitty.cat.utils.getLoadoutIndex
import kitty.cat.utils.skyblock.Island
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.decoration.ArmorStand
import org.lwjgl.glfw.GLFW
import kotlin.random.Random

object FarmHelper : Feature("Farm Helper", "", Categories.Category.MISC) {

    init {
        cheat()
    }

    val autoSpawn = booleanSetting("Auto set spawn", false, "")
    val autoLoadout = booleanSetting("Auto loadout", false)

    val spawnSlot = numberSetting("Spawning loadout slot", 1.0, 12.0, 1.0, "", 1.0)
    val farmSlot = numberSetting("Farming loadout slot", 1.0, 12.0, 1.0, "", 1.0)

    val randomDelay = rangeSetting("Random delay", 0.0, 20.0, 5.0, 10.0, "ticks", 1.0,
        "Used for loadout actions and setting spawn")

    val repressAfterLoadout = booleanSetting("Repress after loadout", true)
    val repressAfterWarp = booleanSetting("Repress after Garden warp", true)
    val loadoutRepressDelay = rangeSetting("Loadout repress delay", 0.0, 100.0, 5.0, 10.0, "ticks", 1.0)
    val warpRepressDelay = rangeSetting("Garden warp repress delay", 0.0, 100.0, 5.0, 10.0, "ticks", 1.0)
    val repressForward = booleanSetting("Repress forward", true, "Restore this direction only if it was held before the loadout swap.")
    val repressBackward = booleanSetting("Repress backward", true)
    val repressLeft = booleanSetting("Repress left", true)
    val repressRight = booleanSetting("Repress right", true)
    val repressAttack = booleanSetting("Repress attack", true, "Resume attack when restoring farming keys. Supports both Hold and Toggle Break.")
    val attackUnfocused = booleanSetting("Attack while unfocused after warp", true)
    val sneakOnWarp = booleanSetting("Sneak until grounded after warp", true)
    val focusOnPests = booleanSetting("Focus Minecraft on pest spawn", false,
        "Restore and focus the Minecraft window when pests spawn in the Garden.")

    val pestCooldown = numberSetting("Pest cooldown (With this much in tablist left it swaps)", 0.0, 300.0, 170.0, "s")

    private val pestSpawnRegex = Regex("YUCK! .*\\bPests? (?:has|have) spawned in Plot\\s*-\\s*.+!", RegexOption.IGNORE_CASE)
    private val cooldownRegex = Regex("\\s*Cooldown: (.+)m (.+)s")

    var ready = false
    var lastPestSpawn = -1
    var toClick = -1

    var repressAfter = false
    private var movementKeysToRepress = emptyList<KeyMapping>()
    private var farmingKeysToRepress = emptyList<KeyMapping>()
    private var farmingSetupRemembered = false
    private var waitingForGardenWarp = false
    private var gardenWarpArrived = false
    private var restoreFarmingKeysOnWarp = false
    private var gardenWarpRepressTicks: Int? = null
    private var gardenWarpAttackActive = false
    private var shiftUntilGround = false
    private var shiftForced = false

    override fun onDisable() {
        repressAfter = false
        movementKeysToRepress = emptyList()
        toClick = -1
        stopWarpSneak()
        farmingSetupRemembered = false
        farmingKeysToRepress = emptyList()
        waitingForGardenWarp = false
        gardenWarpArrived = false
        restoreFarmingKeysOnWarp = false
        gardenWarpRepressTicks = null
        gardenWarpAttackActive = false
    }

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            lastPestSpawn = -1
            markGardenWarpArrival()
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (LocationManager.currentArea != Island.Garden) gardenWarpAttackActive = false
            if (shiftUntilGround) {
                val player = mc.player
                if (!enabled || !sneakOnWarp.value || (LocationManager.currentArea != Island.Garden && !waitingForGardenWarp)) {
                    stopWarpSneak()
                } else if (LocationManager.currentArea == Island.Garden && player != null) {
                    if (player.onGround()) stopWarpSneak()
                    else {
                        mc.options.keyShift.isDown = true
                        shiftForced = true
                    }
                }
            }
            if (!waitingForGardenWarp || !gardenWarpArrived || !enabled ||
                LocationManager.currentArea != Island.Garden || mc.player == null || mc.gui.screen() != null) return@register
            if (!restoreFarmingKeysOnWarp || !repressAfterWarp.value) {
                waitingForGardenWarp = false
                gardenWarpArrived = false
                return@register
            }
            val ticks = gardenWarpRepressTicks ?: Random.nextInt(warpRepressDelay.lowerValue.toInt(), warpRepressDelay.upperValue.toInt() + 1)
            if (ticks > 0) {
                gardenWarpRepressTicks = ticks - 1
                return@register
            }
            waitingForGardenWarp = false
            gardenWarpArrived = false
            restoreFarmingKeysOnWarp = false
            gardenWarpRepressTicks = null
            restoreKeys(farmingKeysToRepress)
            gardenWarpAttackActive = repressAttack.value
        }
    }

    fun handleOutgoingCommand(command: String) {
        if (!enabled) return
        if (!command.trim().removePrefix("/").matches(Regex("warp\\s+garden", RegexOption.IGNORE_CASE))) return
        stopWarpSneak()
        waitingForGardenWarp = true
        gardenWarpArrived = false
        restoreFarmingKeysOnWarp = repressAfterWarp.value && farmingSetupRemembered
        gardenWarpRepressTicks = null
        gardenWarpAttackActive = false
    }

    fun shouldContinueUnfocusedAttack(): Boolean = enabled && attackUnfocused.value && repressAttack.value && gardenWarpAttackActive &&
        LocationManager.currentArea == Island.Garden && !mc.isWindowActive &&
        mc.gui.screen() == null && mc.options.keyAttack.isDown

    fun handlePositionChange() {
        markGardenWarpArrival()
    }

    private fun markGardenWarpArrival() {
        if (!waitingForGardenWarp || gardenWarpArrived) return
        gardenWarpArrived = true
        shiftUntilGround = sneakOnWarp.value
    }

    private fun stopWarpSneak() {
        if (shiftForced) {
            val key = InputConstants.getKey(mc.options.keyShift.saveString())
            mc.options.keyShift.isDown = when (key.type) {
                InputConstants.Type.KEYSYM -> key.value >= 0 && InputConstants.isKeyDown(mc.window, key.value)
                InputConstants.Type.MOUSE -> key.value >= 0 && GLFW.glfwGetMouseButton(mc.window.handle(), key.value) == GLFW.GLFW_PRESS
                else -> false
            }
        }
        shiftUntilGround = false
        shiftForced = false
    }

    fun handleTablist(packet: ClientboundPlayerInfoUpdatePacket) {
        if (!enabled || !autoLoadout.value || !LocationManager.isCurrentArea(Island.Garden)) return

        val tablistMatch = packet.entries().mapNotNull { it.displayName?.string }.firstOrNull { cooldownRegex.containsMatchIn(it) } ?: return

        val minutes = cooldownRegex.find(tablistMatch)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val seconds = cooldownRegex.find(tablistMatch)?.groupValues?.get(2)?.toIntOrNull() ?: return

        val time = 60 * minutes + seconds

        val cd = pestCooldown.value.toInt()

        if (time in cd..cd+3 && ready) {
            ready = false
            movementKeysToRepress = listOf(mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight)
                .filter { it.isDown }
            if (autoLoadout.value) mc.connection?.sendCommand("loadout")
            toClick = spawnSlot.value.toInt()
            repressAfter = repressAfterLoadout.value
        }

        ready = time > cd + 3
    }

    fun handleChat(unformatted: String) {
        if (!enabled || !LocationManager.isCurrentArea(Island.Garden)) return

        if (!pestSpawnRegex.containsMatchIn(unformatted)) return
        if (focusOnPests.value) {
            mc.execute {
                if (enabled && focusOnPests.value && LocationManager.isCurrentArea(Island.Garden)) {
                    val window = mc.window.handle()
                    if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
                        GLFW.glfwRestoreWindow(window)
                    }
                    GLFW.glfwFocusWindow(window)
                }
            }
        }

        setSpawnAfterPests()
    }

    private fun setSpawnAfterPests() {
        if (!enabled || !LocationManager.isCurrentArea(Island.Garden)) return
        if (!autoSpawn.value) return

        if (mc.player?.onGround() != true) {
            schedule(20) {
                setSpawnAfterPests()
            }
            return
        }

        schedule(delay(), true) {
            mc.player!!.connection.sendCommand("sethome")
            schedule(delay(), true) {
                if (autoLoadout.value) {
                    farmingKeysToRepress = listOf(mc.options.keyUp, mc.options.keyLeft,
                        mc.options.keyDown, mc.options.keyRight).filter { it.isDown }
                    farmingSetupRemembered = true
                    if (autoLoadout.value) mc.connection?.sendCommand("loadout")
                    toClick = farmSlot.value.toInt()
                }
            }
        }
    }

    fun openScreen(packet: ClientboundOpenScreenPacket) {
        if (!enabled || !autoLoadout.value || toClick == -1 || !LocationManager.isCurrentArea(Island.Garden)) return

        if (!packet.title.string.contains("Loadout")) {
            toClick = -1
            return
        }

        schedule(delay(), true) {
            val sc = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@schedule
            if (!sc.title.string.contains("Loadout")) return@schedule

            mc.player!!.clickSlot(sc.menu.containerId, getLoadoutIndex(toClick))
            toClick = -1

            schedule(delay()) {
                if (mc.player?.containerMenu != null) {
                    mc.player!!.closeContainer()

                    if (repressAfter) {
                        repressAfter = false
                        val movementKeys = movementKeysToRepress
                        movementKeysToRepress = emptyList()
                        schedule(Random.nextInt(loadoutRepressDelay.lowerValue.toInt(), loadoutRepressDelay.upperValue.toInt() + 1)) {
                            if (enabled && autoLoadout.value && repressAfterLoadout.value &&
                                LocationManager.isCurrentArea(Island.Garden) && mc.player != null && mc.gui.screen() == null) {
                                restoreKeys(movementKeys)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restoreKeys(keys: List<KeyMapping>) {
        keys.filter { key ->
            when (key) {
                mc.options.keyUp -> repressForward.value
                mc.options.keyDown -> repressBackward.value
                mc.options.keyLeft -> repressLeft.value
                mc.options.keyRight -> repressRight.value
                else -> false
            }
        }.forEach { it.isDown = true }
        // ToggleKeyMapping.setDown(true) flips Toggle Break, so only press when off.
        if (repressAttack.value && !mc.options.keyAttack.isDown) {
            mc.options.keyAttack.isDown = true
        }
    }

    private fun delay(): Int {
        return Random.nextInt(randomDelay.lowerValue.toInt(), randomDelay.upperValue.toInt() + 1)
    }
}
