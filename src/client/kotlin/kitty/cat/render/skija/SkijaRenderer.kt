package kitty.cat.render.skija

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.vulkan.VulkanConst
import com.mojang.blaze3d.vulkan.VulkanDevice
import com.mojang.blaze3d.vulkan.VulkanGpuTexture
import io.github.humbleui.skija.BackendRenderTarget
import io.github.humbleui.skija.ColorSpace
import io.github.humbleui.skija.ColorType
import io.github.humbleui.skija.DirectContext
import io.github.humbleui.skija.Paint
import io.github.humbleui.skija.PaintMode
import io.github.humbleui.skija.Surface
import io.github.humbleui.skija.SurfaceOrigin
import io.github.humbleui.types.RRect
import org.lwjgl.vulkan.VK
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK12

/** Small, self-contained Skija Vulkan bridge based on Minecraft's public GPU objects. */
object SkijaRenderer {
    private val colorSpace = ColorSpace.getSRGB()
    private var context: DirectContext? = null
    private var surface: Surface? = null
    private var target: BackendRenderTarget? = null
    private var texture: VulkanGpuTexture? = null
    private var drawing = false

    fun supports(texture: GpuTexture): Boolean = texture is VulkanGpuTexture

    fun beginFrame(texture: GpuTexture): Boolean {
        if (texture !is VulkanGpuTexture) return false
        RenderSystem.getDevice().createCommandEncoder().submit()
        val directContext = ensureContext()
        ensureSurface(directContext, texture)
        drawing = true
        return true
    }

    fun endFrame() {
        if (!drawing) return
        context?.flushAndSubmit(surface, false)
        drawing = false
    }

    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        val canvas = surface?.canvas ?: return
        Paint().use { paint ->
            paint.setAntiAlias(true).setMode(PaintMode.FILL).setColor(color)
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius.coerceAtLeast(0f)), paint)
        }
    }

    fun roundedRectStroke(x: Float, y: Float, width: Float, height: Float, radius: Float, strokeWidth: Float, color: Int) {
        val canvas = surface?.canvas ?: return
        Paint().use { paint ->
            paint.setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth).setColor(color)
            val inset = strokeWidth / 2f
            canvas.drawRRect(
                RRect.makeXYWH(x + inset, y + inset, width - strokeWidth, height - strokeWidth, radius.coerceAtLeast(0f)),
                paint
            )
        }
    }

    private fun ensureContext(): DirectContext {
        context?.let { return it }
        val device = RenderSystem.getDevice().backend as? VulkanDevice
            ?: error("Skija Vulkan rendering requires Minecraft's Vulkan backend")
        val vkDevice = device.vkDevice()
        val functions = VK.getFunctionProvider()
        return DirectContext.makeVulkan(
            device.instance().vkInstance().address(),
            vkDevice.physicalDevice.address(),
            vkDevice.address(),
            device.graphicsQueue().vkQueue().address(),
            device.graphicsQueue().queueFamilyIndex(),
            functions.getFunctionAddress("vkGetInstanceProcAddr"),
            functions.getFunctionAddress("vkGetDeviceProcAddr"),
            VK12.VK_API_VERSION_1_2
        ).also { context = it }
    }

    private fun ensureSurface(context: DirectContext, next: VulkanGpuTexture) {
        if (texture?.vkImage() == next.vkImage() && surface?.width == next.getWidth(0) && surface?.height == next.getHeight(0)) return
        surface?.close()
        target?.close()
        target = BackendRenderTarget.makeVulkan(
            next.getWidth(0), next.getHeight(0), next.vkImage(), VK10.VK_IMAGE_TILING_OPTIMAL,
            VK10.VK_IMAGE_LAYOUT_GENERAL, VulkanConst.toVk(GpuFormat.RGBA8_UNORM),
            VulkanConst.textureUsageToVk(next.usage(), next.format), 1, next.mipLevels
        )
        surface = Surface.wrapBackendRenderTarget(context, target!!, SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, colorSpace)
        texture = next
    }
}
