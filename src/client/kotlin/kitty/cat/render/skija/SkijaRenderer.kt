package kitty.cat.render.skija

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.pipeline.*
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.GpuDevice
import com.mojang.blaze3d.textures.*
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vulkan.*
import kitty.cat.KittycatClient.mc
import io.github.humbleui.skija.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK12
import java.util.Optional

/**
 * Skija draws the complete UI; Minecraft only composites the premultiplied overlay.
 * Backend wrapping and GL state preservation adapted from the local Orbis renderer.
 */
object SkijaRenderer {
    private const val OVERLAY_LABEL = "Kittycat Skija GUI"
    private val colorSpace = ColorSpace.getSRGB()
    private var backend: SkijaBackend? = null
    private var overlayTarget: TextureTarget? = null
    private var overlaySourceTexture: GpuTexture? = null
    private var overlayValid = false
    private var compositeDevice: GpuDevice? = null
    private var compositePipelineReady = false
    private var compositeSampler: GpuSampler? = null
    private data class Frame(val owner: Screen, val width: Int, val height: Int, val draw: SkijaDraw)
    private var pending: Frame? = null

    fun submit(owner: Screen, width: Int, height: Int, draw: SkijaDraw) {
        pending = Frame(owner, width, height, draw)
    }

    @JvmStatic
    fun renderFrame() {
        val frame = pending ?: return
        pending = null
        if (mc.gui.screen() !== frame.owner || frame.width <= 0 || frame.height <= 0) return
        RenderSystem.assertOnRenderThread()
        val target = ensureOverlayTarget(mc.gameRenderer.mainRenderTarget())
        val texture = checkNotNull(target.getColorTexture())
        val renderer = ensureBackend(texture)
        val canvas = renderer.prepare(target, texture).canvas
        val save = canvas.save()
        overlayValid = false
        try {
            canvas.clear(0)
            canvas.scale(target.width.toFloat() / frame.width, target.height.toFloat() / frame.height)
            frame.draw.replay(canvas)
        } finally {
            try {
                canvas.restoreToCount(save)
            } finally {
                renderer.finishOverlay()
            }
        }
        overlayValid = true
        compositeFrame()
    }

    @JvmStatic
    fun cleanup() {
        pending = null
        destroyOverlayTarget()
        SkijaDraw.cleanup()
        colorSpace.close()
    }

    private val SCREENQUAD_SHADER = Identifier.withDefaultNamespace("core/screenquad")
    private val BLIT_SCREEN_SHADER = Identifier.withDefaultNamespace("core/blit_screen")
    private val COMPOSITE_VERTEX_SOURCE = """
#version 330

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}
""".trimIndent()
    private val COMPOSITE_FRAGMENT_SOURCE = """
#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, texCoord);
}
""".trimIndent()
    private val COMPOSITE_SHADER_SOURCE = ShaderSource { id, type ->
        when {
            type == ShaderType.VERTEX && id == SCREENQUAD_SHADER -> COMPOSITE_VERTEX_SOURCE
            type == ShaderType.FRAGMENT && id == BLIT_SCREEN_SHADER -> COMPOSITE_FRAGMENT_SOURCE
            else -> null
        }
    }

    @Suppress("deprecation")
    private val COMPOSITE_PIPELINE: RenderPipeline =
        RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/skija_gui_composite"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build()


    @JvmStatic
    fun compositeFrame(): Boolean {
        RenderSystem.assertOnRenderThread()
        if (!overlayValid) return false

        val framebuffer: RenderTarget = mc.gameRenderer.mainRenderTarget()
        val mainTexture = framebuffer.getColorTexture() ?: return false
        val overlay = overlayTarget ?: return false
        if (
            overlay.width != framebuffer.width ||
            overlay.height != framebuffer.height ||
            overlaySourceTexture !== mainTexture
        ) {
            overlayValid = false
            return false
        }

        val overlayView = overlay.getColorTextureView() ?: return false
        val outputView = framebuffer.getColorTextureView() ?: return false
        val device = RenderSystem.getDevice()
        if (!ensureCompositeResources(device)) return false
        val sampler = compositeSampler ?: return false
        val encoder = device.createCommandEncoder()
        encoder.createRenderPass({ "Kittycat Skija GUI Composite" }, outputView, Optional.empty()).use { renderPass ->
            renderPass.setPipeline(COMPOSITE_PIPELINE)
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.bindTexture("InSampler", overlayView, sampler)
            renderPass.draw(3, 1, 0, 0)
        }
        return true
    }


