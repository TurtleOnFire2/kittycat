package kitty.cat.features.kuudra

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.text
import kitty.cat.utils.Chat
import kitty.cat.utils.aabb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.Vec3
import java.awt.Color

object BackboneAlert : Feature("Backbone Alert", "", Categories.Category.KUUDRA) {
    val time = numberSetting("Time", 5.0, 40.0, 10.0, "t", 1.0)
    val showGear = booleanSetting("Show time, item and helmet on backbone", false)
    val drawWaypoint = booleanSetting("Draw waypoint for backbone pos", false)

    var p4Start = 0

    var pos: Vec3? = null

    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !drawWaypoint.value || pos == null) return@register
            ctx.text("BB", pos!!.add(0.0, 1.0, 0.0), Color.WHITE.rgb, 4f)
            ctx.renderBoxBounds(pos!!.aabb(0.5), Color.ORANGE)
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, level ->
            pos = null
        }
    }

    fun serverTick() {
        p4Start++
    }

    fun alert() {
        if (!showGear.value || !enabled) return

        val item = mc.player?.mainHandItem
        val helmet = mc.player?.getItemBySlot(EquipmentSlot.HEAD)

        val seconds = (p4Start / 20.0)

        Chat.send("Backbone hit with ", item?.displayName ?: "null", " and ", helmet?.displayName ?: "null", " at ${seconds}s.")
    }
}
