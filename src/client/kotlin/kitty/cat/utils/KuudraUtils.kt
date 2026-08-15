package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.KuudraDev
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object KuudraUtils {

    var phase: Phase = Phase.NONE

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (mc.player == null) return@register
            if (client.player!!.y < 20 || phase == Phase.STUN) {
                phase = Phase.DPS
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            phase = Phase.NONE
        }
    }

    fun handleChat(unformatted: String) {
        when (unformatted) {
            "[NPC] Elle: Talk with me to begin!" -> phase = Phase.SUPPLIES
            "[NPC] Elle: OMG! Great work collecting my supplies!" -> phase = Phase.BUILD
            "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> phase = Phase.STUN
            else -> null
        }
    }

    fun supplies(): Boolean {
        return (phase == Phase.SUPPLIES || KuudraDev.forceSupplies.value)
    }

    fun build(): Boolean {
        return (phase == Phase.BUILD || KuudraDev.forceBuild.value)

    }

    fun stun(): Boolean {
        return (phase == Phase.STUN || KuudraDev.forceStun.value)
    }

    fun dps(): Boolean {
        return (phase == Phase.DPS || KuudraDev.forceDps.value)
    }

    fun kuudra(): Boolean {
        return (phase != Phase.NONE || KuudraDev.forceKuudra.value)
    }

    enum class Phase {
        NONE,
        SUPPLIES, //p1
        BUILD, //p2
        STUN, //p3
        DPS //p4
    }
}