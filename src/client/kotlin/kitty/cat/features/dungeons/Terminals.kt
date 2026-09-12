package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.RendMacro.offsetBack
import kitty.cat.features.kuudra.RendMacro.offsetFront
import kitty.cat.features.kuudra.RendMacro.offsetLeft
import kitty.cat.features.kuudra.RendMacro.offsetRight
import kitty.cat.gui.categories.Categories
import kitty.cat.features.Feature
import kitty.cat.render.world.Render3D.BoxRender
import kitty.cat.render.world.Render3D.renderBoxesBounds
import kitty.cat.utils.canInteract
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import java.awt.Color

object Terminals: Feature("Terminals", "", Categories.Category.DUNGEONS) {
    val triggerbot = booleanSetting("Triggerbot", false)
    val showHitbox = booleanSetting("Show Hitbox", false)

    private val terminalNames = setOf("Inactive Terminal", "CLICK HERE")
    var previousXZ: Pair<Int, Int>? = null

    private val targets = mutableListOf<ArmorStand>()

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register {
             val hr = it.hitResult as? EntityHitResult ?: return@register

            if (!enabled || !triggerbot.value || it.screen != null || hr.entity !is ArmorStand || hr.entity.name.string !in terminalNames) return@register
            val xy = hr.entity.x.toInt() to hr.entity.z.toInt()
            if (xy == previousXZ) return@register
            previousXZ = xy
            it.options.keyUse.clickCount++
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            targets.clear()
            if (!enabled || !showHitbox.value) return@register
            mc.level?.entitiesForRendering()?.forEach {
                if (it is ArmorStand && it.name.string in terminalNames) targets.add(it)
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> targets.clear() }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || mc.level == null || !showHitbox.value) return@register

            val boxes = targets.mapNotNull { entity ->
                if (!entity.isAlive) return@mapNotNull null
                val aabb = entity.boundingBox
                BoxRender(aabb, if (aabb.canInteract()) Color.GREEN else Color.RED)
            }
            ctx.renderBoxesBounds(boxes)
        }
    }
}
