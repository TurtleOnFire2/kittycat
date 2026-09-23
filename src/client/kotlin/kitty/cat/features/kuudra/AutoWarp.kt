package kitty.cat.features.kuudra

import com.jcraft.jorbis.Block
import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.features.misc.EtherPath
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBeaconBeam
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.render.world.Render3D.renderString
import kitty.cat.utils.Chat
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.KuudraUtils
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.aabb
import kitty.cat.utils.center
import kitty.cat.utils.uuid
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.collections.contains
import kotlin.math.ceil

object AutoWarp : Feature("Auto warp", "", Categories.Category.KUUDRA) {

    init {
        cheat()
    }

    val highlightRange = booleanSetting("Highlight aura range blocks", false)
    val debug = booleanSetting("Debug (ignore missing crate)", false, "Visual debugging only: disables automatic warping.")
    val debugMessages = booleanSetting("Debug messages", false)
    val debugAreas = booleanSetting("Debug crate areas", false)
    val safeSpotAlert = booleanSetting("Safe spot alert", true)

    private const val VERTICAL_RADIUS = 6
    private const val RESCAN_TICKS = 5
    private const val FALLBACK_RADIUS = 20
    private val ownPreDestination = BlockPos(-77, 76, -138)

    private val recoveredRegex = Regex("(.+) recovered one of Elle's supplies!")

    private var ticks = 0
    private var cachedBoxes: List<BoxRender> = emptyList()
    private var lastZombiePos: Vec3? = null
    // Store geometry, not entities: unloaded zombies must remain usable without retaining the world.
    private data class SupplyZombie(val position: Vec3, val boundingBox: AABB)
    private val supplyZombieCache = mutableMapOf<KuudraUtils.Supply, SupplyZombie>()
    private var target: BlockPos? = null
    private var pendingWarpScan = false
    private var pendingOwnPreWarp = false
    private var ownPreWarpHandled = false
    private var pathfindingStartedThisWorld = false
    private var warpGeneration = 0L
    private var safeSpotAlertStartedAt = 0L

    private enum class Tier { GREEN, ORANGE, RED }
    private data class ScannedBlock(val pos: BlockPos, val tier: Tier)
    private data class SupplyArea(
        val name: String,
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double,
        val color: Color,
    )

    private val supplyAreas = listOf(
        SupplyArea("Triangle", -75.0, -62.0, -125.0, -115.0, Color(0x55D6BE)),
        SupplyArea("Shop", -94.0, -65.0, -165.0, -126.0, Color(0xFFD166)),
        SupplyArea("Equals", -84.0, -59.0, -111.0, -79.0, Color(0x06D6A0)),
        SupplyArea("Slash", -122.0, -96.0, -89.0, -36.0, Color(0x118AB2)),
        SupplyArea("Square", -169.0, -129.0, -97.0, -60.0, Color(0xEF476F)),
        SupplyArea("xCannon", -162.0, -124.0, -131.0, -103.0, Color(0xF78C6B)),
        SupplyArea("X", -153.0, -120.0, -175.0, -131.0, Color(0x9B5DE5)),
    )

    fun handleChat(unformatted: String) {
        val name = recoveredRegex.find(unformatted)?.destructured?.component1() ?: return
        if (!enabled || pathfindingStartedThisWorld) return

        val player = mc.player ?: run {
            debug("Supply placement detected, but the local player was unavailable.")
            return
        }
        if (!name.contains(player.name.string)) return

        debug("Supply placement detected for $name.")

        if (debug.value) {
            debug("Skipped: Debug (ignore missing crate) mode disables automatic warping.")
            return
        }
        if (EtherwarpWaypoints.enabled) {
            debug("Skipped: Etherwarp Waypoints is enabled and takes priority.")
            return
        }

        if (pendingOwnPreWarp) return
        val slot = selectFirstEtherwarp() ?: return

        pendingWarpScan = true
        debug("Queued warp from hotbar slot ${slot + 1}; scanning on the next client tick.")
    }

