package kitty.cat.render

import io.github.humbleui.skija.*
import kitty.cat.render.nanovg.NVGFont
import kitty.cat.render.skija.SkijaDraw
import kitty.cat.render.skija.SkijaRenderer
import org.junit.Assume.assumeTrue
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33C.*
import kotlin.test.*

/** Opt-in real GPU regression test; creates only a hidden window. */
class SkijaOpenGlTest {
    @Test fun textSurvivesExternalTextureState() {
        assumeTrue(System.getenv("KITTYCAT_GPU_TESTS") == "true")
        assertTrue(glfwInit())
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(200, 80, "Kittycat GPU test", 0, 0)
        assertNotEquals(0L, window)
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            val texture = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 200, 80, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            val fbo = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER))
            val sampler = glGenSamplers()
            glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR)
            glBindSampler(0, sampler)
            glActiveTexture(GL_TEXTURE0)
            val uploadBuffer = glGenBuffers()
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, uploadBuffer)
            glBufferData(GL_PIXEL_UNPACK_BUFFER, 16L, GL_STREAM_DRAW)
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 256)
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 2)
            val state = SkijaRenderer.OpenGlStateSnapshot.capture()
            state.prepareForSkia()
            assertEquals(0, glGetInteger(GL_SAMPLER_BINDING))
            assertEquals(0, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING))
            assertEquals(0, glGetInteger(GL_UNPACK_ROW_LENGTH))
            DirectContext.makeGL().use { context ->
                context.resetGLAll()
                BackendRenderTarget.makeGL(200, 80, 0, 0, fbo, FramebufferFormat.GR_GL_RGBA8).use { target ->
                    ColorSpace.getSRGB().use { space ->
                        Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, space).use { surface ->
                            surface.canvas.clear(0)
                            val font = NVGFont("Test", checkNotNull(javaClass.getResourceAsStream("/assets/kittycat/font/onest_regular.ttf")))
                            val draw = SkijaDraw(font)
                            draw.text("Kittycat 123", 10, 10, 0xFFFFFFFF.toInt(), 20f)
                            draw.replay(surface.canvas)
                            context.flushAndSubmit(surface, true)
                            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo)
                            val pixels = org.lwjgl.system.MemoryUtil.memAlloc(200 * 80 * 4)
                            try {
                                glReadPixels(0, 0, 200, 80, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
                                assertEquals(GL_NO_ERROR, glGetError())
                                assertTrue((0 until 200 * 80).any { pixels.get(it * 4 + 3).toInt() and 255 > 32 }, "GPU glyphs must produce visible pixels")
                            } finally { org.lwjgl.system.MemoryUtil.memFree(pixels) }
                        }
                    }
                }
            }
            state.restore()
            assertEquals(sampler, glGetInteger(GL_SAMPLER_BINDING), "Minecraft's font sampler must be restored")
            assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D), "Minecraft's font texture must be restored")
            assertEquals(uploadBuffer, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING))
            assertEquals(256, glGetInteger(GL_UNPACK_ROW_LENGTH))
            assertEquals(2, glGetInteger(GL_UNPACK_SKIP_ROWS))
            assertEquals(GL_NO_ERROR, glGetError(), "Restoring Minecraft's state must not cause GL errors")
            glDeleteBuffers(uploadBuffer)
            glDeleteSamplers(sampler)
            glDeleteFramebuffers(fbo)
            glDeleteTextures(texture)
        } finally {
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }
}
