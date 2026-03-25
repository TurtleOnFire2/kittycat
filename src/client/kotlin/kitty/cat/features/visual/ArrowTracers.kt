package kitty.cat.features.visual

import com.mojang.logging.LogUtils
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Pipelines
import kitty.cat.utils.ShaderedRenderer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.sqrt

object ArrowTracers : Feature("Arrow Tracers", "", Categories.Category.VISUAL) {
    private const val ACQUISITION_RANGE = 5.0
    private const val ACQUISITION_RANGE_SQ = ACQUISITION_RANGE * ACQUISITION_RANGE
    private const val MIN_SEGMENT_DISTANCE_SQ = 0.0001
    private const val BASE_WIDTH_SCALE = 0.008f
    private const val MIN_GLOW_HALF_WIDTH = 0.008f
    private const val GLOW_INNER_WIDTH_BASE = 1.4f
    private const val GLOW_INNER_WIDTH_SCALE = 1.0f
    private const val GLOW_OUTER_WIDTH_BASE = 2.4f
    private const val GLOW_OUTER_WIDTH_SCALE = 1.8f
    private const val MAX_INNER_GLOW_TO_SEGMENT = 0.45f
    private const val MAX_OUTER_GLOW_TO_SEGMENT = 0.75f

    val color = colorSetting("Color")
    private val duration = numberSetting(
        name = "Duration",
        min = 50.0,
        max = 5000.0,
        defaultValue = 600.0,
        unit = "ms",
        step = 50.0
    )
    private val lineWidth = numberSetting(
        name = "Line Width",
        min = 0.1,
        max = 4.0,
        defaultValue = 0.6,
        step = 0.1
    )
    private val glowIntensity = numberSetting(
        name = "Glow Intensity",
        min = 0.0,
        max = 4.0,
        defaultValue = 1.0,
        step = 0.05
    )
    private val trackedArrowIds = mutableSetOf<Int>()
    private val lastPositions = mutableMapOf<Int, Vec3>()
    private val trails = ArrayDeque<TrailSegment>()
    private val logger = LogUtils.getLogger()
    private var lastStatsLogMs = 0L

    private data class TrailSegment(
        val from: Vec3,
        val to: Vec3,
        val createdAtMs: Long
    )

    private data class GlowQuad(
        val x1: Float, val y1: Float, val z1: Float,
        val x2: Float, val y2: Float, val z2: Float,
        val x3: Float, val y3: Float, val z3: Float,
        val x4: Float, val y4: Float, val z4: Float,
        val alpha: Float
    )

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register tick@{ client ->
            if (!enabled) return@tick

            val now = System.currentTimeMillis()
            pruneExpiredTrails(now)

            val level = client.level
            if (level == null) {
                trackedArrowIds.clear()
                lastPositions.clear()
                trails.clear()
                return@tick
            }

            val playerPos = client.player?.position()
            if (playerPos == null) {
                trackedArrowIds.clear()
                lastPositions.clear()
                return@tick
            }

            val nearbyBox = AABB(
                playerPos.x - ACQUISITION_RANGE,
                playerPos.y - ACQUISITION_RANGE,
                playerPos.z - ACQUISITION_RANGE,
                playerPos.x + ACQUISITION_RANGE,
                playerPos.y + ACQUISITION_RANGE,
                playerPos.z + ACQUISITION_RANGE
            )

            for (entity in level.getEntitiesOfClass(Entity::class.java, nearbyBox)) {
                if (!entity.isAlive) continue
                if (entity.type != EntityType.ARROW && entity.type != EntityType.SPECTRAL_ARROW) continue
                if (entity.position().distanceToSqr(playerPos) > ACQUISITION_RANGE_SQ) continue
                trackedArrowIds.add(entity.id)
            }

            val activeTrackedIds = mutableSetOf<Int>()
            for (trackedId in trackedArrowIds) {
                val entity = level.getEntity(trackedId) ?: continue
                if (!entity.isAlive) continue
                if (entity.type != EntityType.ARROW && entity.type != EntityType.SPECTRAL_ARROW) continue

                val currentPos = entity.position()
                activeTrackedIds.add(trackedId)
                val previousPos = lastPositions.put(trackedId, currentPos) ?: continue
                if (previousPos.distanceToSqr(currentPos) > MIN_SEGMENT_DISTANCE_SQ) {
                    trails.addLast(TrailSegment(previousPos, currentPos, now))
                }
            }

            trackedArrowIds.retainAll(activeTrackedIds)
            lastPositions.entries.removeIf { it.key !in activeTrackedIds }
            logStats(now, trackedArrowIds.size, 0, 0)
        }

