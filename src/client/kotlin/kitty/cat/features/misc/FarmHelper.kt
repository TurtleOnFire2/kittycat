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
        "Used for loadout actions and Garden warp key restoration")

    val pestCooldown = numberSetting("Pest cooldown (With this much in tablist left it swaps)", 0.0, 300.0, 170.0, "s")

    private val pestSpawnRegex = Regex("YUCK! (\\d) .+ Pest have spawned in Plot - (.+)!")
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
                if (!enabled || (LocationManager.currentArea != Island.Garden && !waitingForGardenWarp)) {
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
                LocationManager.currentArea != Island.Garden || mc.player == null || mc.screen != null) return@register
            if (!restoreFarmingKeysOnWarp) {
                waitingForGardenWarp = false
                gardenWarpArrived = false
                return@register
            }
            val ticks = gardenWarpRepressTicks ?: delay()
            if (ticks > 0) {
                gardenWarpRepressTicks = ticks - 1
                return@register
            }
            waitingForGardenWarp = false
            gardenWarpArrived = false
            restoreFarmingKeysOnWarp = false
            gardenWarpRepressTicks = null
            farmingKeysToRepress.forEach { it.isDown = true }
            mc.options.keyAttack.isDown = true
            gardenWarpAttackActive = true
        }
    }

    fun handleOutgoingCommand(command: String) {
        if (!enabled) return
        if (!command.trim().removePrefix("/").matches(Regex("warp\\s+garden", RegexOption.IGNORE_CASE))) return
        stopWarpSneak()
        waitingForGardenWarp = true
        gardenWarpArrived = false
        restoreFarmingKeysOnWarp = autoLoadout.value && farmingSetupRemembered
        gardenWarpRepressTicks = null
        gardenWarpAttackActive = false
    }

    fun shouldContinueUnfocusedAttack(): Boolean = enabled && gardenWarpAttackActive &&
        LocationManager.currentArea == Island.Garden && !mc.isWindowActive &&
        mc.screen == null && mc.options.keyAttack.isDown

    fun handlePositionChange() {
        markGardenWarpArrival()
    }

    private fun markGardenWarpArrival() {
        if (!waitingForGardenWarp || gardenWarpArrived) return
        gardenWarpArrived = true
        shiftUntilGround = true
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
            repressAfter = true
        }

        ready = time > cd + 3
    }

    fun handleChat(unformatted: String) {
        if (!enabled || !LocationManager.isCurrentArea(Island.Garden)) return

        val match = pestSpawnRegex.find(unformatted)?.groupValues
        val plot = match?.get(2) ?: return
        val pest = match[1].toIntOrNull() ?: return

        if (!autoSpawn.value) return

        if (mc.player?.onGround() != true) {
            schedule(20) {
                handleChat(unformatted)
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
            val sc = mc.screen as? AbstractContainerScreen<*> ?: return@schedule
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
                        schedule(delay()) {
                            mc.options.keyAttack.isDown = true
                            movementKeys.forEach { it.isDown = true }
                        }
                    }
                }
            }
        }
    }

    private fun delay(): Int {
        return Random.nextInt(randomDelay.lowerValue.toInt(), randomDelay.upperValue.toInt() + 1)
    }
}
