package kitty.cat.utils

import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object RenderLayers {
    val LINES_THROUGH_WALLS = RenderType.create(
        "lines_through_walls",
        RenderSetup.builder(RenderPipelines.LINES_THROUGH_WALLS)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    )
}