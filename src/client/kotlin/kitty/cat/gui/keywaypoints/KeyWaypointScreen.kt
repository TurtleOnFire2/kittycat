package kitty.cat.gui.keywaypoints

import com.mojang.blaze3d.platform.InputConstants

import kitty.cat.features.misc.KeyWaypoints
import kitty.cat.features.misc.KeyWaypoints.KeyAction
import kitty.cat.features.misc.KeyWaypoints.Waypoint
import kitty.cat.utils.GuiUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class KeyWaypointScreen(private val waypoint: Waypoint) : Screen(Component.literal("Key Waypoint")) {
    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(px: Double, py: Double) = px >= x && px < x + w && py >= y && py < y + h
    }

    private fun panel() = Rect(width / 2 - 150, height / 2 - 120, 300, 240)
    private fun keyRect(index: Int): Rect {
        val p = panel()
        val cx = p.x + p.w / 2
        val cy = p.y + 117
        return when (index) {
            0 -> Rect(cx - 42, cy - 58, 84, 42) // W
            1 -> Rect(cx - 132, cy - 10, 84, 42) // A
            2 -> Rect(cx - 42, cy + 38, 84, 42) // S
            else -> Rect(cx + 48, cy - 10, 84, 42) // D
        }
    }
    private fun deleteRect() = panel().let { Rect(it.x + 12, it.y + it.h - 28, 112, 18) }
    private fun doneRect() = panel().let { Rect(it.x + it.w - 100, it.y + it.h - 28, 88, 18) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks)
        val p = panel()
        GuiUtils.renderRectangle(graphics, 0, 0, width, height, 0x88000000.toInt())
        GuiUtils.renderRoundedRectangle(graphics, p.x, p.y, p.w, p.h, 5, 0xF01B1422.toInt())
        GuiUtils.renderRoundedOutline(graphics, p.x, p.y, p.w, p.h, 5, 1, 0xFFB681A7.toInt())
        graphics.centeredText(minecraft.font, "Key Waypoint", width / 2, p.y + 12, 0xFFFFFFFF.toInt())
        graphics.centeredText(minecraft.font, "${waypoint.pos.x}, ${waypoint.pos.y}, ${waypoint.pos.z}", width / 2, p.y + 27, 0xFFBFB1C4.toInt())

        val letters = "WASD"
        repeat(4) { index ->
            val rect = keyRect(index)
            val action = waypoint.actions[index]
            val fill = when (action) {
                KeyAction.NOTHING -> 0xFF403A48.toInt()
                KeyAction.PRESS -> 0xFF276D4C.toInt()
                KeyAction.UNPRESS -> 0xFF873A42.toInt()
            }
            GuiUtils.renderRoundedRectangle(graphics, rect.x, rect.y, rect.w, rect.h, 4, fill)
            GuiUtils.renderRoundedOutline(graphics, rect.x, rect.y, rect.w, rect.h, 4, 1,
                if (rect.contains(mouseX.toDouble(), mouseY.toDouble())) 0xFFFFFFFF.toInt() else 0xFFAD95AE.toInt())
            graphics.centeredText(minecraft.font, letters[index].toString(), rect.x + rect.w / 2, rect.y + 6, 0xFFFFFFFF.toInt())
            graphics.centeredText(minecraft.font, action.label, rect.x + rect.w / 2, rect.y + 23, 0xFFFFFFFF.toInt())
        }

        val delete = deleteRect()
        val done = doneRect()
        GuiUtils.renderRoundedRectangle(graphics, delete.x, delete.y, delete.w, delete.h, 3, 0xFF74303B.toInt())
        GuiUtils.renderRoundedRectangle(graphics, done.x, done.y, done.w, done.h, 3, 0xFF425C74.toInt())
        graphics.centeredText(minecraft.font, "Remove waypoint", delete.x + delete.w / 2, delete.y + 5, 0xFFFFFFFF.toInt())
        graphics.centeredText(minecraft.font, "Done", done.x + done.w / 2, done.y + 5, 0xFFFFFFFF.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubled)
        repeat(4) { index ->
            if (keyRect(index).contains(event.x(), event.y())) {
                KeyWaypoints.cycle(waypoint, index)
                return true
            }
        }
        if (deleteRect().contains(event.x(), event.y())) {
            KeyWaypoints.delete(waypoint)
            onClose()
            return true
        }
        if (doneRect().contains(event.x(), event.y())) {
            onClose()
            return true
        }
        return super.mouseClicked(event, doubled)
    }

    override fun isPauseScreen() = false
}
