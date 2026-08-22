package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

object RendDamage : Feature("Rend Damage", "", Categories.Category.KUUDRA) {
    val range = numberSetting("Range", 200.0, 500.0, 300.0, "ms")

    private val swings = mutableListOf<Pair<String, Long>>()

    var inP4 = false
    var client = 0
    private var lastHealth = 24999f

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, event ->
            inP4 = false
            swings.clear()
            lastHealth = 24999f
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (inP4) client++
        }
    }

    fun startTracking() {
        inP4 = true
        client++
        swings.clear()
        lastHealth = 24999f
    }

    fun handleAnimation(packet: ClientboundAnimatePacket) {
        if (packet.action != ClientboundAnimatePacket.SWING_MAIN_HAND) return

        val player = mc.level?.getEntity(packet.id) as? Player ?: return
        player.mainHandItem.takeIf { it.item == Items.BOW } ?: return

        swings.removeAll { it.first == player.name.string }
        swings.add(player.name.string to System.currentTimeMillis())
    }

    fun handleSetEntityData(packet: ClientboundSetEntityDataPacket) {
        if (!inP4) return

        val entity = mc.level?.getEntity(packet.id) as? MagmaCube ?: return

        if (entity.size != 30) return

        val health = (packet.packedItems.find { data -> data.value is Float }?.value as? Float).takeIf { it != null && it < 25000 } ?: return

        val diff = maxOf(0f, lastHealth - health)

        if (diff > 1666f) {
            val dmg = diff * 9600f

            val validSwings = swings.filter { swing -> System.currentTimeMillis() - swing.second < range.value }

            swings.removeAll(validSwings)

            val time = client / 20.0

            when (validSwings.size) {
                1 -> {
                    Chat.send("${validSwings[0].first} pulled for $dmg at $time seconds.")
                }
                2 -> {
                    Chat.send("${validSwings[0].first} and ${validSwings[1].first} pulled for $dmg at $time seconds.")
                }
                3 -> {
                    Chat.send("${validSwings[0].first}, ${validSwings[1].first} and ${validSwings[2].first} pulled for $dmg at $time seconds.")
                }
                4 -> {
                    Chat.send("${validSwings[0].first}, ${validSwings[1].first}, ${validSwings[2].first} and ${validSwings[3].first} pulled for $dmg at $time seconds.")
                }
                else -> {
                    Chat.send("Someone pulled for $dmg at $time seconds.")
                }
            }
        }

        lastHealth = health
    }
}
