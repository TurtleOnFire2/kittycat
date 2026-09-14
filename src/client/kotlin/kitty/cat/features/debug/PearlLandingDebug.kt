package kitty.cat.features.debug

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.utils.Chat
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.PrimitiveRenderer
import kitty.cat.render.world.RenderLayers
import kitty.cat.render.world.drawLineBox
import kitty.cat.render.world.poseScopeWithCamera
import kitty.cat.render.world.text
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Locale

object PearlLandingDebug : Feature(
    "Pearl Landing Debug",
    "Predicts impact from your thrown pearl's actual motion. Server physics and moving entities can change the result.",
    Categories.Category.DEBUG
) {
    private val debugMessages = booleanSetting("Debug messages", true, "Local chat logs for pearl tracking, prediction changes, and server teleports.")
    private val showPath = booleanSetting("Show path", true)
    private val printPosition = booleanSetting("Print landing position", true, "Prints server teleport coordinates while a pearl is active or was just removed.")
    private val keepSeconds = numberSetting("Keep after landing", 0.0, 15.0, 5.0, "s", 0.5)
    private data class Prediction(val points: List<Vec3>, val status: String, val impact: Boolean, val ticks: Int, val blockHit: BlockHitResult? = null)
    private data class Tracked(val pearl: ThrownEnderpearl, var prediction: Prediction, var removedAt: Long? = null, var lastLogAt: Long = 0L, var loggedPrediction: Prediction = prediction, val initialPrediction: Prediction = prediction)
    private val tracked = mutableMapOf<Int, Tracked>()
    private data class ThrowAttempt(val time: Long, val eye: Vec3, val direction: Vec3)
    private val throwAttempts = ArrayDeque<ThrowAttempt>()

    fun prepareUseItem(player: Player, hand: InteractionHand) {
        if (!enabled || player !== mc.player || !player.getItemInHand(hand).`is`(Items.ENDER_PEARL)) return
        val now = System.currentTimeMillis()
        throwAttempts.removeAll { now - it.time > 2000L }
        if (throwAttempts.size >= 16) throwAttempts.removeFirst()
        throwAttempts.addLast(ThrowAttempt(now, player.eyePosition, player.lookAngle))
        debug("Pearl use detected; waiting up to 2s for a nearby spawn.")
    }

    private fun debug(message: String) {
        if (debugMessages.value) Chat.send("[Pearl Debug] $message")
    }

    // Hypixel centers X/Z and uses the full-block top of the impact cell, even for partial blocks.
    private fun centeredImpact(prediction: Prediction): Vec3 {
        // Select the cell on the approach side of the hit face, not inside the wall.
        // This epsilon only resolves exact face boundaries; it does not round the trajectory.
        val hit = prediction.blockHit
        val point = if (hit == null) prediction.points.last() else hit.location.add(
            hit.direction.stepX * 1e-7, hit.direction.stepY * 1e-7, hit.direction.stepZ * 1e-7
        )
        return Vec3(kotlin.math.floor(point.x) + 0.5, kotlin.math.floor(point.y) + 1.0, kotlin.math.floor(point.z) + 0.5)
    }

    private fun describe(prediction: Prediction): String =
        "${prediction.status} at ${coordinates(prediction.points.last())}, ${prediction.ticks} ticks remaining" +
            if (prediction.impact) " | Estimated Hypixel destination ${coordinates(centeredImpact(prediction))}${impactDetails(prediction)}" else ""

    private fun impactDetails(prediction: Prediction): String {
        val hit = prediction.blockHit ?: return ""
        val point = hit.location
        // Only tangent axes are uncertain: the collision face fixes the normal coordinate.
        val edgeDistance = listOfNotNull(
            if (hit.direction.stepX == 0) kotlin.math.abs(point.x - kotlin.math.round(point.x)) else null,
            if (hit.direction.stepZ == 0) kotlin.math.abs(point.z - kotlin.math.round(point.z)) else null
        ).minOrNull() ?: 1.0
        val nearEdge = if (edgeDistance < 0.005) "; near block edge: destination uncertain" else ""
        val destination = centeredImpact(prediction)
        val player = mc.player
        val obstructed = player != null && mc.level?.noCollision(
            player, player.boundingBox.move(destination.subtract(player.position()))
        ) == false
        return " | hit=${hit.blockPos.x},${hit.blockPos.y},${hit.blockPos.z} face=${hit.direction}" +
            nearEdge + if (obstructed) "; destination obstructed: server may relocate" else ""
    }

    override fun onEnable() { debug("Enabled; waiting for your pearl spawn packet.") }
    override fun onDisable() {
        debug("Disabled; cleared ${tracked.size} tracked pearls.")
        tracked.clear()
        throwAttempts.clear()
    }

    fun handleAddEntity(packet: ClientboundAddEntityPacket) {
        if (!enabled) return
        val player = mc.player ?: return
        val pearl = mc.level?.getEntity(packet.id) as? ThrownEnderpearl ?: return
        val now = System.currentTimeMillis()
        throwAttempts.removeAll { now - it.time > 2000L }
        val knownOwner = pearl.owner == player || packet.data == player.id
        val ownerless = pearl.owner == null && packet.data == 0
        val attempt = throwAttempts.filter {
            pearl.position().distanceToSqr(it.eye) <= 9.0 &&
                pearl.deltaMovement.lengthSqr() > 1e-8 &&
                pearl.deltaMovement.normalize().dot(it.direction) >= 0.7
        }.minByOrNull { pearl.position().distanceToSqr(it.eye) }
        if (!knownOwner && !(ownerless && attempt != null)) {
            debug("Ignored pearl #${pearl.id}: owner=${pearl.owner?.id}, packet owner=${packet.data}, player=${player.id}; no matching local throw or owned by someone else")
            return
        }
        if (attempt != null) throwAttempts.remove(attempt)
        if (!knownOwner) debug("#${pearl.id}: inferred local owner from recent throw, nearby spawn, and matching direction (Hypixel fallback).")
        val prediction = predict(pearl)
        tracked[pearl.id] = Tracked(pearl, prediction, lastLogAt = System.currentTimeMillis())
        debug("Tracking #${pearl.id}: pos=${coordinates(pearl.position())}, velocity=${coordinates(pearl.deltaMovement)}")
        debug("#${pearl.id}: ${describe(prediction)}")
    }

    fun onPositionChange() {
        if (!enabled) return
        val player = mc.player ?: return
        val now = System.currentTimeMillis()
        if (tracked.values.none { entry -> entry.removedAt?.let { now - it <= 1500L } ?: true }) return
        if (debugMessages.value) {
            tracked.values.filter { it.removedAt?.let { removed -> now - removed <= 1500L } ?: true }.forEach { entry ->
                val comparisons = listOf("spawn" to entry.initialPrediction, "latest" to entry.prediction)
                    .filter { it.second.impact }.joinToString(" | ") { (label, prediction) ->
                        val center = centeredImpact(prediction)
                        val dx = player.x - center.x
                        val dz = player.z - center.z
                        val dy = player.y - center.y
                        "$label ${coordinates(center)}, horizontal error=" +
                            String.format(Locale.ROOT, "%.3f blocks, Y difference=%+.3f", kotlin.math.sqrt(dx * dx + dz * dz), dy)
                    }
                debug("Teleport comparison candidate #${entry.pearl.id}: $comparisons")
            }
        }
        // Called after vanilla applies the packet, so relative position flags are already resolved.
        // The packet has no teleport cause: label it as a server teleport, not a confirmed pearl hit.
        if (printPosition.value) Chat.send("Server teleport (pearl active/recent): ${coordinates(player.position())}")
        debug("Server position=${coordinates(player.position())}; active/recent pearl IDs=" +
            tracked.values.filter { it.removedAt?.let { removed -> now - removed <= 1500L } ?: true }
                .joinToString { "#${it.pearl.id}" } + "; teleport cause is not supplied by server.")
    }

    fun register() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            if (enabled) debug("World changed; cleared ${tracked.size} tracked pearls.")
            tracked.clear()
            throwAttempts.clear()
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!enabled || mc.level == null || mc.player == null) {
                tracked.clear()
                throwAttempts.clear()
                return@register
            }
            val now = System.currentTimeMillis()
            tracked.values.forEach { entry ->
                if (entry.pearl.isRemoved) {
                    if (entry.removedAt == null) {
                        entry.removedAt = now
                        debug("#${entry.pearl.id} removed: last observed=${coordinates(entry.pearl.position())}, reason=${entry.pearl.removalReason}; ${describe(entry.prediction)}. Removal alone does not confirm landing.")
                    }
                } else {
                    entry.prediction = predict(entry.pearl, entry.prediction)
                    val previous = entry.loggedPrediction
                    val current = entry.prediction
                    if (debugMessages.value && now - entry.lastLogAt >= 1000L &&
                        (previous.status != current.status || previous.points.last().distanceToSqr(current.points.last()) >= 0.25 ||
                            kotlin.math.floor(previous.points.last().x) != kotlin.math.floor(current.points.last().x) ||
                            kotlin.math.floor(previous.points.last().z) != kotlin.math.floor(current.points.last().z))) {
                        debug("#${entry.pearl.id} prediction changed: ${describe(current)}; velocity=${coordinates(entry.pearl.deltaMovement)}")
                        entry.lastLogAt = now
                        entry.loggedPrediction = current
                    }
                }
            }
            tracked.entries.removeIf { (_, entry) ->
                entry.removedAt?.let { now - it >= maxOf(keepSeconds.value * 1000.0, 1500.0) } ?: false
            }
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            // Capture immutable predictions before submitting deferred geometry.
            val now = System.currentTimeMillis()
            val predictions = tracked.values.filter { entry ->
                entry.removedAt?.let { now - it < keepSeconds.value * 1000.0 } ?: true
            }.map { it.prediction }
            if (showPath.value && predictions.isNotEmpty()) {
                ctx.poseStack().poseScopeWithCamera { stack ->
                    ctx.submitNodeCollector().submitCustomGeometry(stack, RenderLayers.LINES_THROUGH_WALLS) { pose, buffer ->
                        predictions.forEach { prediction ->
                            prediction.points.zipWithNext().forEach { (start, end) ->
                                PrimitiveRenderer.drawLine(pose, buffer, start, end, Color.CYAN.rgb, Color.CYAN.rgb, 1.5f)
                            }
                        }
                    }
                }
            }
            predictions.forEach { prediction ->
                val end = prediction.points.last()
                val color = if (prediction.impact) Color.GREEN else Color.ORANGE
                ctx.drawLineBox(AABB(end.x - 0.1, end.y - 0.1, end.z - 0.1, end.x + 0.1, end.y + 0.1, end.z + 0.1), color, 2f, false)
                if (prediction.impact) {
                    val center = centeredImpact(prediction)
                    ctx.drawLineBox(AABB(center.x - 0.15, center.y, center.z - 0.15, center.x + 0.15, center.y + 0.1, center.z + 0.15), Color.CYAN, 2f, false)
                    ctx.text("Estimated Hypixel destination ${coordinates(center)}${impactDetails(prediction)}", center.add(0.0, 0.8, 0.0), Color.CYAN.rgb, depth = false)
                }
                val coords = coordinates(end)
                ctx.text("${prediction.status}: $coords (${prediction.ticks} ticks)", end.add(0.0, 0.35, 0.0), color.rgb, depth = false)
            }
        }
    }

    private fun coordinates(pos: Vec3): String =
        String.format(Locale.ROOT, "%.6f, %.6f, %.6f", pos.x, pos.y, pos.z)

    private fun snapBoundary(value: Double): Double {
        val integer = kotlin.math.round(value)
        return if (kotlin.math.abs(value - integer) <= 1e-7) integer else value
    }

    private fun stableHit(hit: BlockHitResult): BlockHitResult {
        val point = hit.location
        return BlockHitResult(
            Vec3(snapBoundary(point.x), snapBoundary(point.y), snapBoundary(point.z)),
            hit.direction, hit.blockPos, hit.isInside
        )
    }

    private fun predict(pearl: ThrownEnderpearl, previous: Prediction? = null): Prediction {
        val level = pearl.level()
        var pos = pearl.position()
        var velocity = pearl.deltaMovement
        var box = pearl.boundingBox
        val points = arrayListOf(pos)
        fun result(status: String, impact: Boolean, ticks: Int, hit: BlockHitResult? = null) = Prediction(points.toList(), status, impact, ticks, hit)
        for (tick in 1..400) {
            val blockPos = BlockPos.containing(pos)
            if (!level.hasChunk(blockPos.x shr 4, blockPos.z shr 4)) return result("Unknown beyond loaded chunks", false, tick - 1)
            // These blocks can change motion or dimensions; do not label their endpoint a landing.
            if (BlockPos.betweenClosed(box).any {
                    val state = level.getBlockState(it)
                    state.`is`(Blocks.NETHER_PORTAL) || state.`is`(Blocks.END_PORTAL)
                }) return result("Special block: prediction stops", false, tick - 1)
            // 26.2 ThrowableProjectile.tick: gravity, then float air drag, then collision/movement.
            velocity = velocity.add(0.0, -if (pearl.isNoGravity) 0.0 else pearl.gravity, 0.0).scale(0.99f.toDouble())
            val next = pos.add(velocity)
            val sweep = box.expandTowards(velocity)
            for (x in (kotlin.math.floor(sweep.minX).toInt() shr 4)..(kotlin.math.floor(sweep.maxX).toInt() shr 4)) {
                for (z in (kotlin.math.floor(sweep.minZ).toInt() shr 4)..(kotlin.math.floor(sweep.maxZ).toInt() shr 4)) {
                    if (!level.hasChunk(x, z)) return result("Unknown beyond loaded chunks", false, tick - 1)
                }
            }
            val rawHit = level.clip(ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl))
            if (rawHit.type != HitResult.Type.MISS && rawHit.isInside) {
                // A client pearl can linger just inside a surface before the server removes it.
                // An inside hit is not a new surface intersection: keep the last valid impact.
                if (previous?.blockHit != null && !previous.blockHit.isInside &&
                    previous.points.last().distanceToSqr(pos) <= 0.25) {
                    return previous.copy(ticks = 0)
                }
                return result("Inside block: impact uncertain", false, tick - 1)
            }
            val blockHit = if (rawHit.type == HitResult.Type.MISS) rawHit else stableHit(rawHit)
            val end = if (blockHit.type == HitResult.Type.MISS) next else blockHit.location
            val entityHit = ProjectileUtil.getEntityHitResult(level, pearl, pos, end, sweep.inflate(1.0), {
                it !== pearl && !it.isPassengerOfSameVehicle(pearl.owner ?: mc.player ?: pearl) && it.canBeHitByProjectile()
            }, ((pearl.tickCount + tick - 3) / 20f).coerceIn(0f, 0.3f))
            if (entityHit != null) {
                points.add(entityHit.location)
                return result("Predicted entity impact", true, tick)
            }
            points.add(end)
            if (blockHit.type != HitResult.Type.MISS) return result("Predicted block impact", true, tick, blockHit)
            box = box.move(velocity)
            pos = next
            if (pos.y < level.minY) return result("Below world", false, tick)
        }
        return result("No impact within 400 ticks", false, 400)
    }
}
