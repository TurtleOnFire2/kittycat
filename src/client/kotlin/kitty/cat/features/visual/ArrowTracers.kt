package kitty.cat.features.visual

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import com.mojang.blaze3d.vertex.PoseStack
import kitty.cat.utils.entityType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object ArrowTracers : Feature("Arrow Tracers", "", Categories.Category.VISUAL) {

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

    private val trackedArrowIds = mutableSetOf<Int>()
    private val lastPositions = mutableMapOf<Int, Vec3>()
    private val segments = mutableMapOf<Segment, Long>()

    data class Segment(
        val start: Vec3,
        val end: Vec3
    )

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val toRemove = mutableSetOf<Int>()

            trackedArrowIds.forEach {
                val end = mc.level?.getEntity(it)?.position()

                if (end == null) {
                    toRemove.add(it)
                    return@forEach
                }

                val start = lastPositions[it] ?: run {
                    lastPositions[it] = end
                    return@forEach
                }

                if (start != end) {
                    segments[Segment(start, end)] = System.currentTimeMillis()
                    lastPositions[it] = end
                }
            }

            toRemove.forEach {
                trackedArrowIds.remove(it)
                lastPositions.remove(it)
            }

            segments.entries.removeIf { (_, time) ->
                System.currentTimeMillis() - time > duration.value
            }
        }

        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register
            if (segments.isEmpty()) return@register

            val cam = ctx.levelState().cameraRenderState.pos
            val stack = PoseStack()
            stack.translate(-cam.x, -cam.y, -cam.z)

            val r = color.red
            val g = color.green
            val b = color.blue
            val a = color.alpha

            ctx.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.linesTranslucent()) { pose, buf ->
                segments.forEach { (seg, _) ->
                    val sx = seg.start.x.toFloat()
                    val sy = seg.start.y.toFloat()
                    val sz = seg.start.z.toFloat()
                    val ex = seg.end.x.toFloat()
                    val ey = seg.end.y.toFloat()
                    val ez = seg.end.z.toFloat()

                    val dx = ex - sx
                    val dy = ey - sy
                    val dz = ez - sz
                    val len = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                    val nx = dx / len
                    val ny = dy / len
                    val nz = dz / len

                    buf.addVertex(pose, sx, sy, sz)
                        .setColor(r, g, b, a)
                        .setNormal(nx, ny, nz)
                        .setLineWidth(lineWidth.value.toFloat())

                    buf.addVertex(pose, ex, ey, ez)
                        .setColor(r, g, b, a)
                        .setNormal(nx, ny, nz)
                        .setLineWidth(lineWidth.value.toFloat())
                }
            }
        }
    }

    fun handleAddEntity(packet: ClientboundAddEntityPacket) {
        if (!enabled) return
        if (packet.type != entityType("arrow")) return

        val player = Minecraft.getInstance().player ?: return
        val dx = packet.x - player.x
        val dy = packet.y - player.y
        val dz = packet.z - player.z
        if (dx * dx + dy * dy + dz * dz > 25) return

        trackedArrowIds.add(packet.id)
        lastPositions[packet.id] = Vec3(packet.x, packet.y, packet.z)
    }

    fun handleRemoveEntities(packet: ClientboundRemoveEntitiesPacket) {
        val iterator = packet.entityIds.intIterator()
        while (iterator.hasNext()) {
            val removedId = iterator.nextInt()
            trackedArrowIds.remove(removedId)
        }
    }
}
