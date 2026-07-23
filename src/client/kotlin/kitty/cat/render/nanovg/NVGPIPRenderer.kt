package kitty.cat.render.nanovg

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import kitty.cat.mixin.client.gui.GuiGraphicsAccessor
import kitty.cat.compat.GuiGraphicsAccessorHelper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f

class NVGPIPRenderer(vertexConsumers: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<NVGPIPRenderer.NVGRenderState>(vertexConsumers) {

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack) {
        val colorTex = RenderSystem.outputColorTextureOverride ?: return
        val glColorTex = colorTex.texture() as? GlTexture ?: return
        val glDepthTex = RenderSystem.outputDepthTextureOverride?.texture() ?: return
        val bufferManager = RenderSystem.getDevice().directStateAccessReflective() ?: return

        val width = colorTex.getWidth(0)
        val height = colorTex.getHeight(0)
        val framebuffer = glColorTex.getFboReflective(bufferManager, glDepthTex) ?: return
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer)
        GlStateManager._viewport(0, 0, width, height)

        // IMPORTANT for 1.21.11+: Unbind Minecraft's sampler objects from texture unit 0.
        // Minecraft 1.21.11 introduced GlSampler objects (via glBindSampler) that override
        // any glTexParameter calls. NanoVG uses glTexParameter internally for its font atlas,
        // so without this line the font atlas gets wrong filtering and text is invisible.
        org.lwjgl.opengl.GL33.glBindSampler(0, 0)

        NVGRenderer.beginFrame(width.toFloat(), height.toFloat())
        state.renderContent()
        NVGRenderer.endFrame()

        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
    }

    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<NVGRenderState> = NVGRenderState::class.java
    override fun getTextureLabel(): String = "nvg_renderer"

    data class NVGRenderState(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int,
        private val poseMatrix: Matrix3x2f,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        val renderContent: () -> Unit
    ) : PictureInPictureRenderState {
        override fun scale(): Float = 1f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + width
        override fun y1(): Int = y + height
        override fun pose(): Matrix3x2f = poseMatrix
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
    }

    companion object {
        fun draw(
            context: GuiGraphicsExtractor,
            x: Int, y: Int,
            width: Int, height: Int,
            renderContent: () -> Unit
        ) {
            val accessor = context as GuiGraphicsAccessor
            val scissor = GuiGraphicsAccessorHelper.getScissorStack(accessor).peekScissor()
            val pose = Matrix3x2f(context.pose())
            val bounds = createBounds(x, y, x + width, y + height, pose, scissor)

            val state = NVGRenderState(x, y, width, height, pose, scissor, bounds, renderContent)
            accessor.guiRenderState.addPicturesInPictureState(state)
        }

        private fun createBounds(
            x0: Int, y0: Int, x1: Int, y1: Int,
            pose: Matrix3x2f, scissorArea: ScreenRectangle?
        ): ScreenRectangle? {
            val screenRect = ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose)
            return if (scissorArea != null) scissorArea.intersection(screenRect) else screenRect
        }
    }
}


private fun Any.directStateAccessReflective(): Any? =
    findZeroArgMethod("directStateAccess", "method_68401", "b")?.invoke(this)

private fun GlTexture.getFboReflective(dsa: Any, depth: Any): Int? =
    findMethod("getFbo", "method_68426", "a") { method -> method.parameterCount == 2 }
        ?.invoke(this, dsa, depth) as? Int

private fun Any.findZeroArgMethod(vararg names: String): java.lang.reflect.Method? =
    findMethod(*names) { method -> method.parameterCount == 0 }

private fun Any.findMethod(vararg names: String, predicate: (java.lang.reflect.Method) -> Boolean): java.lang.reflect.Method? =
    javaClass.methods.firstOrNull { method -> method.name in names && predicate(method) }
        ?: javaClass.declaredMethods.firstOrNull { method -> method.name in names && predicate(method) }
            ?.apply { isAccessible = true }

private fun Any.peekScissor(): ScreenRectangle? =
    runCatching {
        javaClass.getDeclaredMethod("peek").apply { isAccessible = true }.invoke(this) as? ScreenRectangle
    }.getOrNull()
