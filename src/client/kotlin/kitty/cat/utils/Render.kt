package kitty.cat.utils

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import kitty.cat.KittycatClient.mc
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val FULL_BRIGHT = 15728880

object PrimitiveRenderer {

    private val edges = intArrayOf(
        0, 1,  1, 5,  5, 4,  4, 0,
        3, 2,  2, 6,  6, 7,  7, 3,
        0, 3,  1, 2,  5, 6,  4, 7
    )

    fun renderLineBox(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        aabb: AABB,
        color: Int,
        thickness: Float
    ) {
        val x0 = aabb.minX.toFloat()
        val y0 = aabb.minY.toFloat()
        val z0 = aabb.minZ.toFloat()
        val x1 = aabb.maxX.toFloat()
        val y1 = aabb.maxY.toFloat()
        val z1 = aabb.maxZ.toFloat()

        val corners = floatArrayOf(
            x0, y0, z0,
            x1, y0, z0,
            x1, y1, z0,
            x0, y1, z0,
            x0, y0, z1,
            x1, y0, z1,
            x1, y1, z1,
            x0, y1, z1
        )

        for (i in edges.indices step 2) {
            val i0 = edges[i] * 3
            val i1 = edges[i + 1] * 3

            val x0 = corners[i0]
            val y0 = corners[i0 + 1]
            val z0 = corners[i0 + 2]
            val x1 = corners[i1]
            val y1 = corners[i1 + 1]
            val z1 = corners[i1 + 2]

            val dx = x1 - x0
            val dy = y1 - y0
            val dz = z1 - z0

            buffer.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(thickness)
            buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(thickness)
        }
    }

