package kitty.cat.features.dungeons

import kitty.cat.KittycatClient.mc
import kitty.cat.gui.categories.Categories
import kitty.cat.features.Feature
import kitty.cat.features.settings.cheat
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.utils.ClickUtils
import kitty.cat.utils.LocationUtils
import kitty.cat.utils.canInteract
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color

object Relics: Feature("Relics", "Features for M7 relics", Categories.Category.DUNGEONS) {
    val relicTriggerbot = booleanSetting("Relic triggerbot")
    val cauldronTriggerbot = booleanSetting("Cauldron triggerbot", false).cheat()
    val renderSpawnBox = booleanSetting("Render spawn box", false)

    var active = false
    var cd = 0

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (mc.player == null || !enabled || !cauldronTriggerbot.value || !LocationUtils.inF7Boss) return@register

            val hr = mc.hitResult ?: return@register

            if (hr.type == HitResult.Type.MISS) return@register

            if (hr is BlockHitResult && cauldronTriggerbot.value && active) {
                val cauldron = Relic.entries.find { it.hoverName == mc.player?.mainHandItem?.hoverName?.string }?.cauldronPos ?: return@register
                if (hr.blockPos == cauldron || hr.blockPos == cauldron.below()) {
                    mc.options.keyUse.clickCount++
                    active = false
                }
            }

            if (hr is EntityHitResult && relicTriggerbot.value) {
                if ((hr.entity as? ArmorStand)?.getItemBySlot(EquipmentSlot.HEAD)?.displayName?.string?.contains("Relic") ?: return@register && --cd < 0) {
                    ClickUtils.rightClickEntity(hr.entity)
                    cd = 10
                }
            }
        }
        LevelRenderEvents.END_MAIN.register { ctx ->
            if (!enabled || !renderSpawnBox.value || !LocationUtils.inF7Boss) return@register
            Relic.entries.forEach {
                ctx.renderBoxBounds(it.aabb, if (it.aabb.canInteract()) Color.GREEN else Color.RED, phase = true)
            }
        }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { minecraft, level ->
            active = false
            cd = 0
        }
    }

    fun handleChat(unformatted: String) {
        if (!enabled) return
        if (unformatted.contains(mc.player?.name?.string ?: return) && unformatted.contains("picked the Corrupted")) {
            active = true
        }
    }

    private enum class Relic(val hoverName: String, val cauldronPos: BlockPos, val aabb: AABB) {
        Green("Corrupted Green Relic", BlockPos(49, 7, 44), AABB(20.914, 6.726, 94.914, 21.086, 6.898, 95.086)),
        Purple("Corrupted Purple Relic", BlockPos(54, 7, 41), AABB(56.914, 8.726, 132.914, 57.086, 8.898, 133.086)),
        Blue("Corrupted Blue Relic", BlockPos(59, 7, 44), AABB(91.914, 6.726, 94.914, 92.086, 6.898, 95.086)),
        Orange("Corrupted Orange Relic", BlockPos(57, 7, 42), AABB(92.914, 6.726, 56.914, 93.086, 6.898, 57.086)),
        Red("Corrupted Red Relic", BlockPos(51, 7, 42), AABB(20.914, 6.726, 59.914, 21.086, 6.898, 60.086))
    }
}
