package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import kitty.cat.utils.skyblock.Island
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import java.util.Locale

object LocationManager {
    var isInSkyblock: Boolean = false
        private set
    var currentArea: Island = Island.Unknown
        private set
    var lobbyId: String? = null
        private set

    private val lobbyRegex = Regex("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *")
    private val formattingRegex = Regex("§.")

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            currentArea = if (mc.hasSingleplayerServer()) Island.SinglePlayer else Island.Unknown
            isInSkyblock = false
            lobbyId = null
        }
    }

    fun handlePlayerInfo(packet: ClientboundPlayerInfoUpdatePacket) {
        if (!isCurrentArea(Island.Unknown)) return
        if (ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME !in packet.actions()) return

        val area = packet.entries().asSequence()
            .mapNotNull { it.displayName()?.string }
            .firstOrNull { it.startsWith("Area: ") || it.startsWith("Dungeon: ") }
            ?: return
        val normalized = area.lowercase(Locale.getDefault())
        currentArea = Island.entries.firstOrNull {
            normalized.contains(it.displayName.lowercase(Locale.getDefault()))
        } ?: Island.Unknown
    }

    fun handleObjective(packet: ClientboundSetObjectivePacket) {
        if (!isInSkyblock && packet.objectiveName == "SBScoreboard") isInSkyblock = true
    }

    fun handlePlayerTeam(packet: ClientboundSetPlayerTeamPacket) {
        if (!isCurrentArea(Island.Unknown)) return
        val parameters = packet.parameters.orElse(null) ?: return
        val text = (parameters.playerPrefix.string + parameters.playerSuffix.string)
            .replace(formattingRegex, "")
        lobbyRegex.find(text)?.let { lobbyId = it.groupValues[1] }
    }

    fun isCurrentArea(vararg areas: Island?): Boolean {
        if (currentArea == Island.SinglePlayer) return true
        return areas.any { it == currentArea }
    }
}
