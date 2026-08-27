package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.KuudraDev
import kitty.cat.features.kuudra.RendDamage
import kitty.cat.utils.Schedule.schedule
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.awt.Color

object KuudraUtils {

    private val armorSlots = EquipmentSlot.entries.filter { it.type == EquipmentSlot.Type.HUMANOID_ARMOR }
    private var entityCacheTick = Long.MIN_VALUE
    private var entityCache = emptyList<Entity>()
    private var supplyZombieCacheTick = Long.MIN_VALUE
    private var supplyZombieCache = emptyList<Zombie>()

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
                RendDamage.startTracking()
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            phase = Phase.NONE
            isDead = false
            activeDropOffs = dropOffs.toMutableList()
            entityCacheTick = Long.MIN_VALUE
            entityCache = emptyList()
            supplyZombieCacheTick = Long.MIN_VALUE
            supplyZombieCache = emptyList()
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
            }
            "[NPC] Elle: OMG! Great work collecting my supplies!" -> {
                phase = Phase.BUILD
            }
            "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> {
                phase = Phase.STUN
            }
        }

        if (unformatted.contains("${mc.player?.name?.string ?: return} was FINAL KILLED by Kuudra!")) isDead = true

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
        Triple("Square DP", Vec3(-140.0, 77.0, -87.0), Color.GREEN),
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

    fun getSupplyZombies(): List<Zombie> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()

        entitiesForRendering()
        if (supplyZombieCacheTick == level.gameTime) return supplyZombieCache

        supplyZombieCache = entityCache
            .filterIsInstance<Zombie>()
            .filter {
                it.isAlive &&
                        !it.isBaby &&
                        it.y in 60.0..78.0 &&
                        armorSlots.all { slot -> it.getItemBySlot(slot).isEmpty }
            }
            .sortedBy { it.distanceToSqr(player) }
        supplyZombieCacheTick = level.gameTime
        return supplyZombieCache
    }

    /** A stable entity snapshot shared by all feature scans during one client tick. */
    fun entitiesForRendering(): List<Entity> {
        val level = mc.level ?: return emptyList()
        if (entityCacheTick != level.gameTime) {
            entityCacheTick = level.gameTime
            entityCache = level.entitiesForRendering().toList()
            supplyZombieCacheTick = Long.MIN_VALUE
            supplyZombieCache = emptyList()
        }
        return entityCache
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
