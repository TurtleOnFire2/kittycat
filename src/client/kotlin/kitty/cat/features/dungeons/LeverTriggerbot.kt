package kitty.cat.features.dungeons

import kitty.cat.gui.categories.Categories
import kitty.cat.gui.features.Feature
import kitty.cat.utils.Schedule.schedule
import kitty.cat.utils.add
import kitty.cat.utils.canInteract
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult

object LeverTriggerbot: Feature("Lever Triggerbot", "", Categories.Category.DUNGEONS) {
    val forGate = booleanSetting("For gate levers")
    val forDevice = booleanSetting("For device levers")

    val gateLevers = listOf(
        BlockPos(106, 124, 113),
        BlockPos(94, 124, 113),
        BlockPos(23, 132, 138),
        BlockPos(27, 124, 127),
        BlockPos(2, 122, 55),
        BlockPos(14, 122, 55),
        BlockPos(84, 121, 34),
        BlockPos(86, 128, 46),
    )

    val deviceLevers = listOf(
        BlockPos(62, 133, 142),
        BlockPos(58, 133, 142),
        BlockPos(60, 134, 142),
        BlockPos(60, 135, 142),
        BlockPos(62, 136, 142),
        BlockPos(58, 136, 142)
    )

    val clicked = mutableListOf<BlockPos>()

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!enabled || client.screen != null || client.player == null) return@register

            val hr = client.hitResult as? BlockHitResult ?: return@register

            val shape = client.level?.getBlockState(hr.blockPos)?.getShape(client.level!!, hr.blockPos) ?: return@register

            if (shape.isEmpty || !shape.bounds().add(hr.blockPos).canInteract(4.5)) return@register

            if (forGate.value && gateLevers.contains(hr.blockPos) && !clicked.contains(hr.blockPos)) {
                client.options.keyUse.clickCount++

                clicked.add(hr.blockPos)
                schedule(20) {
                    clicked.remove(hr.blockPos)
                }
            } else if (forDevice.value && deviceLevers.contains(hr.blockPos) && !clicked.contains(hr.blockPos)) {
                client.options.keyUse.clickCount++
                clicked.add(hr.blockPos)
                schedule(20) {
                    clicked.remove(hr.blockPos)
                }
            }
        }
    }
}