    private fun ensureOverlayTarget(mainTarget: RenderTarget): TextureTarget {
        val mainTexture = mainTarget.getColorTexture()
            ?: error("Kittycat Skija renderer cannot allocate an overlay without a main color texture")
        val expectedBackend = backendKind(mainTexture)
        val existing = overlayTarget
        val existingTexture = existing?.getColorTexture()

        if (
            existing != null &&
            existingTexture != null &&
            existing.width == mainTarget.width &&
            existing.height == mainTarget.height &&
            overlaySourceTexture === mainTexture &&
            backendKind(existingTexture) == expectedBackend
        ) {
            return existing
        }

        destroyOverlayTarget()
        return TextureTarget(OVERLAY_LABEL, mainTarget.width, mainTarget.height, false, GpuFormat.RGBA8_UNORM)
            .also {
                overlayTarget = it
                overlaySourceTexture = mainTexture
                overlayValid = false
            }
    }

    private fun destroyOverlayTarget() {
        overlayValid = false
        overlaySourceTexture = null
        backend?.close()
        backend = null
        overlayTarget?.destroyBuffers()
        overlayTarget = null
    }

    private fun backendKind(texture: GpuTexture): Int = when (texture) {
        is VulkanGpuTexture -> 1
        is GlTexture -> 2
        else -> 0
    }

    private fun ensureCompositeResources(device: GpuDevice): Boolean {
        if (compositeDevice !== device) {
            compositeDevice = device
            compositePipelineReady = false
            compositeSampler = null
        }
        if (!compositePipelineReady) {
            if (!device.precompilePipeline(COMPOSITE_PIPELINE, COMPOSITE_SHADER_SOURCE).isValid()) {
                return false
            }
            compositePipelineReady = true
        }
        if (compositeSampler == null) {
            compositeSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        }
        return true
    }

    private fun ensureBackend(texture: GpuTexture): SkijaBackend {
        val current = backend
        if (current != null && current.supports(texture)) {
            return current
        }

        current?.close()
        overlayValid = false
        val next = when (texture) {
            is VulkanGpuTexture -> VulkanSkijaBackend()
            is GlTexture -> OpenGlSkijaBackend()
            else -> error("Kittycat Skija renderer does not support Minecraft texture backend ${texture.javaClass.name}")
        }
        backend = next
        return next
    }


    private interface SkijaBackend : AutoCloseable {
        fun supports(texture: GpuTexture): Boolean
        fun prepare(framebuffer: RenderTarget, texture: GpuTexture): Surface
        fun finishOverlay()
    }

    private class VulkanSkijaBackend : SkijaBackend {
        private var directContext: DirectContext? = null
        private var surface: Surface? = null
        private var renderTarget: BackendRenderTarget? = null
        private var surfaceTexture: VulkanGpuTexture? = null
        private var frameSurface: Surface? = null

        override fun supports(texture: GpuTexture): Boolean {
            return texture is VulkanGpuTexture
        }

        override fun prepare(framebuffer: RenderTarget, texture: GpuTexture): Surface {
            check(texture is VulkanGpuTexture) {
                "Kittycat Skija Vulkan backend received ${texture.javaClass.name}"
            }

            val context = ensureContext()
            ensureSurface(context, texture)
            val prepared = surface ?: error("Skija Vulkan surface was not created")
            frameSurface = prepared
            return prepared
        }

        override fun finishOverlay() {
            try {
                directContext?.flushAndSubmit(frameSurface, false)
            } finally {
                frameSurface = null
            }
        }

        override fun close() {
            releaseSurface()
            directContext?.close()
            directContext = null
        }

        private fun ensureContext(): DirectContext {
            directContext?.let { return it }

            val backend = RenderSystem.getDevice().backend
            check(backend is VulkanDevice) {
                "Kittycat Skija Vulkan backend requires Minecraft's Vulkan renderer"
            }

            val instance = backend.instance().vkInstance()
            val device = backend.vkDevice()
            val physicalDevice = device.physicalDevice
            val queue = backend.graphicsQueue().vkQueue()
            val functionProvider = VK.getFunctionProvider()

            val context = DirectContext.makeVulkan(
                instance.address(),
                physicalDevice.address(),
                device.address(),
                queue.address(),
                backend.graphicsQueue().queueFamilyIndex(),
                functionProvider.getFunctionAddress("vkGetInstanceProcAddr"),
                functionProvider.getFunctionAddress("vkGetDeviceProcAddr"),
                VK12.VK_API_VERSION_1_2
            )
            directContext = context
            return context
        }