    /** Receives the actual missing crate before CratePriority maps it to a secondary. */
    fun onMissingPre(missing: Crate) {
        if (!enabled || pathfindingStartedThisWorld || debug.value || EtherwarpWaypoints.enabled || ownPreWarpHandled) return
        if (missing == Crate.NONE || missing != CratePriority.currentPre) return
        val slot = selectFirstEtherwarp() ?: return
        ownPreWarpHandled = true
        pendingWarpScan = false
        pendingOwnPreWarp = true
        debug("Own pre ${missing.name} is missing; selected slot ${slot + 1}, routing to $ownPreDestination on the next client tick.")
    }

    private fun isEtherwarp(item: ItemStack): Boolean {
        val id = item.uuid()
        return id == "ETHERWARP_CONDUIT" ||
            ((id == "ASPECT_OF_THE_VOID" || id == "ASPECT_OF_THE_END") &&
                item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("ethermerge").orElse(0) == 1)
    }

    private fun selectFirstEtherwarp(): Int? {
        val player = mc.player ?: return null
        val slot = (0..8).firstOrNull { isEtherwarp(player.inventory.getItem(it)) }
        if (slot == null) {
            debug("Skipped: no Etherwarp Conduit or Etherwarp-upgraded AOTE/AOTV in the hotbar.")
            return null
        }
        player.inventory.selectedSlot = slot
        return slot
    }

