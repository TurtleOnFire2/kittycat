package kitty.cat.gui

import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import imgui.*
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import kitty.cat.KittycatClient.mc
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30C
import java.io.IOException
import java.io.UncheckedIOException

object ImGuiHandler {
    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()

    abstract class RenderInterface(name: String) : Screen(Component.literal(name)) {
        abstract fun render(io: ImGuiIO)

        fun open() {
            ImGui.getIO().clearInputKeys()
            ImGui.getIO().clearInputMouse()
            ImGui.getIO().clearEventsQueue()
            applyColors()
            ImGui.getIO().fontGlobalScale = mc.window.guiScale.toFloat() / 2f
            mc.setScreen(this)
        }
    }

    fun initialize(handle: Long) {
        ImGui.createContext()
        ImPlot.createContext()

        val io = ImGui.getIO()
        io.iniFilename = null

        io.configFlags = ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.ViewportsEnable

        imGuiGlfw.init(handle, true)
        imGuiGl3.init()
    }

    fun applyColors() {
        ImGui.styleColorsDark()
        val imGuiStyle = ImGui.getStyle()

        imGuiStyle.windowRounding = 0.0f
        imGuiStyle.childRounding = 0.0f
        imGuiStyle.frameRounding = 0.0f
        imGuiStyle.popupRounding = 0.0f
        imGuiStyle.scrollbarRounding = 0.0f
        imGuiStyle.grabRounding = 0f
        imGuiStyle.setWindowPadding(14.0f, 12.0f)
        imGuiStyle.setFramePadding(12.0f, 8.0f)
        imGuiStyle.setItemSpacing(10.0f, 10.0f)
        imGuiStyle.setItemInnerSpacing(8.0f, 6.0f)
        imGuiStyle.indentSpacing = 18.0f
        imGuiStyle.scrollbarSize = 1.0f
        imGuiStyle.windowBorderSize = 0.0f
        imGuiStyle.frameBorderSize = 0.0f
        imGuiStyle.popupBorderSize = 0.0f

        imGuiStyle.setColor(0, 0.95f, 0.95f, 0.98f, 1.0f)
        imGuiStyle.setColor(1, 0.60f, 0.60f, 0.60f, 1.0f)
        imGuiStyle.setColor(2, 0.10f, 0.10f, 0.12f, 1.0f)
        imGuiStyle.setColor(3, 0.14f, 0.14f, 0.16f, 0.95f)
        imGuiStyle.setColor(4, 0.12f, 0.12f, 0.14f, 1.0f)
        imGuiStyle.setColor(5, 0.35f, 0.35f, 0.38f, 0.50f)
        imGuiStyle.setColor(6, 0.00f, 0.00f, 0.00f, 0.00f)
        imGuiStyle.setColor(7, 0.18f, 0.18f, 0.20f, 1.0f)
        imGuiStyle.setColor(8, 0.25f, 0.25f, 0.28f, 1.0f)
        imGuiStyle.setColor(9, 0.30f, 0.30f, 0.33f, 1.0f)
        imGuiStyle.setColor(10, 0.16f, 0.16f, 0.18f, 1.0f)
        imGuiStyle.setColor(11, 0.22f, 0.22f, 0.25f, 1.0f)
        imGuiStyle.setColor(12, 0.14f, 0.14f, 0.16f, 1.0f)
        imGuiStyle.setColor(13, 0.14f, 0.14f, 0.16f, 1.0f)
        imGuiStyle.setColor(14, 0.16f, 0.16f, 0.18f, 1.0f)
        imGuiStyle.setColor(15, 0.35f, 0.35f, 0.38f, 1.0f)
        imGuiStyle.setColor(16, 0.45f, 0.45f, 0.48f, 1.0f)
        imGuiStyle.setColor(17, 0.90f, 0.60f, 0.35f, 1.0f)
        imGuiStyle.setColor(18, 0.95f, 0.65f, 0.35f, 1.0f)
        imGuiStyle.setColor(19, 0.90f, 0.60f, 0.35f, 1.0f)
        imGuiStyle.setColor(20, 0.95f, 0.70f, 0.40f, 1.0f)
        imGuiStyle.setColor(21, 0.28f, 0.28f, 0.30f, 1.0f)
        imGuiStyle.setColor(22, 0.90f, 0.60f, 0.35f, 0.80f)
        imGuiStyle.setColor(23, 0.95f, 0.65f, 0.40f, 1.0f)
        imGuiStyle.setColor(24, 0.28f, 0.28f, 0.30f, 0.76f)
        imGuiStyle.setColor(25, 0.90f, 0.60f, 0.35f, 0.80f)
        imGuiStyle.setColor(26, 0.95f, 0.65f, 0.40f, 1.0f)
        imGuiStyle.setColor(27, 0.35f, 0.35f, 0.38f, 0.50f)
        imGuiStyle.setColor(28, 0.90f, 0.60f, 0.35f, 0.78f)
        imGuiStyle.setColor(29, 0.95f, 0.65f, 0.40f, 1.0f)
        imGuiStyle.setColor(30, 0.35f, 0.35f, 0.38f, 0.25f)
        imGuiStyle.setColor(31, 0.90f, 0.60f, 0.35f, 0.67f)
        imGuiStyle.setColor(32, 0.95f, 0.65f, 0.40f, 0.95f)
        imGuiStyle.setColor(33, 0.22f, 0.22f, 0.24f, 0.86f)
        imGuiStyle.setColor(34, 0.90f, 0.60f, 0.35f, 0.80f)
        imGuiStyle.setColor(35, 0.32f, 0.32f, 0.35f, 1.0f)
        imGuiStyle.setColor(36, 0.18f, 0.18f, 0.20f, 1.0f)
        imGuiStyle.setColor(37, 0.26f, 0.26f, 0.28f, 1.0f)
        imGuiStyle.setColor(38, 0.90f, 0.60f, 0.35f, 0.70f)
        imGuiStyle.setColor(39, 0.18f, 0.18f, 0.20f, 0.00f)
        imGuiStyle.setColor(40, 0.90f, 0.60f, 0.35f, 1.0f)
        imGuiStyle.setColor(41, 0.95f, 0.70f, 0.45f, 1.0f)
        imGuiStyle.setColor(42, 0.90f, 0.60f, 0.35f, 0.40f)
        imGuiStyle.setColor(43, 0.95f, 0.65f, 0.40f, 1.0f)
        imGuiStyle.setColor(44, 0.00f, 0.00f, 0.00f, 0.52f)
        imGuiStyle.setColor(45, 0.28f, 0.28f, 0.30f, 1.0f)
    }

