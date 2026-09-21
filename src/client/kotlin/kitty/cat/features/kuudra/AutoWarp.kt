package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.aabb
import kitty.cat.utils.center
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.ceil

object AutoWarp : Feature("Auto warp", "", Categories.Category.KUUDRA) {

    val highlightRange = booleanSetting("Highlight aura range blocks", false)
    val debug = booleanSetting("Debug (ignore missing crate)", false)
    val beaconColor = colorSetting("Beacon color")

    private const val VERTICAL_RADIUS = 6
    private const val RESCAN_TICKS = 5
    private const val FALLBACK_RADIUS = 20

    private val recoveredRegex = Regex("(.+) recovered one of Elle's supplies!")

    private var ticks = 0
    private var cachedBoxes: List<BoxRender> = emptyList()
    private var lastZombiePos: Vec3? = null
    private var beaconTarget: BlockPos? = null

    private enum class Tier { GREEN, ORANGE, RED }
    private data class ScannedBlock(val pos: BlockPos, val tier: Tier)

    fun handleChat(unformatted: String) {
        if (!enabled || debug.value) return
        val player = mc.player ?: return

        val name = recoveredRegex.find(unformatted)?.destructured?.component1() ?: return
        if (!name.contains(player.name.string)) return

        val level = mc.level ?: return
        val zombie = zombieForMissingCrate() ?: return

        beaconTarget = pickBeaconTarget(zombie, level)
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled || !highlightRange.value || !kuudra() || !supplies()) {
                if (cachedBoxes.isNotEmpty()) cachedBoxes = emptyList()
                lastZombiePos = null
                return@register
            }

            if (ticks++ % RESCAN_TICKS != 0) return@register

            val level = mc.level ?: return@register
            val zombie = zombieForMissingCrate()

            if (zombie == null) {
                if (cachedBoxes.isNotEmpty()) cachedBoxes = emptyList()
                lastZombiePos = null
                return@register
            }

            val pos = zombie.position()
            // Zombies don't move, don't redo the scan while it's still standing in the same spot.
            if (lastZombiePos == pos) return@register

            cachedBoxes = scanBlocks(zombie, level).map { BoxRender(it.pos.aabb(), colorFor(it.tier)) }
            lastZombiePos = pos
        }

        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register

            if (highlightRange.value && cachedBoxes.isNotEmpty()) {
                ctx.renderBoxesBounds(cachedBoxes)
            }

            beaconTarget?.let { ctx.renderBeaconBeam(it.center(), beaconColor.color) }
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            cachedBoxes = emptyList()
            lastZombiePos = null
            beaconTarget = null
        }
    }

    private fun colorFor(tier: Tier): Color = when (tier) {
        Tier.GREEN -> Color.GREEN
        Tier.ORANGE -> Color.ORANGE
        Tier.RED -> Color.RED
    }

    private fun pickBeaconTarget(zombie: Zombie, level: ClientLevel): BlockPos? {
        val player = mc.player ?: return null
        val playerPos = player.position()
        val scanned = scanBlocks(zombie, level)

        for (tier in Tier.entries) {
            val tierBlocks = scanned.filter { it.tier == tier }
            if (tierBlocks.isEmpty()) continue

            val safeSpotMatch = tierBlocks.firstOrNull { block ->
                SafeSpots.safeSpots.any { it.safe && it.loc == block.pos }
            }
            if (safeSpotMatch != null) return safeSpotMatch.pos

            return tierBlocks.minByOrNull { it.pos.center().distanceToSqr(playerPos) }?.pos
        }

        // Green/orange/red all came up empty (e.g. the zombie is stuck somewhere with no standable ground nearby) - just grab the nearest standable block, range be damned.
        return nearestStandableBlock(zombie.position(), level)
    }

    private fun nearestStandableBlock(origin: Vec3, level: ClientLevel): BlockPos? {
        val originBlock = BlockPos.containing(origin)
        var best: BlockPos? = null
        var bestDistSq = Double.MAX_VALUE

        for (dx in -FALLBACK_RADIUS..FALLBACK_RADIUS) {
            for (dz in -FALLBACK_RADIUS..FALLBACK_RADIUS) {
                for (dy in -VERTICAL_RADIUS..VERTICAL_RADIUS) {
                    val pos = originBlock.offset(dx, dy, dz)
                    val state = level.getBlockState(pos)
                    if (state.isAir || state.getCollisionShape(level, pos).isEmpty) continue
                    if (!level.getBlockState(pos.above()).isAir) continue

                    val distSq = pos.center().distanceToSqr(origin)
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq
                        best = pos
                    }
                }
            }
        }

        return best
    }

    private fun zombieForMissingCrate(): Zombie? {
        // Already sorted by distance to the player.
        val zombies = KuudraUtils.getSupplyZombies()

        if (debug.value) return zombies.firstOrNull()

        return zombies.firstOrNull { KuudraUtils.getSupply(it.position()).name == CratePriority.missing.name }
    }

    private fun scanBlocks(zombie: Zombie, level: ClientLevel): List<ScannedBlock> {
        val player = mc.player ?: return emptyList()
        val eyeHeight = player.eyeHeight.toDouble()
        val box = zombie.boundingBox
        val green = SupplyCheats.auraRange.value
        val orange = green + 2.0
        val red = green + 4.0

        val horizontalRadius = ceil(red).toInt()
        val originBlock = BlockPos.containing(zombie.position())
        val results = ArrayList<ScannedBlock>()

        for (dx in -horizontalRadius..horizontalRadius) {
            for (dz in -horizontalRadius..horizontalRadius) {
                // Cheap prune before touching any block state: horizontal distance alone is a lower bound on the real distance.
                if (dx * dx + dz * dz > red * red) continue

                for (dy in -VERTICAL_RADIUS..VERTICAL_RADIUS) {
                    val pos = originBlock.offset(dx, dy, dz)
                    val state = level.getBlockState(pos)
                    if (state.isAir || state.getCollisionShape(level, pos).isEmpty) continue
                    if (!level.getBlockState(pos.above()).isAir) continue

                    // Standing dead-center on the block, same reach check as SupplyCheats.isInRange.
                    val eyePoint = Vec3(pos.x + 0.5, pos.y + 1.0 + eyeHeight, pos.z + 0.5)
                    val closestOnBox = Vec3(
                        eyePoint.x.coerceIn(box.minX, box.maxX),
                        eyePoint.y.coerceIn(box.minY, box.maxY),
                        eyePoint.z.coerceIn(box.minZ, box.maxZ)
                    )
                    val dist = eyePoint.distanceTo(closestOnBox)

                    val tier = when {
                        dist <= green -> Tier.GREEN
                        dist <= orange -> Tier.ORANGE
                        dist <= red -> Tier.RED
                        else -> continue
                    }

                    results.add(ScannedBlock(pos, tier))
                }
            }
        }

        return results
    }
}
