package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.RendMacro.offsetBack
import kitty.cat.features.kuudra.RendMacro.offsetFront
import kitty.cat.features.kuudra.RendMacro.offsetLeft
import kitty.cat.features.kuudra.RendMacro.offsetRight
import kitty.cat.gui.categories.Categories
import kitty.cat.features.Feature
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.canInteract
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import java.awt.Color

object Terminals: Feature("Terminals", "", Categories.Category.DUNGEONS) {
    val triggerbot = booleanSetting("Triggerbot", false)
    val showHitbox = booleanSetting("Show Hitbox", false)

    val terminalRegex = Regex("Inactive Terminal|CLICK HERE")
    var previousXZ: Pair<Int, Int>? = null

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register {
             val hr = it.hitResult as? EntityHitResult ?: return@register

            if (!enabled || !triggerbot.value || it.screen != null || hr.entity !is ArmorStand || !terminalRegex.matches(hr.entity.name.string)) return@register
            val xy = hr.entity.x.toInt() to hr.entity.z.toInt()
            if (xy == previousXZ) return@register
            previousXZ = xy
            it.options.keyUse.clickCount++
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !showHitbox.value) return@register

            mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()?.forEach { entity ->
                if (!entity.name.string.matches(terminalRegex)) return@forEach

                val aabb = entity.boundingBox

                val color = if (aabb.canInteract()) Color.GREEN else Color.RED

                ctx.renderBoxBounds(aabb, color, phase = true)
            }
        }
    }
}
