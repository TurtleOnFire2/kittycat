package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/** Controls the Minecraft OS window's compact, always-on-top mode. */
object PictureInPictureMode {
    private const val MIN_WIDTH = 480
    private const val MAX_WIDTH = 640
    private const val WIDTH_FRACTION = 0.30
    private const val ASPECT_RATIO = 16.0 / 9.0
    private const val SCREEN_MARGIN = 16

    private var savedState: WindowState? = null

    val active: Boolean
        get() = savedState != null

    fun toggle(): Boolean {
        if (active) disable() else enable()
        return active
    }

    private fun enable() {
        val window = mc.window
        val handle = window.handle()
        val positionX = IntArray(1)
        val positionY = IntArray(1)
        val width = IntArray(1)
        val height = IntArray(1)

        GLFW.glfwGetWindowPos(handle, positionX, positionY)
        GLFW.glfwGetWindowSize(handle, width, height)

        savedState = WindowState(
            x = positionX[0],
            y = positionY[0],
            width = width[0],
            height = height[0],
            fullscreen = window.isFullscreen,
            maximized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE,
            floating = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FLOATING) == GLFW.GLFW_TRUE,
        )

        val workArea = findCurrentMonitorWorkArea(handle, savedState!!)
        val pipWidth = (workArea.width * WIDTH_FRACTION).toInt()
            .coerceIn(MIN_WIDTH.coerceAtMost(workArea.width), MAX_WIDTH.coerceAtMost(workArea.width))
        val pipHeight = (pipWidth / ASPECT_RATIO).toInt()
            .coerceAtMost(workArea.height)

        if (window.isFullscreen) {
            window.setWindowed(pipWidth, pipHeight)
        } else if (savedState!!.maximized) {
            GLFW.glfwRestoreWindow(handle)
        }

        val frameLeft = IntArray(1)
        val frameTop = IntArray(1)
        val frameRight = IntArray(1)
        val frameBottom = IntArray(1)
        GLFW.glfwGetWindowFrameSize(handle, frameLeft, frameTop, frameRight, frameBottom)

        val pipX = workArea.x + workArea.width - pipWidth - frameRight[0] - SCREEN_MARGIN
        val pipY = workArea.y + frameTop[0] + SCREEN_MARGIN

        GLFW.glfwSetWindowSize(handle, pipWidth, pipHeight)
        GLFW.glfwSetWindowPos(handle, pipX, pipY)
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE)
        GLFW.glfwShowWindow(handle)
    }

    private fun disable() {
        val state = savedState ?: return
        val window = mc.window
        val handle = window.handle()

        GLFW.glfwSetWindowAttrib(
            handle,
            GLFW.GLFW_FLOATING,
            if (state.floating) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE,
        )

        if (state.fullscreen) {
            if (!window.isFullscreen) {
                window.toggleFullScreen()
                window.updateFullscreenIfChanged()
            }
        } else {
            if (window.isFullscreen) {
                window.toggleFullScreen()
                window.updateFullscreenIfChanged()
            }

            GLFW.glfwRestoreWindow(handle)
            GLFW.glfwSetWindowSize(handle, state.width, state.height)
            GLFW.glfwSetWindowPos(handle, state.x, state.y)

            if (state.maximized) {
                GLFW.glfwMaximizeWindow(handle)
            }
        }

        // F11 can still be pressed while PiP is active. Keep Minecraft's saved
        // fullscreen preference in sync with the state we just restored.
        mc.options.fullscreen().set(state.fullscreen)
        mc.options.save()

        savedState = null
    }

    private fun findCurrentMonitorWorkArea(handle: Long, state: WindowState): WorkArea {
        val attachedMonitor = GLFW.glfwGetWindowMonitor(handle)
        if (attachedMonitor != 0L) return getWorkArea(attachedMonitor)

        val monitors = GLFW.glfwGetMonitors()
        var bestMonitor = GLFW.glfwGetPrimaryMonitor()
        var bestOverlap = -1L

        if (monitors != null) {
            for (index in 0 until monitors.limit()) {
                val monitor = monitors[index]
                val area = getWorkArea(monitor)
                val overlapWidth = max(0, min(state.x + state.width, area.x + area.width) - max(state.x, area.x))
                val overlapHeight = max(0, min(state.y + state.height, area.y + area.height) - max(state.y, area.y))
                val overlap = overlapWidth.toLong() * overlapHeight.toLong()

                if (overlap > bestOverlap) {
                    bestOverlap = overlap
                    bestMonitor = monitor
                }
            }
        }

        return getWorkArea(bestMonitor)
    }

    private fun getWorkArea(monitor: Long): WorkArea {
        val x = IntArray(1)
        val y = IntArray(1)
        val width = IntArray(1)
        val height = IntArray(1)
        GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height)
        return WorkArea(x[0], y[0], width[0], height[0])
    }

    private data class WindowState(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val fullscreen: Boolean,
        val maximized: Boolean,
        val floating: Boolean,
    )

    private data class WorkArea(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )
}
