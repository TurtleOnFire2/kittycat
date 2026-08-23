package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.utils.Chat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.item.Items

object RendDamage : Feature("Rend Damage", "", Categories.Category.KUUDRA) {
    val range = numberSetting("Range", 200.0, 500.0, 300.0, "ms")

    private val swings = mutableMapOf<String, Long>()

    var inP4 = false
    var client = 0
    private var lastHealth = 24999f

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            inP4 = false
            swings.clear()
            lastHealth = 24999f
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (!inP4) return@register

            client++

            mc.level?.players()?.forEach { player ->
                if (!player.swinging) return@forEach

                val item = player.mainHandItem.item
                if (item != Items.BOW && item != Items.BONE) return@forEach

                swings[player.name.string] = System.currentTimeMillis()
            }
        }
    }

    private fun addSwing(playerName: String) {
        val now = System.currentTimeMillis()

        swings[playerName] = now
    }

    fun startTracking() {
        inP4 = true
        client = 0
        swings.clear()
        lastHealth = 24999f
    }

    fun handleSetEntityData(packet: ClientboundSetEntityDataPacket) {
        if (!inP4) return

        val entity = mc.level?.getEntity(packet.id) as? MagmaCube ?: return

        if (entity.size != 30) return

        val health = (packet.packedItems
            .find { it.value is Float }
            ?.value as? Float)
            ?.takeIf { it <= 25_000f }
            ?: return

        val diff = maxOf(0f, lastHealth - health)

        if (diff > 1666f) {
            val dmg = diff * 9600f

            val fDmg = String.format("%.2fM", dmg / 1_000_000f)

            val now = System.currentTimeMillis()

            val validSwings = swings
                .filter { (_, timestamp) ->
                    now - timestamp <= range.value
                }
                .keys
                .toList()

            validSwings.forEach {
                swings.remove(it)
            }

            val time = client / 20.0

            when (validSwings.size) {
                1 -> Chat.send(
                    "${validSwings[0]} pulled for $fDmg at $time seconds."
                )

                2 -> Chat.send(
                    "${validSwings[0]} and ${validSwings[1]} pulled for $fDmg $time seconds."
                )

                3 -> Chat.send(
                    "${validSwings[0]}, ${validSwings[1]} and ${validSwings[2]} pulled for $fDmg at $time seconds."
                )

                4 -> Chat.send(
                    "${validSwings[0]}, ${validSwings[1]}, ${validSwings[2]} and ${validSwings[3]} pulled for $fDmg at $time seconds."
                )

                else -> Chat.send(
                    "Someone pulled for $fDmg at $time seconds."
                )
            }
        }

        lastHealth = health
    }
}
