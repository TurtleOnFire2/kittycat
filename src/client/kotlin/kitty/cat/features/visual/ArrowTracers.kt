package kitty.cat.features.visual
import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Pipelines
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

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
    private val glowIntensity = numberSetting(
        name = "Glow Intensity",
        min = 0.0,
        max = 4.0,
        defaultValue = 1.0,
        step = 0.05
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
            trackedArrowIds.forEach {
                val end = mc.level?.getEntity(it)?.position()

                if (end == null) {
                    trackedArrowIds.remove(it)
                    lastPositions.remove(it)
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

            segments.entries.removeIf { (_, time) -> System.currentTimeMillis() - time > duration.value }
        }

        WorldRenderEvents.END_MAIN.register { ctx ->
            val cam = ctx.worldState().cameraRenderState.pos
            val consumers = ctx.consumers() ?: return@register
            val buf = consumers.getBuffer(Pipelines.TRAIL_GLOW_RENDER_TYPE)
            val pose = ctx.matrices().last() ?: return@register

            segments.forEach { (seg, _) ->
                val sx = (seg.start.x - cam.x).toFloat()
                val sy = (seg.start.y - cam.y).toFloat()
                val sz = (seg.start.z - cam.z).toFloat()
                val ex = (seg.end.x - cam.x).toFloat()
                val ey = (seg.end.y - cam.y).toFloat()
                val ez = (seg.end.z - cam.z).toFloat()

                val dir = Vector3f(ex - sx, ey - sy, ez - sz).normalize()
                val mid = Vector3f((sx + ex) * 0.5f, (sy + ey) * 0.5f, (sz + ez) * 0.5f)
                val toCamera = Vector3f(-mid.x, -mid.y, -mid.z).normalize()
                val basePerp = dir.cross(toCamera, Vector3f()).normalize()

                val r = color.red
                val g = color.green
                val b = color.blue
                val a = color.alpha

                val coreHalf = lineWidth.value.toFloat() * 0.02f
                val glowHalf = coreHalf * (1.5f + glowIntensity.value.toFloat() * 1.5f)
                val corePerp = Vector3f(basePerp).mul(coreHalf)
                val glowPerp = Vector3f(basePerp).mul(glowHalf)

                // core — full alpha, full bright
                buf.addVertex(pose, sx + corePerp.x, sy + corePerp.y, sz + corePerp.z).setUv(0f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, ex + corePerp.x, ey + corePerp.y, ez + corePerp.z).setUv(1f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, ex - corePerp.x, ey - corePerp.y, ez - corePerp.z).setUv(1f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, sx - corePerp.x, sy - corePerp.y, sz - corePerp.z).setUv(0f, 1f).setColor(r, g, b, a)

                // left glow
                buf.addVertex(pose, sx + glowPerp.x, sy + glowPerp.y, sz + glowPerp.z).setUv(0f, 0f).setColor(r, g, b, a)
                buf.addVertex(pose, ex + glowPerp.x, ey + glowPerp.y, ez + glowPerp.z).setUv(1f, 0f).setColor(r, g, b, a)
                buf.addVertex(pose, ex + corePerp.x, ey + corePerp.y, ez + corePerp.z).setUv(1f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, sx + corePerp.x, sy + corePerp.y, sz + corePerp.z).setUv(0f, 1f).setColor(r, g, b, a)

                // right glow
                buf.addVertex(pose, sx - corePerp.x, sy - corePerp.y, sz - corePerp.z).setUv(0f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, ex - corePerp.x, ey - corePerp.y, ez - corePerp.z).setUv(1f, 1f).setColor(r, g, b, a)
                buf.addVertex(pose, ex - glowPerp.x, ey - glowPerp.y, ez - glowPerp.z).setUv(1f, 0f).setColor(r, g, b, a)
                buf.addVertex(pose, sx - glowPerp.x, sy - glowPerp.y, sz - glowPerp.z).setUv(0f, 0f).setColor(r, g, b, a)
            }
        }
    }

    fun handleAddEntity(packet: ClientboundAddEntityPacket) {
        if (!enabled) return
        if (packet.type != EntityType.ARROW) return

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
