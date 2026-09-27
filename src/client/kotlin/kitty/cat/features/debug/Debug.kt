package kitty.cat.features.debug

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import net.minecraft.network.protocol.Packet

object Debug: Feature("Debug", "", Categories.Category.DEBUG)  {
    val sendLocation = booleanSetting("Send location")

    val loggedPackets = selectorSetting(
        name = "Logged packets",
        options = PacketLogWindow.packetOptions,
        defaultSelected = listOf(PacketLogWindow.ALL_PACKETS),
        allowMultiple = true,
        searchable = true,
        description = "Packet types to record. Select All packets to record every type."
    )
    val openPacketLog = actionSetting("Open packet log", "Open the packet log in a separate desktop window.") {
        PacketLogWindow.open()
    }
    val clearPacketLog = actionSetting("Clear packet log") {
        PacketLogWindow.clear()
    }

    @JvmStatic
    fun logPacket(packet: Packet<*>, direction: PacketLogWindow.Direction) {
        if (!enabled) return
        val packetName = packet.javaClass.simpleName
        if (!loggedPackets.isSelected(PacketLogWindow.ALL_PACKETS) && !loggedPackets.isSelected(packetName)) return
        PacketLogWindow.log(packet, direction)
    }

    override fun onEnable() {
        PacketLogWindow.open()
    }

    override fun onDisable() {
        PacketLogWindow.close()
    }
}
