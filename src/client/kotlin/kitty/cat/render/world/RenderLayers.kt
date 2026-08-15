package kitty.cat.render.world

import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object RenderLayers {
    val LINES_THROUGH_WALLS = RenderType.create(
        "lines_through_walls",
        RenderSetup.builder(RenderPipelines.LINES_THROUGH_WALLS)
            .createRenderSetup()
    )

    val QUADS_THROUGH_WALLS = RenderType.create(
        "quads_through_walls",
        RenderSetup.builder(RenderPipelines.QUADS_THROUGH_WALLS)
            .sortOnUpload()
            .createRenderSetup()
    )
}
