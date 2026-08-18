package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.KuudraDev
import kitty.cat.utils.BoneUtils.addBone
import kitty.cat.utils.BoneUtils.awaitBone
import kitty.cat.utils.BoneUtils.handleBone
import kitty.cat.utils.BoneUtils.throwOrigin
import kitty.cat.utils.Schedule.schedule
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.awt.Color

object KuudraUtils {

    var phase: Phase = Phase.NONE
    var activeDropOffs = mutableListOf<Triple<String, Vec3, Color>>()
    var square: Supply = Supply.None
    var isDead = false

    private val partyRegex = Regex("No (X Cannon|Triangle|X|Equals|Slash|xCannon|Shop)")

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (mc.player == null) return@register
            if ((client.player!!.y < 20 && phase == Phase.STUN) && phase != Phase.DPS) {
                phase = Phase.DPS
                Chat.send("Dps")
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            phase = Phase.NONE
            isDead = false
            activeDropOffs = dropOffs.toMutableList()
        }
    }

    fun addEntity(entity: Entity) {
        if (entity is ArmorStand && supplies()) {
            schedule(1) {
                handleArmorStand(entity.id)
            }
        }
    }

    fun handleChat(unformatted: String) {
        when (unformatted) {
            "[NPC] Elle: Talk with me to begin!" -> {
                phase = Phase.SUPPLIES
                Chat.send("Supplies")
            }
            "[NPC] Elle: OMG! Great work collecting my supplies!" -> {
                phase = Phase.BUILD
                Chat.send("Build")
            }
            "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> {
                phase = Phase.STUN
                Chat.send("Stun")
            }
        }

        if (unformatted.contains("was FINAL KILLED by Kuudra!")) isDead = true

        val match = partyRegex.find(unformatted)?.groupValues ?: return
        var string = match.lastOrNull()
        if (string == "X Cannon") string = "xCannon"
        square = Supply.valueOf(string ?: return)
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

    val dropOffs = mutableListOf(
        Triple("Shop", Vec3(-98.0, 79.0, -112.9375), Color.RED),
        Triple("X", Vec3(-106.0, 79.0, -112.9375), Color.ORANGE),
        Triple("xCannon", Vec3(-110.0, 79.0, -106.0), Color.WHITE),
        Triple("Equals", Vec3(-106.0, 79.0, -99.0625), Color.BLUE),
        Triple("Slash", Vec3(-98.0, 79.0, -99.0625), Color.GREEN),
        Triple("Triangle", Vec3(-94.0, 79.0, -106.0), Color.PINK)
    )

    val doublePearls = listOf(
        Triple("Square DP", Vec3(-141.0, 77.0, -87.0), Color.GREEN),
        Triple("Shop DP", Vec3(-76.0, 78.0, -136.0), Color.RED)
    )

    fun getSupply(): Supply {
        val x = mc.player?.position()!!.x
        val z = mc.player?.position()!!.z
        if (x in -75.0..-62.0 && z in -125.0..-115.0) return Supply.Triangle
        if (x in -94.0..-65.0 && z in -145.0..-126.0) return Supply.Shop
        if (x in -84.0..-59.0 && z in -111.0..-79.0) return Supply.Equals
        if (x in -122.0..-96.0 && z in -89.0..-36.0) return Supply.Slash
        if (x in -149.0..-129.0 && z in -97.0..-80.0) return Supply.Square
        if (x in -142.0..-124.0 && z in -131.0..-103.0) return Supply.xCannon
        if (x in -153.0..-120.0 && z in -175.0..-131.0) return Supply.X
        return Supply.None
    }

    fun handleArmorStand(id: Int) {
        val entity: Entity = mc.level?.getEntity(id) ?: return

        val item = (entity as ArmorStand).mainHandItem.item

        if (item == Items.SPRUCE_PLANKS || item == Items.ACACIA_LOG) {
            val closest = activeDropOffs.minByOrNull { it.second.distanceToSqr(entity.position()) }
            closest?.second?.distanceToSqr(entity.position())?.let { cl ->
                if (cl < 9.0)
                    closest.let {
                        activeDropOffs.remove(it)
                    }
            }
        }
    }

    enum class Supply {
        X, // -> X,
        Shop, // -> Shop
        Triangle, // -> Triangle
        Slash, // -> Slash
        Equals, // -> Equals
        Square,
        xCannon, // -> Cannon
        None
    }

    enum class Phase {
        NONE,
        SUPPLIES, //p1
        BUILD, //p2
        STUN, //p3
        DPS //p4
    }
}