        private fun ensureSurface(context: DirectContext, texture: VulkanGpuTexture) {
            val width = texture.getWidth(0)
            val height = texture.getHeight(0)
            val current = surfaceTexture
            if (
                surface != null &&
                renderTarget != null &&
                current != null &&
                current.vkImage() == texture.vkImage() &&
                surface?.width == width &&
                surface?.height == height
            ) {
                return
            }

            releaseSurface()

            val target = makeRenderTarget(texture)
            renderTarget = target
            surface = Surface.wrapBackendRenderTarget(
                context,
                target,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                colorSpace
            )
            surfaceTexture = texture
        }

        private fun makeRenderTarget(texture: VulkanGpuTexture): BackendRenderTarget {
            val width = texture.getWidth(0)
            val height = texture.getHeight(0)
            return BackendRenderTarget.makeVulkan(
                width,
                height,
                texture.vkImage(),
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_GENERAL,
                VulkanConst.toVk(GpuFormat.RGBA8_UNORM),
                VulkanConst.textureUsageToVk(texture.usage(), texture.format),
                1,
                texture.mipLevels
            )
        }

        private fun releaseSurface() {
            frameSurface = null
            surface?.close()
            surface = null
            renderTarget?.close()
            renderTarget = null
            surfaceTexture = null
        }
    }

    private class OpenGlSkijaBackend : SkijaBackend {
        private var directContext: DirectContext? = null
        private var surface: Surface? = null
        private var renderTarget: BackendRenderTarget? = null
        private var surfaceTextureId = 0
        private var framebufferId = 0
        private var frameSurface: Surface? = null
        private var frameState: OpenGlStateSnapshot? = null

        override fun supports(texture: GpuTexture): Boolean {
            return texture is GlTexture
        }

        override fun prepare(framebuffer: RenderTarget, texture: GpuTexture): Surface {
            check(texture is GlTexture) {
                "Kittycat Skija OpenGL backend received ${texture.javaClass.name}"
            }

            beginGlFrame()
            return try {
                val context = ensureContext()
                ensureSurface(context, framebuffer, texture)
                val prepared = surface ?: error("Skija OpenGL surface was not created")
                frameSurface = prepared
                prepared
            } catch (t: Throwable) {
                finishGlFrame()
                throw t
            }
        }

        override fun finishOverlay() {
            try {
                directContext?.flushAndSubmit(frameSurface, false)
            } finally {
                frameSurface = null
                finishGlFrame()
            }
        }

        override fun close() {
            finishGlFrame()
            releaseSurface()
            directContext?.close()
            directContext = null
        }

        private fun ensureContext(): DirectContext {
            directContext?.let { return it }
            val context = DirectContext.makeGL()
            directContext = context
            return context
        }

        private fun beginGlFrame() {
            if (frameState != null) return

            val state = OpenGlStateSnapshot.capture()
            try {
                ensureContext().resetGLAll()
                frameState = state
            } catch (t: Throwable) {
                state.restore()
                throw t
            }
        }

        private fun finishGlFrame() {
            val state = frameState ?: return
            try {
                directContext?.resetGLAll()
            } finally {
                state.restore()
                frameState = null
            }
        }

        private fun ensureSurface(context: DirectContext, framebuffer: RenderTarget, texture: GlTexture) {
            val width = texture.getWidth(0)
            val height = texture.getHeight(0)
            if (
                surface != null &&
                renderTarget != null &&
                surfaceTextureId == texture.glId() &&
                surface?.width == width &&
                surface?.height == height
            ) {
                return
            }

            releaseSurface()
            framebufferId = createFramebuffer(texture)
            surfaceTextureId = texture.glId()

            val target = BackendRenderTarget.makeGL(
                width,
                height,
                0,
                0,
                framebufferId,
                FramebufferFormat.GR_GL_RGBA8
            )
            renderTarget = target
            surface = Surface.wrapBackendRenderTarget(
                context,
                target,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                colorSpace
            )
        }