    fun start() {
        val framebuffer = Minecraft.getInstance().mainRenderTarget
        GlStateManager._glBindFramebuffer(
            GL30C.GL_FRAMEBUFFER,
            (framebuffer.getColorTexture() as GlTexture)
                .getFbo((RenderSystem.getDevice() as GlDevice).directStateAccess(), null)
        )
        GL11C.glViewport(0, 0, framebuffer.width, framebuffer.height)

        imGuiGl3.newFrame()
        imGuiGlfw.newFrame()
        ImGui.newFrame()
    }

    fun end() {
        ImGui.render()

        imGuiGl3.renderDrawData(ImGui.getDrawData())

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val ptr = glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()

            glfwMakeContextCurrent(ptr)
        }
    }

    var glyphRanges: ShortArray? = null
    fun loadFont(path: String, pixelSize: Float): ImFont {
        if (glyphRanges == null) {
            val rangesBuilder = ImFontGlyphRangesBuilder()

            rangesBuilder.addRanges(ImGui.getIO().getFonts().glyphRangesDefault)
            rangesBuilder.addRanges(ImGui.getIO().getFonts().glyphRangesCyrillic)
            rangesBuilder.addRanges(ImGui.getIO().getFonts().glyphRangesJapanese)

            glyphRanges = rangesBuilder.buildRanges()
        }

        val config = ImFontConfig()
        config.setGlyphRanges(glyphRanges)
        try {
            this::class.java.getClassLoader().getResourceAsStream(path).let {
                val fontData = it.readAllBytes()
                return ImGui.getIO().fonts.addFontFromMemoryTTF(fontData, pixelSize, config)
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Failed to load font from path: $path", e)
        } finally {
            config.destroy()
        }
    }

    fun dispose() {
        imGuiGl3.shutdown()
        imGuiGlfw.shutdown()

        ImPlot.destroyContext()
        ImGui.destroyContext()
    }
}
