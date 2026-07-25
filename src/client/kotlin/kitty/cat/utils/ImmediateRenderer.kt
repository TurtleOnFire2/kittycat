package kitty.cat.utils

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import kitty.cat.KittycatClient.mc
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt

/**
 * Draws world geometry with its own RenderPass instead of the level BufferSource,
 * writing DynamicTransforms itself (modeled off Skyblocker's Renderer / vanilla GuiRenderer).
 * Record via [getBuffer] during LevelRenderEvents.END_MAIN; draws are flushed by the
 * END_MAIN handler registered in [register], which must run after all recording handlers.
 */
object ImmediateRenderer {

    private val allocators = HashMap<RenderPipeline, ByteBufferBuilder>()
    private val batches = LinkedHashMap<RenderPipeline, BufferBuilder>()

    private val modelOffset = Vector3f()
    private val textureMatrix = Matrix4f()

    fun register() {
        LevelRenderEvents.END_MAIN.register { executeDraws() }
    }

    fun getBuffer(pipeline: RenderPipeline): VertexConsumer =
        batches.getOrPut(pipeline) {
            val allocator = allocators.getOrPut(pipeline) { ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE) }
            BufferBuilder(allocator, pipeline.vertexFormatMode, pipeline.vertexFormat)
        }

    private fun executeDraws() {
        if (batches.isEmpty()) return
        batches.forEach { (pipeline, builder) ->
            val mesh = builder.build() ?: return@forEach
            draw(pipeline, mesh)
            mesh.close()
        }
        batches.clear()
    }

    private fun draw(pipeline: RenderPipeline, mesh: MeshData) {
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        RenderSystem.getProjectionType().applyLayeringTransform(modelViewStack, 1f)

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),
            Vector4f(1f, 1f, 1f, 1f),
            modelOffset,
            textureMatrix
        )

        val vertices = pipeline.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
        val indices: com.mojang.blaze3d.buffers.GpuBuffer
        val indexType: VertexFormat.IndexType
        if (pipeline.vertexFormatMode == VertexFormat.Mode.QUADS) {
            mesh.sortQuads(allocators.getValue(pipeline), RenderSystem.getProjectionType().vertexSorting())
            indices = pipeline.vertexFormat.uploadImmediateIndexBuffer(mesh.indexBuffer()!!)
            indexType = mesh.drawState().indexType()
        } else {
            val sequential = RenderSystem.getSequentialBuffer(pipeline.vertexFormatMode)
            indices = sequential.getBuffer(mesh.drawState().indexCount())
            indexType = sequential.type()
        }

        val target = mc.mainRenderTarget
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "kittycat world rendering" },
                target.colorTextureView!!,
                OptionalInt.empty(),
                if (target.useDepth) target.depthTextureView else null,
                OptionalDouble.empty()
            ).use { pass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setVertexBuffer(0, vertices)
                pass.setIndexBuffer(indices, indexType)
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
            }

        modelViewStack.popMatrix()
    }
}