        private fun createFramebuffer(texture: GlTexture): Int {
            val previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)
            val previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING)
            val fbo = GL30C.glGenFramebuffers()
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo)
            GL30C.glFramebufferTexture2D(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_COLOR_ATTACHMENT0,
                GL11C.GL_TEXTURE_2D,
                texture.glId(),
                texture.fboMipLevel()
            )
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0)
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0)
            val status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                GL30C.glDeleteFramebuffers(fbo)
                error("Kittycat Skija OpenGL backend could not wrap Minecraft framebuffer: GL status 0x${status.toString(16)}")
            }
            return fbo
        }

        private fun releaseSurface() {
            frameSurface = null
            surface?.close()
            surface = null
            renderTarget?.close()
            renderTarget = null
            if (framebufferId != 0) {
                GL30C.glDeleteFramebuffers(framebufferId)
                framebufferId = 0
            }
            surfaceTextureId = 0
        }
    }

    private class OpenGlStateSnapshot(
        private val drawFramebuffer: Int,
        private val readFramebuffer: Int,
        private val viewport: IntArray,
        private val scissorBox: IntArray,
        private val currentProgram: Int,
        private val activeTexture: Int,
        private val texture2d: Int,
        private val vertexArray: Int,
        private val arrayBuffer: Int,
        private val elementArrayBuffer: Int,
        private val pixelPackBuffer: Int,
        private val pixelUnpackBuffer: Int,
        private val uniformBuffer: Int,
        private val blend: Boolean,
        private val depthTest: Boolean,
        private val cullFace: Boolean,
        private val scissorTest: Boolean,
        private val stencilTest: Boolean,
        private val multisample: Boolean,
        private val polygonOffsetFill: Boolean,
        private val depthMask: Boolean,
        private val colorMask: BooleanArray,
        private val blendSrcRgb: Int,
        private val blendDstRgb: Int,
        private val blendSrcAlpha: Int,
        private val blendDstAlpha: Int,
        private val blendEquationRgb: Int,
        private val blendEquationAlpha: Int
    ) {
        fun restore() {
            setEnabled(GL11C.GL_BLEND, blend)
            setEnabled(GL11C.GL_DEPTH_TEST, depthTest)
            setEnabled(GL11C.GL_CULL_FACE, cullFace)
            setEnabled(GL11C.GL_SCISSOR_TEST, scissorTest)
            setEnabled(GL11C.GL_STENCIL_TEST, stencilTest)
            setEnabled(GL13C.GL_MULTISAMPLE, multisample)
            setEnabled(GL11C.GL_POLYGON_OFFSET_FILL, polygonOffsetFill)

            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer)
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
            GL11C.glDepthMask(depthMask)
            GL11C.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])
            GL14C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            GL20C.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)
            GL20C.glUseProgram(currentProgram)
            GL13C.glActiveTexture(activeTexture)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture2d)
            GL30C.glBindVertexArray(vertexArray)
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, arrayBuffer)
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer)
            GL15C.glBindBuffer(GL21_PIXEL_PACK_BUFFER, pixelPackBuffer)
            GL15C.glBindBuffer(GL21_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer)
            GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, uniformBuffer)
        }

        companion object {
            private const val GL21_PIXEL_PACK_BUFFER = 35051
            private const val GL21_PIXEL_UNPACK_BUFFER = 35052
            private const val GL_PIXEL_PACK_BUFFER_BINDING = 35053
            private const val GL_PIXEL_UNPACK_BUFFER_BINDING = 35055

            fun capture(): OpenGlStateSnapshot {
                MemoryStack.stackPush().use { stack ->
                    val viewport = IntArray(4)
                    val viewportBuffer = stack.mallocInt(4)
                    GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewportBuffer)
                    viewportBuffer.get(viewport)

                    val scissor = IntArray(4)
                    val scissorBuffer = stack.mallocInt(4)
                    GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBuffer)
                    scissorBuffer.get(scissor)

                    val colorMask = BooleanArray(4)
                    val colorMaskBuffer = stack.malloc(4)
                    GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMaskBuffer)
                    for (i in colorMask.indices) {
                        colorMask[i] = colorMaskBuffer.get(i).toInt() != 0
                    }

                    return OpenGlStateSnapshot(
                        GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                        GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),
                        viewport,
                        scissor,
                        GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                        GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),
                        GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D),
                        GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                        GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING),
                        GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING),
                        GL11C.glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),
                        GL11C.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),
                        GL11C.glGetInteger(GL31C.GL_UNIFORM_BUFFER_BINDING),
                        GL11C.glIsEnabled(GL11C.GL_BLEND),
                        GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                        GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                        GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST),
                        GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST),
                        GL11C.glIsEnabled(GL13C.GL_MULTISAMPLE),
                        GL11C.glIsEnabled(GL11C.GL_POLYGON_OFFSET_FILL),
                        GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK),
                        colorMask,
                        GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
                        GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
                        GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA),
                        GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA),
                        GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB),
                        GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA)
                    )
                }
            }

            private fun setEnabled(cap: Int, enabled: Boolean) {
                if (enabled) {
                    GL11C.glEnable(cap)
                } else {
                    GL11C.glDisable(cap)
                }
            }
        }
    }


}
