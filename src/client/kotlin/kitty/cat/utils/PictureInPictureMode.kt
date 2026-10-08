package kitty.cat.utils

import kitty.cat.KittycatClient.mc
import org.lwjgl.sdl.SDLVideo
import org.lwjgl.sdl.SDL_Rect
import org.lwjgl.system.MemoryStack
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

    private fun enable() = MemoryStack.stackPush().use { stack ->
        val window = mc.window
        val handle = window.handle()
        val positionX = stack.callocInt(1)
        val positionY = stack.callocInt(1)
        val width = stack.callocInt(1)
        val height = stack.callocInt(1)

        SDLVideo.SDL_GetWindowPosition(handle, positionX, positionY)
        SDLVideo.SDL_GetWindowSize(handle, width, height)

        savedState = WindowState(
            x = positionX[0],
            y = positionY[0],
            width = width[0],
            height = height[0],
            fullscreen = window.fullscreen,
            maximized = (SDLVideo.SDL_GetWindowFlags(handle) and SDLVideo.SDL_WINDOW_MAXIMIZED) != 0L,
            floating = (SDLVideo.SDL_GetWindowFlags(handle) and SDLVideo.SDL_WINDOW_ALWAYS_ON_TOP) != 0L,
        )

        val workArea = findCurrentMonitorWorkArea(handle, savedState!!)
        val pipWidth = (workArea.width * WIDTH_FRACTION).toInt()
            .coerceIn(MIN_WIDTH.coerceAtMost(workArea.width), MAX_WIDTH.coerceAtMost(workArea.width))
        val pipHeight = (pipWidth / ASPECT_RATIO).toInt()
            .coerceAtMost(workArea.height)

        if (window.fullscreen) {
            window.setWindowed(pipWidth, pipHeight)
        } else if (savedState!!.maximized) {
            SDLVideo.SDL_RestoreWindow(handle)
        }

        val frameLeft = stack.callocInt(1)
        val frameTop = stack.callocInt(1)
        val frameRight = stack.callocInt(1)
        val frameBottom = stack.callocInt(1)
        SDLVideo.SDL_GetWindowBordersSize(handle, frameTop, frameLeft, frameBottom, frameRight)

        val pipX = workArea.x + workArea.width - pipWidth - frameRight[0] - SCREEN_MARGIN
        val pipY = workArea.y + frameTop[0] + SCREEN_MARGIN

        SDLVideo.SDL_SetWindowSize(handle, pipWidth, pipHeight)
        SDLVideo.SDL_SetWindowPosition(handle, pipX, pipY)
        SDLVideo.SDL_SetWindowAlwaysOnTop(handle, true)
        SDLVideo.SDL_ShowWindow(handle)
    }

    private fun disable() {
        val state = savedState ?: return
        val window = mc.window
        val handle = window.handle()

        SDLVideo.SDL_SetWindowAlwaysOnTop(handle, state.floating)

        if (state.fullscreen) {
            if (!window.fullscreen) {
                window.setFullscreen(state.fullscreen)
                window.updateFullscreenIfChanged()
            }
        } else {
            if (window.fullscreen) {
                window.setFullscreen(state.fullscreen)
                window.updateFullscreenIfChanged()
            }

            SDLVideo.SDL_RestoreWindow(handle)
            SDLVideo.SDL_SetWindowSize(handle, state.width, state.height)
            SDLVideo.SDL_SetWindowPosition(handle, state.x, state.y)

            if (state.maximized) {
                SDLVideo.SDL_MaximizeWindow(handle)
            }
        }

        // F11 can still be pressed while PiP is active. Keep Minecraft's saved
        // fullscreen preference in sync with the state we just restored.
        mc.options.fullscreen().set(state.fullscreen)
        mc.options.save()

        savedState = null
    }

    private fun findCurrentMonitorWorkArea(handle: Long, state: WindowState): WorkArea {
        val attachedMonitor = if (state.fullscreen) SDLVideo.SDL_GetDisplayForWindow(handle) else 0
        if (attachedMonitor != 0) return getWorkArea(attachedMonitor)

        val monitors = SDLVideo.SDL_GetDisplays()
        var bestMonitor = SDLVideo.SDL_GetPrimaryDisplay()
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

    private fun getWorkArea(monitor: Int): WorkArea = MemoryStack.stackPush().use { stack ->
        val bounds = SDL_Rect.calloc(stack)
        check(SDLVideo.SDL_GetDisplayUsableBounds(monitor, bounds)) { "Cannot query display work area" }
        WorkArea(bounds.x(), bounds.y(), bounds.w(), bounds.h())
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