    fun addChainedFilledBoxVertices(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        aabb: AABB,
        color: Int
    ) {
        val matrix = pose.pose()

        fun vertex(x: Float, y: Float, z: Float) {
            buffer.addVertex(matrix, x, y, z).setColor(color)
        }

        val minX = aabb.minX.toFloat()
        val minY = aabb.minY.toFloat()
        val minZ = aabb.minZ.toFloat()
        val maxX = aabb.maxX.toFloat()
        val maxY = aabb.maxY.toFloat()
        val maxZ = aabb.maxZ.toFloat()

        vertex(minX, minY, minZ)
        vertex(minX, minY, maxZ)
        vertex(minX, maxY, maxZ)
        vertex(minX, maxY, minZ)

        vertex(maxX, minY, maxZ)
        vertex(maxX, minY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, maxY, maxZ)

        vertex(minX, minY, minZ)
        vertex(minX, maxY, minZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, minY, minZ)

        vertex(maxX, minY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(minX, maxY, maxZ)
        vertex(minX, minY, maxZ)

        vertex(minX, minY, minZ)
        vertex(maxX, minY, minZ)
        vertex(maxX, minY, maxZ)
        vertex(minX, minY, maxZ)

        vertex(minX, maxY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, maxY, minZ)
        vertex(minX, maxY, minZ)
    }

    fun renderVector(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vec3,
        direction: Vec3,
        startColor: Int,
        endColor: Int,
        thickness: Float
    ) {
        val endX = start.x().toFloat() + direction.x.toFloat()
        val endY = start.y().toFloat() + direction.y.toFloat()
        val endZ = start.z().toFloat() + direction.z.toFloat()

        val nx = direction.x.toFloat()
        val ny = direction.y.toFloat()
        val nz = direction.z.toFloat()

        buffer.addVertex(pose, start.x().toFloat(), start.y().toFloat(), start.z().toFloat())
            .setColor(startColor)
            .setNormal(pose, nx, ny, nz)
            .setLineWidth(thickness)

        buffer.addVertex(pose, endX, endY, endZ)
            .setColor(endColor)
            .setNormal(pose, nx, ny, nz)
            .setLineWidth(thickness)
    }

    fun drawLine(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vec3,
        end: Vec3,
        startColor: Int,
        endColor: Int,
        thickness: Float
    ) {
        renderVector(pose, buffer, start, end.subtract(start), startColor, endColor, thickness)
    }

    fun renderInBatch(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        pos: BlockPos,
        neighbors: Set<BlockPos>,
        color: Int
    ) {
        val x0 = pos.x.toFloat()
        val y0 = pos.y.toFloat()
        val z0 = pos.z.toFloat()
        val x1 = x0 + 1f
        val y1 = y0 + 1f
        val z1 = z0 + 1f
        val matrix = pose.pose()

        if (pos.below() !in neighbors) {
            buffer.addVertex(matrix, x0, y0, z0).setColor(color)
            buffer.addVertex(matrix, x1, y0, z0).setColor(color)
            buffer.addVertex(matrix, x1, y0, z1).setColor(color)
            buffer.addVertex(matrix, x0, y0, z1).setColor(color)
        }
        if (pos.above() !in neighbors) {
            buffer.addVertex(matrix, x0, y1, z1).setColor(color)
            buffer.addVertex(matrix, x1, y1, z1).setColor(color)
            buffer.addVertex(matrix, x1, y1, z0).setColor(color)
            buffer.addVertex(matrix, x0, y1, z0).setColor(color)
        }
        if (pos.north() !in neighbors) {
            buffer.addVertex(matrix, x1, y0, z0).setColor(color)
            buffer.addVertex(matrix, x0, y0, z0).setColor(color)
            buffer.addVertex(matrix, x0, y1, z0).setColor(color)
            buffer.addVertex(matrix, x1, y1, z0).setColor(color)
        }
        if (pos.south() !in neighbors) {
            buffer.addVertex(matrix, x0, y0, z1).setColor(color)
            buffer.addVertex(matrix, x1, y0, z1).setColor(color)
            buffer.addVertex(matrix, x1, y1, z1).setColor(color)
            buffer.addVertex(matrix, x0, y1, z1).setColor(color)
        }
        if (pos.west() !in neighbors) {
            buffer.addVertex(matrix, x0, y0, z0).setColor(color)
            buffer.addVertex(matrix, x0, y0, z1).setColor(color)
            buffer.addVertex(matrix, x0, y1, z1).setColor(color)
            buffer.addVertex(matrix, x0, y1, z0).setColor(color)
        }
        if (pos.east() !in neighbors) {
            buffer.addVertex(matrix, x1, y0, z1).setColor(color)
            buffer.addVertex(matrix, x1, y0, z0).setColor(color)
            buffer.addVertex(matrix, x1, y1, z0).setColor(color)
            buffer.addVertex(matrix, x1, y1, z1).setColor(color)
        }
    }
}

fun LevelRenderContext.drawLine(
    startPos: Vec3,
    endPos: Vec3,
    color: Color,
    lineWidth: Float,
    depth: Boolean
) = poseStack().poseScopeWithCamera { stack ->
    val renderType = if (depth) RenderTypes.LINES else RenderLayers.LINES_THROUGH_WALLS
    submitNodeCollector().submitCustomGeometry(stack, renderType) { pose, buffer ->
        PrimitiveRenderer.drawLine(pose, buffer, startPos, endPos, color.rgb, color.rgb, lineWidth)
    }
}

fun LevelRenderContext.drawLineFromCursor(
    endPos: Vec3,
    color: Color,
    lineWidth: Float
) = poseStack().poseScopeWithCamera { stack ->
    val startPos = mc.player?.let { player ->
        player.renderPos.add(player.forward.add(0.0, player.eyeHeight.toDouble(), 0.0))
    } ?: return@poseScopeWithCamera

    submitNodeCollector().submitCustomGeometry(stack, RenderLayers.LINES_THROUGH_WALLS) { pose, buffer ->
        PrimitiveRenderer.drawLine(pose, buffer, startPos, endPos, color.rgb, color.rgb, lineWidth)
    }
}

fun LevelRenderContext.drawCircle(
    center: Vec3,
    radius: Double,
    segments: Int,
    color: Color,
    width: Float = 3.0f,
    depth: Boolean = false
) {
    val renderType = if (depth) RenderTypes.LINES else RenderLayers.LINES_THROUGH_WALLS

    poseStack().poseScopeWithCamera { stack ->
        submitNodeCollector().submitCustomGeometry(stack, renderType) { pose, buffer ->
            val angleStep = 2.0 * Math.PI / segments
            for (i in 0 until segments) {
                val angle1 = i * angleStep
                val angle2 = (i + 1) * angleStep

                val x1 = (radius * cos(angle1)).toFloat()
                val z1 = (radius * sin(angle1)).toFloat()
                val x2 = (radius * cos(angle2)).toFloat()
                val z2 = (radius * sin(angle2)).toFloat()

                val p1 = center.add(x1.toDouble(), 0.0, z1.toDouble())
                val p2 = center.add(x2.toDouble(), 0.0, z2.toDouble())

                PrimitiveRenderer.drawLine(pose, buffer, p1, p2, color.rgb, color.rgb, width)
            }
        }
    }
}

fun LevelRenderContext.drawBlockOverlay(pos: BlockPos, color: Color, depth: Boolean) {
    val level = mc.level ?: return

    val block = level.getBlockState(pos)
    val shape = block.getShape(level, pos)
    if (shape.isEmpty) return

    val renderType = if (depth) RenderTypes.debugFilledBox() else RenderLayers.QUADS_THROUGH_WALLS

    val camera = mc.gameRenderer.mainCamera().position()

    val matrices = PoseStack()
    matrices.pushPose()
    matrices.translate(
        pos.x - camera.x,
        pos.y - camera.y,
        pos.z - camera.z
    )

    submitNodeCollector().submitCustomGeometry(matrices, renderType) { pose, buffer ->
        shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            PrimitiveRenderer.addChainedFilledBoxVertices(
                pose,
                buffer,
                AABB(
                    minX * 0.999, minY * 0.999, minZ * 0.999,
                    maxX * 1.001, maxY * 1.001, maxZ * 1.001,
                ),
                color.rgb
            )
        }
    }

