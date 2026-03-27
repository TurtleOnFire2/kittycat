package kitty.cat.utils

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.util.OptionalDouble
import java.util.OptionalInt

object ShaderedRenderer {

    private var allocator2D: ByteBufferBuilder? = null
    private var allocator3D: ByteBufferBuilder? = null

    private var buffer2D: BufferBuilder? = null
    private var buffer3D: BufferBuilder? = null

    // ── 2D ───────────────────────────────────────────────────────────────────

    fun start2D() {
        allocator2D = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
        buffer2D = BufferBuilder(allocator2D!!, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    }

    fun quad2D(matrix: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) {
        val buf = buffer2D ?: error("call start2D() first")
        buf.addVertex(matrix, x1, y1, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, x1, y2, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, x2, y2, 0f).setColor(r, g, b, a)
        buf.addVertex(matrix, x2, y1, 0f).setColor(r, g, b, a)
    }

    fun stop2D(pipeline: RenderPipeline) {
        val buf = buffer2D ?: return
        buffer2D = null
        val built = buf.build() ?: return
        val drawState = built.drawState()
        if (drawState.vertexCount() == 0) { built.close(); allocator2D = null; return }

        val byteBuffer = built.vertexBuffer()

        val vertexBuf: GpuBuffer = pipeline.vertexFormat
            .uploadImmediateVertexBuffer(byteBuffer)

        val shapeIndexBuffer = RenderSystem.getSequentialBuffer(drawState.mode())
        val indexBuf: GpuBuffer = shapeIndexBuffer.getBuffer(drawState.indexCount())
        val indexType = shapeIndexBuffer.type()

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f()
        )

        val client = Minecraft.getInstance()
        val colorView = client.mainRenderTarget.colorTextureView!!
        val depthView = client.mainRenderTarget.depthTextureView!!

        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(
                { "kitty trail render" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ).use { pass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setVertexBuffer(0, vertexBuf)
                pass.setIndexBuffer(indexBuf, indexType)
                pass.drawIndexed(0, drawState.indexCount(), 1, 0)
            }

        built.close()
        allocator2D = null
    }

    // ── 3D ───────────────────────────────────────────────────────────────────

    fun start3D() {
        allocator3D = ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE)
        buffer3D = BufferBuilder(allocator3D!!, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    }

    fun quad3D(
        matrix: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val buf = buffer3D ?: error("call start3D() first")
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
        buf.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
        buf.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a)
        buf.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a)
    }

    fun stop3D(pipeline: RenderPipeline, modelViewMatrix: Matrix4f = RenderSystem.getModelViewMatrix()) {
        val buf = buffer3D ?: return
        buffer3D = null
        val built = buf.build() ?: return
        val drawState = built.drawState()
        if (drawState.vertexCount() == 0) { built.close(); allocator3D = null; return }

        val byteBuffer = built.vertexBuffer()
        val copy = ByteBuffer.allocateDirect(byteBuffer.remaining()).also {
            it.put(byteBuffer)
            it.flip()
            byteBuffer.rewind()
        }
        val vertexBuf: GpuBuffer = RenderSystem.getDevice().createBuffer(
            { "kitty trail vertex buffer" },
            40,
            copy
        )

        val shapeIndexBuffer = RenderSystem.getSequentialBuffer(drawState.mode())
        val indexBuf: GpuBuffer = shapeIndexBuffer.getBuffer(drawState.indexCount())
        val indexType = shapeIndexBuffer.type()

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            modelViewMatrix,
            Vector4f(1f, 1f, 1f, 1f),
            Vector3f(),
            Matrix4f()
        )

        val client = Minecraft.getInstance()
        val colorView = client.mainRenderTarget.colorTextureView!!
        val depthView = client.mainRenderTarget.depthTextureView!!

        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(
                { "kitty trail render" },
                colorView,
                OptionalInt.empty(),
                depthView,
                OptionalDouble.empty()
            ).use { pass ->
                pass.setPipeline(pipeline)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setVertexBuffer(0, vertexBuf)
                pass.setIndexBuffer(indexBuf, indexType)
                pass.drawIndexed(0, drawState.indexCount(), 1, 0)
            }.also {
                vertexBuf.close()
            }

        built.close()
        allocator3D = null
    }
}