    override fun onDisable() {
        supplyZombieCache.clear()
        pendingWarpScan = false
        pendingOwnPreWarp = false
        ownPreWarpHandled = false
        warpGeneration++
    }

    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("kittycat", "auto_warp_safe_spot_alert")) { context, _ ->
            if (!enabled || !safeSpotAlert.value || safeSpotAlertStartedAt == 0L) return@addLast

            val elapsed = (System.nanoTime() - safeSpotAlertStartedAt) / 1_000_000L
            if (elapsed >= 2_000L) {
                safeSpotAlertStartedAt = 0L
                return@addLast
            }

            val text = Component.literal("SAFE SPOT").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            val pose = context.pose()
            pose.pushMatrix()
            pose.translate(context.guiWidth() / 2f, context.guiHeight() / 2f - 45f)
            pose.scale(4f)
            context.text(mc.font, text, -mc.font.width(text) / 2, -mc.font.lineHeight / 2, 0xFF55FF55.toInt())
            pose.popMatrix()
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            updateSupplyZombieCache()
            if (enabled && pendingOwnPreWarp && mc.player != null) {
                pendingOwnPreWarp = false
                if (selectFirstEtherwarp() != null) {
                    target = ownPreDestination
                    executeWarp(ownPreDestination)
                }
            } else if (enabled && pendingWarpScan && mc.player != null) {
                pendingWarpScan = false
                scanAndExecuteWarp()
            }

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

            val pos = zombie.position
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

            if (debugAreas.value) {
                supplyAreas.forEach { area ->
                    ctx.renderBoxBounds(
                        area.minX, 60.0, area.minZ,
                        area.maxX, 78.0, area.maxZ,
                        area.color,
                        Color(area.color.red, area.color.green, area.color.blue, 24),
                    )
                    ctx.renderString(
                        area.name,
                        Vec3((area.minX + area.maxX) / 2.0, 79.5, (area.minZ + area.maxZ) / 2.0),
                        area.color,
                        2f,
                    )
                }

                KuudraUtils.getSupplyZombies().forEach { zombie ->
                    val area = KuudraUtils.getSupply(zombie.position()).name
                    ctx.renderBoxBounds(zombie.boundingBox, Color.WHITE, fill = false)
                    ctx.renderString(area, zombie.position().add(0.0, zombie.bbHeight + 0.5, 0.0), Color.WHITE, 1.5f)
                }
            }
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            supplyZombieCache.clear()
            cachedBoxes = emptyList()
            lastZombiePos = null
            pendingWarpScan = false
            pendingOwnPreWarp = false
            ownPreWarpHandled = false
            pathfindingStartedThisWorld = false
            warpGeneration++
            safeSpotAlertStartedAt = 0L
        }
    }

    private fun scanAndExecuteWarp() {
        debug("Scanning for the missing-crate zombie.")

        val level = mc.level ?: run {
            debug("Skipped: the client level was unavailable.")
            return
        }
        val zombie = supplyZombieCache.entries.firstOrNull { it.key.name == CratePriority.missing.name }?.value
            ?: run {
                val detected = supplyZombieCache.keys.joinToString { it.name }
                    .ifEmpty { "none" }
                debug("Skipped: no zombie matched missing crate ${CratePriority.missing.name}; detected crates: $detected.")
                return
            }

        target = pickWarp(zombie, level)
        val destination = target ?: run {
            debug("Skipped: no standable warp destination was found near ${KuudraUtils.getSupply(zombie.position).name}.")
            return
        }

        if (safeSpotAlert.value && SafeSpots.safeSpots.any { it.safe && it.loc == destination }) {
            safeSpotAlertStartedAt = System.nanoTime()
        }

        debug("Selected ${destination.x}, ${destination.y}, ${destination.z} near ${KuudraUtils.getSupply(zombie.position).name}; finding route.")
        executeWarp(destination)
    }

    private fun executeWarp(destination: BlockPos) {
        if (pathfindingStartedThisWorld) return
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (!isEtherwarp(player.mainHandItem)) return
        // Consume the attempt before starting async work, even if routing fails or is cancelled.
        // Only a world change resets this; toggling the feature must not allow another attempt.
        pathfindingStartedThisWorld = true
        pendingWarpScan = false
        pendingOwnPreWarp = false
        val itemId = player.mainHandItem.uuid()
        val generation = ++warpGeneration
        fun canExecute() = enabled && mc.level === level && generation == warpGeneration &&
            mc.player?.mainHandItem?.let { it.uuid() == itemId && isEtherwarp(it) } == true
        EtherPath.findRoute(destination).whenComplete { route, error ->
            if (!canExecute()) return@whenComplete
            if (error != null) {
                debug("Route failed: ${error.message ?: error.javaClass.simpleName}.")
                Chat.send(error.message ?: "Etherwarp route failed.")
                return@whenComplete
            }
            debug("Route found with ${route.rotations.size} click(s).")
            val queue = queue@{
                if (!canExecute()) return@queue
                val sneak = route.eyeHeight == mc.player?.getEyeHeight(Pose.CROUCHING)?.toDouble()
                var cancelled = false
                route.hops.forEachIndexed { index, hop ->
                    ClickUtils.queueLook(hop.aim.yaw to hop.aim.pitch, sneak = sneak, resolveLook = if (index == 0) {
                        {
                            val aim = EtherPath.aimFromPlayer(hop.block, route.range)
                            if (aim == null) {
                                cancelled = true
                                Chat.send("Etherwarp cancelled: the first hop is no longer reachable.")
                            }
                            aim?.let { it.yaw to it.pitch }
                        }
                    } else null) {
                        if (!canExecute()) cancelled = true
                        !cancelled
                    }
                }
            }
            if (itemId == "ASPECT_OF_THE_VOID" && mc.player?.isShiftKeyDown == true && mc.player?.isCrouching == true) queue()
            else schedule(2) { queue() }
        }
    }

    private fun debug(message: String) {
        if (enabled && debugMessages.value) Chat.send("[Auto Warp Debug] $message")
    }

    private fun colorFor(tier: Tier): Color = when (tier) {
        Tier.GREEN -> Color.GREEN
        Tier.ORANGE -> Color.ORANGE
        Tier.RED -> Color.RED
    }

    private fun pickWarp(zombie: SupplyZombie, level: ClientLevel): BlockPos? {
        val zombiePos = zombie.position
        val scanned = scanBlocks(zombie, level)

        for (tier in Tier.entries) {
            val tierBlocks = scanned.filter { it.tier == tier }
            if (tierBlocks.isEmpty()) continue

            val safeSpotMatch = tierBlocks.filter { block ->
                SafeSpots.safeSpots.any { it.safe && it.loc == block.pos }
            }.minByOrNull { it.pos.center().distanceToSqr(zombiePos) }
            if (safeSpotMatch != null) return safeSpotMatch.pos

            return tierBlocks.minByOrNull { it.pos.center().distanceToSqr(zombiePos) }?.pos
        }

        // Green/orange/red all came up empty (e.g. the zombie is stuck somewhere with no standable ground nearby) - just grab the nearest standable block, range be damned.
        return nearestStandableBlock(zombie.position, level)
    }

    private fun nearestStandableBlock(origin: Vec3, level: ClientLevel): BlockPos? {
        val originBlock = BlockPos.containing(origin)
        var best: BlockPos? = null
        var bestDistSq = Double.MAX_VALUE

        for (dx in -FALLBACK_RADIUS..FALLBACK_RADIUS) {
            for (dz in -FALLBACK_RADIUS..FALLBACK_RADIUS) {
                for (dy in -VERTICAL_RADIUS..VERTICAL_RADIUS) {
                    val pos = originBlock.offset(dx, dy, dz)
                    if (!isWithinWarpArea(pos)) continue
                    if (pos in blacklist) continue
                    val state = level.getBlockState(pos)
                    if (state.`is`(Blocks.BARRIER)) continue
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

    private fun updateSupplyZombieCache() {
        if (!enabled || !kuudra() || !supplies() || mc.level == null) {
            supplyZombieCache.clear()
            return
        }
        // Nearest matching zombie wins if multiple loaded zombies occupy the same supply area.
        val seen = mutableSetOf<KuudraUtils.Supply>()
        for (zombie in KuudraUtils.getSupplyZombies()) {
            val supply = KuudraUtils.getSupply(zombie.position())
            if (supply == KuudraUtils.Supply.None || !seen.add(supply)) continue
            supplyZombieCache[supply] = SupplyZombie(zombie.position(), zombie.boundingBox)
        }
    }

    private fun zombieForMissingCrate(): SupplyZombie? {
        if (debug.value) {
            val player = mc.player ?: return null
            return supplyZombieCache.values.minByOrNull { it.position.distanceToSqr(player.position()) }
        }
        return supplyZombieCache.entries.firstOrNull { it.key.name == CratePriority.missing.name }?.value
    }

    private fun scanBlocks(
        zombie: SupplyZombie,
        level: ClientLevel,
    ): List<ScannedBlock> {
        val player = mc.player ?: return emptyList()
        val eyeHeight = player.eyeHeight.toDouble()
        val box = zombie.boundingBox
        val green = SupplyCheats.auraRange.value
        val orange = green + 2.0
        val red = green + 4.0

        val horizontalRadius = ceil(red).toInt()
        val originBlock = BlockPos.containing(zombie.position)
        val results = ArrayList<ScannedBlock>()

        for (dx in -horizontalRadius..horizontalRadius) {
            for (dz in -horizontalRadius..horizontalRadius) {
                // Cheap prune before touching any block state: horizontal distance alone is a lower bound on the real distance.
                if (dx * dx + dz * dz > red * red) continue

                for (dy in -VERTICAL_RADIUS..VERTICAL_RADIUS) {
                    val pos = originBlock.offset(dx, dy, dz)
                    if (!isWithinWarpArea(pos)) continue
                    if (pos in blacklist) continue
                    val state = level.getBlockState(pos)
                    if (state.`is`(Blocks.BARRIER)) continue
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

    private fun isWithinWarpArea(pos: BlockPos): Boolean =
        pos.x in -143..-68 && pos.z in -139..-82

    val blacklist = listOf(
        BlockPos(-138, 74, -137),
        BlockPos(-138, 76, -136),
        BlockPos(-138, 74, -133),
        BlockPos(-137, 74, -131),
        BlockPos(-137, 74, -125),
        BlockPos(-130, 75, -120),
        BlockPos(-132, 76, -114),
        BlockPos(-133, 75, -113),
        BlockPos(-133, 77, -112),
        BlockPos(-133, 76, -111),
        BlockPos(-73, 74, -139),
        BlockPos(-78, 74, -137),
        BlockPos(-82, 74, -134),
        BlockPos(-82, 75, -133),
        BlockPos(-83, 75, -132),
        BlockPos(-83, 76, -131),
        BlockPos(-84, 74, -133),
        BlockPos(-86, 74, -130),
     )
}
