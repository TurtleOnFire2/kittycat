package kitty.cat.render.skija

import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import kitty.cat.KittycatClient.mc
import io.github.humbleui.skija.*
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.opengl.*
import org.lwjgl.system.MemoryStack

/**
 * OpenGL-only GUI renderer for Minecraft 26.1.2.
 * Skia draws into the game's color target after the vanilla GUI pass, preserving
 * its contents and restoring GL state afterwards. No Vulkan or compositor pipeline.
 */
object SkijaRenderer {
    private val colorSpace = ColorSpace.getSRGB()
    private var backend: OpenGlSkijaBackend? = null
    private data class Frame(val owner: Screen, val width: Int, val height: Int, val draw: SkijaDraw)
    private var pending: Frame? = null

    fun submit(owner: Screen, width: Int, height: Int, draw: SkijaDraw) {
        pending = Frame(owner, width, height, draw)
    }

    @JvmStatic
    fun renderFrame() {
        val frame = pending ?: return
        pending = null
        if (mc.screen !== frame.owner || frame.width <= 0 || frame.height <= 0) return
        RenderSystem.assertOnRenderThread()
        val target = mc.mainRenderTarget
        val texture = target.getColorTexture() ?: return
        val renderer = backend ?: OpenGlSkijaBackend().also { backend = it }
        val canvas = renderer.prepare(target, texture).canvas
        val save = canvas.save()
        try {
            canvas.scale(target.width.toFloat() / frame.width, target.height.toFloat() / frame.height)
            frame.draw.replay(canvas)
        } finally {
            try {
                canvas.restoreToCount(save)
            } finally {
                renderer.finishOverlay()
            }
        }
    }

    @JvmStatic
    fun cleanup() {
        pending = null
        backend?.close()
        backend = null
        SkijaDraw.cleanup()
        colorSpace.close()
    }

    private interface SkijaBackend : AutoCloseable {
        fun supports(texture: GpuTexture): Boolean
        fun prepare(framebuffer: RenderTarget, texture: GpuTexture): Surface
        fun finishOverlay()
    }

    private class OpenGlSkijaBackend : SkijaBackend {
        private var directContext: DirectContext? = null
        private var surface: Surface? = null
        private var renderTarget: BackendRenderTarget? = null
        private var surfaceTextureId = 0
        private var framebufferId = 0
        private var stencilId = 0
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
                state.prepareForSkia()
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
                8,
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
                0 // The main render target in 26.1.2 uses mip level zero.
            )
            // Skia's clipped/path glyph rendering needs a real stencil attachment.
            val previousRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING)
            stencilId = GL30C.glGenRenderbuffers()
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, stencilId)
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL30C.GL_DEPTH24_STENCIL8, texture.getWidth(0), texture.getHeight(0))
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_RENDERBUFFER, stencilId)
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, previousRenderbuffer)
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0)
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0)
            val status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer)
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer)
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                GL30C.glDeleteRenderbuffers(stencilId)
                stencilId = 0
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
            if (stencilId != 0) {
                GL30C.glDeleteRenderbuffers(stencilId)
                stencilId = 0
            }
            if (framebufferId != 0) {
                GL30C.glDeleteFramebuffers(framebufferId)
                framebufferId = 0
            }
            surfaceTextureId = 0
        }
    }

    class OpenGlStateSnapshot(
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
        private val textureBindings = IntArray(GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS))
        private val samplerBindings = IntArray(textureBindings.size)
        private val unpackParameters = intArrayOf(GL11C.GL_UNPACK_ALIGNMENT, GL11C.GL_UNPACK_ROW_LENGTH, GL11C.GL_UNPACK_SKIP_ROWS, GL11C.GL_UNPACK_SKIP_PIXELS)
        private val unpackValues = unpackParameters.map { GL11C.glGetInteger(it) }
        init {
            for (unit in textureBindings.indices) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit)
                textureBindings[unit] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
                samplerBindings[unit] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING)
            }
            GL13C.glActiveTexture(activeTexture)
        }

        fun prepareForSkia() {
            // Glyph atlas uploads must use client memory, not Minecraft's upload PBO.
            GL15C.glBindBuffer(GL21_PIXEL_UNPACK_BUFFER, 0)
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4)
            unpackParameters.drop(1).forEach { GL11C.glPixelStorei(it, 0) }
            samplerBindings.indices.forEach { GL33C.glBindSampler(it, 0) }
        }

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
            for (unit in textureBindings.indices) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit)
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindings[unit])
                GL33C.glBindSampler(unit, samplerBindings[unit])
            }
            GL13C.glActiveTexture(activeTexture)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture2d)
            unpackParameters.forEachIndexed { index, parameter -> GL11C.glPixelStorei(parameter, unpackValues[index]) }
            GL30C.glBindVertexArray(vertexArray)
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, arrayBuffer)
            if (vertexArray != 0) GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer)
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