        WorldRenderEvents.END_MAIN.register render@{ ctx ->
            if (!enabled) return@render

            val now = System.currentTimeMillis()
            pruneExpiredTrails(now)
            if (trails.isEmpty()) return@render

            val matrices = ctx.matrices() ?: return@render
            val pose = matrices.last()
            val cameraPos = ctx.worldState().cameraRenderState.pos
            val mat = Matrix4f()
            val fallbackConsumer = ctx.consumers().getBuffer(RenderTypes.debugQuads())
            val cameraOrientation = ctx.worldState().cameraRenderState.orientation
            val cameraUp = Vector3f(0f, 1f, 0f).apply { cameraOrientation.transform(this) }
            val cameraRight = Vector3f(1f, 0f, 0f).apply { cameraOrientation.transform(this) }

            val durationMs = duration.value.toLong().coerceAtLeast(1L)
            val red = color.red / 255f
            val green = color.green / 255f
            val blue = color.blue / 255f
            val coreHalfWidth = BASE_WIDTH_SCALE * lineWidth.value.toFloat()
            val glowStrength = glowIntensity.value.toFloat().coerceAtLeast(0f)
            val glowQuads = if (glowStrength > 0f) ArrayList<GlowQuad>() else null
            var drawnSegments = 0

            ShaderedRenderer.start3D()

            for (trail in trails) {
                val ageMs = now - trail.createdAtMs
                if (ageMs < 0L) continue
                val alphaFactor = 1.0 - ageMs.toDouble() / durationMs.toDouble()
                if (alphaFactor <= 0.0) continue

                val alpha = (color.alpha / 255f * alphaFactor).toFloat()

                val x1 = (trail.from.x - cameraPos.x).toFloat()
                val y1 = (trail.from.y - cameraPos.y).toFloat()
                val z1 = (trail.from.z - cameraPos.z).toFloat()
                val x2 = (trail.to.x - cameraPos.x).toFloat()
                val y2 = (trail.to.y - cameraPos.y).toFloat()
                val z2 = (trail.to.z - cameraPos.z).toFloat()

                // direction along the segment
                val dx = x2 - x1; val dy = y2 - y1; val dz = z2 - z1
                val length = sqrt(dx * dx + dy * dy + dz * dz)
                if (length < 1.0e-4f) continue

                // Build a camera-facing perpendicular so ribbon width stays visible
                // regardless of view angle or segment orientation.
                val halfW = coreHalfWidth
                val midX = (x1 + x2) * 0.5f
                val midY = (y1 + y2) * 0.5f
                val midZ = (z1 + z2) * 0.5f
                val viewX = midX
                val viewY = midY
                val viewZ = midZ
                var cx = dy * viewZ - dz * viewY
                var cy = dz * viewX - dx * viewZ
                var cz = dx * viewY - dy * viewX
                var cLen = sqrt(cx * cx + cy * cy + cz * cz)

                if (cLen < 1.0e-4f) {
                    // Fallback 1: cross with camera up
                    cx = dy * cameraUp.z - dz * cameraUp.y
                    cy = dz * cameraUp.x - dx * cameraUp.z
                    cz = dx * cameraUp.y - dy * cameraUp.x
                    cLen = sqrt(cx * cx + cy * cy + cz * cz)
                }
                if (cLen < 1.0e-4f) {
                    // Fallback 2: cross with camera right
                    cx = dy * cameraRight.z - dz * cameraRight.y
                    cy = dz * cameraRight.x - dx * cameraRight.z
                    cz = dx * cameraRight.y - dy * cameraRight.x
                    cLen = sqrt(cx * cx + cy * cy + cz * cz)
                }
                if (cLen < 1.0e-4f) continue

                val invCLen = 1.0f / cLen
                val px = cx * invCLen * halfW
                val py = cy * invCLen * halfW
                val pz = cz * invCLen * halfW

                // core quad
                ShaderedRenderer.quad3D(
                    mat,
                    x1 + px, y1 + py, z1 + pz,
                    x2 + px, y2 + py, z2 + pz,
                    x2 - px, y2 - py, z2 - pz,
                    x1 - px, y1 - py, z1 - pz,
                    red, green, blue, alpha
                )
                fallbackConsumer.addVertex(pose, x1 + px, y1 + py, z1 + pz).setColor(red, green, blue, alpha)
                fallbackConsumer.addVertex(pose, x2 + px, y2 + py, z2 + pz).setColor(red, green, blue, alpha)
                fallbackConsumer.addVertex(pose, x2 - px, y2 - py, z2 - pz).setColor(red, green, blue, alpha)
                fallbackConsumer.addVertex(pose, x1 - px, y1 - py, z1 - pz).setColor(red, green, blue, alpha)

                if (glowStrength > 0f) {
                    val innerGlowAlpha = (alpha * 0.45f * glowStrength).coerceIn(0f, 1f)
                    if (innerGlowAlpha > 0f) {
                        val innerTarget = max(
                            halfW * (GLOW_INNER_WIDTH_BASE + GLOW_INNER_WIDTH_SCALE * glowStrength),
                            MIN_GLOW_HALF_WIDTH * (0.6f + glowStrength * 0.4f)
                        )
                        val innerHalfGlow = innerTarget.coerceAtMost(length * MAX_INNER_GLOW_TO_SEGMENT)
                        val igx = cx * invCLen * innerHalfGlow
                        val igy = cy * invCLen * innerHalfGlow
                        val igz = cz * invCLen * innerHalfGlow

                        val innerFallbackAlpha = (innerGlowAlpha * 0.22f).coerceIn(0f, 1f)
                        fallbackConsumer.addVertex(pose, x1 + igx, y1 + igy, z1 + igz).setColor(red, green, blue, innerFallbackAlpha)
                        fallbackConsumer.addVertex(pose, x2 + igx, y2 + igy, z2 + igz).setColor(red, green, blue, innerFallbackAlpha)
                        fallbackConsumer.addVertex(pose, x2 - igx, y2 - igy, z2 - igz).setColor(red, green, blue, innerFallbackAlpha)
                        fallbackConsumer.addVertex(pose, x1 - igx, y1 - igy, z1 - igz).setColor(red, green, blue, innerFallbackAlpha)

                        glowQuads?.add(
                            GlowQuad(
                                x1 + igx, y1 + igy, z1 + igz,
                                x2 + igx, y2 + igy, z2 + igz,
                                x2 - igx, y2 - igy, z2 - igz,
                                x1 - igx, y1 - igy, z1 - igz,
                                innerGlowAlpha
                            )
                        )
                    }

                    val outerGlowAlpha = (alpha * 0.2f * glowStrength).coerceIn(0f, 1f)
                    if (outerGlowAlpha > 0f) {
                        val outerTarget = max(
                            halfW * (GLOW_OUTER_WIDTH_BASE + GLOW_OUTER_WIDTH_SCALE * glowStrength),
                            MIN_GLOW_HALF_WIDTH * (1.1f + glowStrength * 0.6f)
                        )
                        val outerHalfGlow = outerTarget.coerceAtMost(length * MAX_OUTER_GLOW_TO_SEGMENT)
                        val ogx = cx * invCLen * outerHalfGlow
                        val ogy = cy * invCLen * outerHalfGlow
                        val ogz = cz * invCLen * outerHalfGlow

                        glowQuads?.add(
                            GlowQuad(
                                x1 + ogx, y1 + ogy, z1 + ogz,
                                x2 + ogx, y2 + ogy, z2 + ogz,
                                x2 - ogx, y2 - ogy, z2 - ogz,
                                x1 - ogx, y1 - ogy, z1 - ogz,
                                outerGlowAlpha
                            )
                        )
                    }
                }

                drawnSegments++
            }
            val viewMatrix = Matrix4f().rotation(ctx.worldState().cameraRenderState.orientation).invert()
            ShaderedRenderer.stop3D(Pipelines.TRAIL_3D, viewMatrix)

            if (!glowQuads.isNullOrEmpty()) {
                ShaderedRenderer.start3D()
                for (quad in glowQuads) {
                    ShaderedRenderer.quad3D(
                        mat,
                        quad.x1, quad.y1, quad.z1,
                        quad.x2, quad.y2, quad.z2,
                        quad.x3, quad.y3, quad.z3,
                        quad.x4, quad.y4, quad.z4,
                        red, green, blue, quad.alpha
                    )
                }
                ShaderedRenderer.stop3D(Pipelines.TRAIL_3D_GLOW, viewMatrix)
            }

            logStats(now, lastPositions.size, drawnSegments, glowQuads?.size ?: 0)
        }
    }

    fun handleAddEntity(packet: ClientboundAddEntityPacket) {
        // No-op: trail tracking is driven by world entity scan in END_CLIENT_TICK.
    }

    fun handleRemoveEntities(packet: ClientboundRemoveEntitiesPacket) {
        // No-op: stale ids are pruned during world scan.
    }

    override fun onDisable() {
        trackedArrowIds.clear()
        lastPositions.clear()
        trails.clear()
    }

    private fun pruneExpiredTrails(nowMs: Long) {
        val durationMs = duration.value.toLong().coerceAtLeast(1L)
        while (trails.isNotEmpty()) {
            val oldest = trails.first()
            if (nowMs - oldest.createdAtMs <= durationMs) break
            trails.removeFirst()
        }
    }

    private fun logStats(nowMs: Long, trackedArrows: Int, drawnSegments: Int, glowQuads: Int) {
        if (nowMs - lastStatsLogMs < 2000L) return
        lastStatsLogMs = nowMs
        logger.info(
            "[ArrowTracers] trackedArrows={}, trailSegments={}, drawnSegments={}, glowQuads={}",
            trackedArrows,
            trails.size,
            drawnSegments,
            glowQuads
        )
    }
}
