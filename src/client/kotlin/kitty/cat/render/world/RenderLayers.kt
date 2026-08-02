package kitty.cat.render.world

import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object RenderLayers {
    val FILLED = RenderType.create("noamm_filled", RenderSetup.builder(RenderPipelines.FILLED).createRenderSetup())
    val FILLED_THROUGH_WALLS = RenderType.create("noamm_filled_through_walls", RenderSetup.builder(RenderPipelines.FILLED_THROUGH_WALLS).createRenderSetup())

    val CIRCLE_FILLED = RenderType.create("noamm_circle_filled", RenderSetup.builder(RenderPipelines.CIRCLE_FILLED).createRenderSetup())
    val CIRCLE_FILLED_THROUGH_WALLS = RenderType.create("noamm_circle_filled_through_walls", RenderSetup.builder(RenderPipelines.CIRCLE_FILLED_THROUGH_WALLS).createRenderSetup())

    val LINES = RenderType.create("noamm_lines", RenderSetup.builder(RenderPipelines.LINES).createRenderSetup())
    val LINES_THROUGH_WALLS = RenderType.create("noamm_lines_through_walls", RenderSetup.builder(RenderPipelines.LINES_THROUGH_WALLS).createRenderSetup())
}