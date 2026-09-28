package kitty.cat.features.misc

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
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.random.Random

object FarmHelper : Feature("Farm Helper", "", Categories.Category.MISC) {

    init {
        cheat()
    }

    val autoSpawn = booleanSetting("Auto set spawn", false, "")
    val autoLoadout = booleanSetting("Auto loadout", false)

    val spawnSlot = numberSetting("Spawning loadout slot", 1.0, 12.0, 1.0, "", 1.0)
    val farmSlot = numberSetting("Farming loadout slot", 1.0, 12.0, 1.0, "", 1.0)

    val randomDelay = rangeSetting("Random delay", 0.0, 20.0, 5.0, 10.0)

    val pestCooldown = numberSetting("Pest cooldown (With this much in tablist left it swaps)", 0.0, 300.0, 170.0, "s")

    private val pestSpawnRegex = Regex("YUCK! (\\d) .+ Pest have spawned in Plot - (.+)!")
    private val cooldownRegex = Regex("\\s*Cooldown: (.+)m (.+)s")

    var ready = false
    var lastPestSpawn = -1
    var toClick = -1

    var attackAfter = false

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            lastPestSpawn = -1
        }
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
            if (autoLoadout.value) mc.connection?.sendCommand("loadout")
            toClick = spawnSlot.value.toInt()
            attackAfter = true
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

                    if (attackAfter) {
                        attackAfter = false
                        schedule(1) {
                            mc.options.keyAttack.isDown = true
                        }
                    }
                }
            }
        }
    }

    private fun delay(): Int {
        return Random.nextInt(randomDelay.lowerValue.toInt(), randomDelay.upperValue.toInt())
    }
}