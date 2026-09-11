package kitty.cat.features.kuudra

import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.KuudraUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import java.awt.Color
import java.util.Locale

object KuudraDisplay: Feature("Kuudra Display", "", Categories.Category.KUUDRA) {
    val highlightKuudra = booleanSetting("Highlight Kuudra")
    val showHp = booleanSetting("Show HP")

    var kuudra: Entity? = null
    var hpString = ""


    fun register() {
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled) return@register

            if (highlightKuudra.value) {
                ctx.renderBoxBounds(kuudra?.boundingBox ?: return@register, Color.RED)
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            kuudra = if (enabled) KuudraUtils.kuudraEntity else null
            val health = (kuudra as? LivingEntity)?.health
            hpString = if (!showHp.value || health == null || health == 1024f) "" else formatHealth(health)
        }
    }

    fun formatHealth(health: Float): String = if (health >= 25000) {
        val percent = health / 100000.0 * 100.0
        "§c${"%.1f".format(Locale.ROOT, percent)}§8/§a100%"
    } else {
        "§c${"%.1f".format(Locale.ROOT, health * 9.6 / 1000)}m§8/§a240m §c❤"
    }
}