    matrices.popPose()
}

fun LevelRenderContext.text(
    text: String,
    pos: Vec3,
    color: Int = -1,
    scale: Float = 1.0f,
    depth: Boolean = true,
    shadow: Boolean = true
) {
    val client = Minecraft.getInstance()
    val camera = client.gameRenderer.mainCamera()

    val camPos = camera.position()
    val dx = pos.x - camPos.x
    val dy = pos.y - camPos.y
    val dz = pos.z - camPos.z
    val dist = sqrt(dx * dx + dy * dy + dz * dz).toFloat().coerceAtLeast(0.01f)

    val distFactor = (dist * 0.02f).coerceIn(0.5f, 25f)
    val finalScale = scale * distFactor

    val stack = PoseStack()
    stack.pushPose()

    val s = finalScale * 0.025f
    with(scale * 0.025f) {
        stack.translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
        stack.translate(-camPos.x, -camPos.y, -camPos.z)
        stack.last().rotate(camera.rotation())
        stack.scale(s, -s, s)
    }

    client.font.let {
        submitNodeCollector().submitText(
            stack,
            -it.width(text) / 2f,
            0f,
            FormattedCharSequence.forward(text, Style.EMPTY),
            shadow,
            if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
            color,
            0,
            FULL_BRIGHT,
            0
        )
    }

    stack.popPose()
}

fun LevelRenderContext.drawLineBox(
    aabb: AABB,
    color: Color,
    thickness: Float,
    depth: Boolean
) = poseStack().poseScopeWithCamera { stack ->
    val renderType = if (depth) RenderTypes.LINES else RenderLayers.LINES_THROUGH_WALLS
    submitNodeCollector().submitCustomGeometry(stack, renderType) { pose, buffer ->
        PrimitiveRenderer.renderLineBox(pose, buffer, aabb, color.rgb, thickness)
    }
}

fun LevelRenderContext.drawFilled(
    aabb: AABB,
    color: Color,
    depth: Boolean
) = poseStack().poseScopeWithCamera { stack ->
    val renderType = if (depth) RenderTypes.debugFilledBox() else RenderLayers.QUADS_THROUGH_WALLS
    submitNodeCollector().submitCustomGeometry(stack, renderType) { pose, buffer ->
        PrimitiveRenderer.addChainedFilledBoxVertices(pose, buffer, aabb, color.rgb)
    }
}

inline fun PoseStack.poseScopeWithCamera(block: (PoseStack) -> Unit) = poseScope {
    val camera = mc.gameRenderer.mainCamera().position()
    it.translate(-camera.x, -camera.y, -camera.z)
    block(this)
}

inline fun PoseStack.poseScope(block: (PoseStack) -> Unit) {
    this.pushPose()
    block(this)
    this.popPose()
}
