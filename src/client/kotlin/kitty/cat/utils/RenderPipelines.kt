package kitty.cat.utils

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines


object RenderPipelines {

    val LINES: RenderPipeline = RenderPipelines.LINES
    val LINES_THROUGH_WALLS: RenderPipeline = RenderPipelines.LINES
    val FILLED: RenderPipeline = RenderPipelines.DEBUG_FILLED_BOX
    val FILLED_THROUGH_WALLS: RenderPipeline = RenderPipelines.DEBUG_FILLED_BOX
    val QUADS_THROUGH_WALLS: RenderPipeline = RenderPipelines.DEBUG_QUADS
}
