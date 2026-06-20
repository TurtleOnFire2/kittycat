package kitty.cat.render.nanovg

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import kitty.cat.mixin.client.gui.GuiGraphicsAccessor
import kitty.cat.mixin.client.gui.GuiScissorStackAccessor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.lwjgl.opengl.GL30C

class NVGPIPRenderer : PictureInPictureRenderer<NVGPIPRenderer.NVGRenderState>() {

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector) {
        val colorTex = RenderSystem.outputColorTextureOverride ?: return
        val glColorTex = colorTex.texture() as? GlTexture ?: return
        val glDepthTex = RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture ?: return

        val (width, height) = colorTex.let { it.getWidth(0) to it.getHeight(0) }
        val fbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, fbo)
        GlStateManager._glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_TEXTURE_2D, glColorTex.glId(), 0)
        GlStateManager._glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, glDepthTex.glId(), 0)
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
        GlStateManager._enableBlend(0)
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
        GlStateManager._glDeleteFramebuffers(fbo)
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
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun pose(): Matrix3x2fc = poseMatrix
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
            val scissor = (accessor.scissorStack as GuiScissorStackAccessor).`kittycat$peek`()
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
