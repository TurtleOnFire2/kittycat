package kitty.cat.render

import io.github.humbleui.skija.Bitmap
import io.github.humbleui.skija.Surface
import kitty.cat.render.nanovg.NVGFont
import kitty.cat.render.skija.SkijaDraw
import kotlin.test.*

class SkijaDrawTest {
    private fun font() = NVGFont("Test", checkNotNull(javaClass.getResourceAsStream("/assets/kittycat/font/onest_regular.ttf")))

    @Test fun colorPickerGradientsBlendSmoothlyAndPreserveTransparencyGrid() {
        val draw = SkijaDraw(font())
        draw.linearGradient(0, 0, 100, 10, intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
        draw.checkerboard(0, 12, 100, 8)
        draw.linearGradient(0, 12, 100, 8, intArrayOf(0x00FF0000, 0xFFFF0000.toInt()))
        Surface.makeRasterN32Premul(100, 20).use { surface ->
            surface.canvas.clear(0)
            draw.replay(surface.canvas)
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { pixels ->
                    val middle = pixels.getColor(50, 5)
                    assertTrue((middle ushr 16 and 255) in 120..135)
                    assertTrue((middle and 255) in 120..135)
                    assertNotEquals(pixels.getColor(1, 13), pixels.getColor(5, 13))
                    assertTrue((pixels.getColor(99, 13) ushr 16 and 255) > 250)
                }
            }
        }
    }

    @Test fun numericValuesAreVisuallyCenteredInsideTheirField() {
        val draw = SkijaDraw(font())
        draw.fieldText("123.45", 10, 10, 80, 20, 0xFFFFFFFF.toInt(), centered = true)
        Surface.makeRasterN32Premul(100, 40).use { surface ->
            surface.canvas.clear(0)
            draw.replay(surface.canvas)
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { pixels ->
                    val ink = (0 until 40).flatMap { y -> (0 until 100).filter { x -> pixels.getColor(x, y) ushr 24 > 32 }.map { x -> x to y } }
                    assertTrue(ink.isNotEmpty())
                    val centerX = (ink.minOf { it.first } + ink.maxOf { it.first } + 1) / 2f
                    val centerY = (ink.minOf { it.second } + ink.maxOf { it.second } + 1) / 2f
                    assertTrue(kotlin.math.abs(centerX - 50f) <= 1f)
                    assertTrue(kotlin.math.abs(centerY - 20f) <= 1f)
                }
            }
        }
    }

    @Test fun clippingAndScalingAreAppliedToNativeDrawCommands() {
        val draw = SkijaDraw(font())
        draw.enableScissor(10, 10, 30, 30)
        draw.roundedRect(0, 0, 50, 50, 0, 0xFFFF0000.toInt())
        draw.disableScissor()
        draw.roundedRect(40, 40, 5, 5, 0, 0xFF00FF00.toInt())
        Surface.makeRasterN32Premul(100, 100).use { surface ->
            surface.canvas.clear(0)
            surface.canvas.scale(2f, 2f)
            draw.replay(surface.canvas)
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { pixels ->
                    assertEquals(0, pixels.getColor(5, 5))
                    assertEquals(0xFFFF0000.toInt(), pixels.getColor(40, 40))
                    assertEquals(0, pixels.getColor(65, 65))
                    assertEquals(0xFF00FF00.toInt(), pixels.getColor(85, 85))
                }
            }
        }
    }

    @Test fun bundledFontAndAntialiasedCornersRenderWithoutMinecraft() {
        val draw = SkijaDraw(font())
        draw.roundedRect(10, 10, 40, 40, 12, 0xFFFFFFFF.toInt())
        draw.text("Kittycat", 60, 12, 0xFFFFFFFF.toInt(), 14f)
        Surface.makeRasterN32Premul(160, 60).use { surface ->
            surface.canvas.clear(0)
            draw.replay(surface.canvas)
            surface.makeImageSnapshot().use { image ->
                Bitmap.makeFromImage(image).use { pixels ->
                    assertEquals(0, pixels.getColor(10, 10))
                    assertEquals(0xFFFFFFFF.toInt(), pixels.getColor(30, 30))
                    assertTrue((10..25).any { y -> (10..25).any { x -> (pixels.getColor(x, y) ushr 24) in 1..254 } })
                    assertTrue((12..35).any { y -> (60..150).any { x -> pixels.getColor(x, y) != 0 } })
                }
            }
        }
    }
}
