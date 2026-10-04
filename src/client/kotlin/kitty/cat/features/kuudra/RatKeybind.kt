package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.kuudra.RendMacro.loadoutSlot
import kitty.cat.features.settings.KeybindSetting
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.clickSlot
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket

object RatKeybind : Feature("Rat Keybind", "",Categories.Category.KUUDRA)  {
    val keybind = keybindSetting("Keybind")
    val delay = numberSetting("Delay", 0.0, 10.0, 0.0, "t", 1.0)

    val slot = numberSetting("Slot", 1.0, 28.0, 1.0, "", 1.0)

    var click = true

    override fun onKeybindPressed(setting: KeybindSetting) {
        if (!enabled || !kuudra() || !supplies() || mc.gui.screen() != null || click) return

        click = true
        mc.connection?.sendCommand("pets")
    }

    fun handleTitle(packet: ClientboundOpenScreenPacket) {
        if (!enabled || !kuudra() || !supplies()) return

        if (!packet.title.string.contains("Pets") || !click || mc.player == null) {
            click = false
            return
        }

        click = false

        schedule(delay.value, false) {
            val sc = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@schedule
            if (!sc.title.string.contains("Pets")) return@schedule

            mc.player!!.clickSlot(sc.menu.containerId, getSlotIndex(slot.value.toInt()))
            if (mc.player?.containerMenu != null) {
                mc.player!!.closeContainer()
            }
        }
    }

    private fun getSlotIndex(input: Int): Int {
        require(input in 1..28)
        val index = input - 1
        return 10 + (index / 7) * 9 + index % 7
    }
}