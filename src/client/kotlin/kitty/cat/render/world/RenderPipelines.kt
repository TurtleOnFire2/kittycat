package kitty.cat.render.world

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines as VanillaRenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional


object RenderPipelines {

    val LINES: RenderPipeline = VanillaRenderPipelines.LINES
    val LINES_THROUGH_WALLS: RenderPipeline = VanillaRenderPipelines.register(
        RenderPipeline.builder(VanillaRenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/lines_through_walls"))
            .withDepthStencilState(Optional.empty())
            .build()
    )
    val FILLED: RenderPipeline = VanillaRenderPipelines.DEBUG_FILLED_BOX
    val FILLED_THROUGH_WALLS: RenderPipeline = VanillaRenderPipelines.register(
        RenderPipeline.builder(VanillaRenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/filled_through_walls"))
            .withDepthStencilState(Optional.empty())
            .build()
    )
    val QUADS_THROUGH_WALLS: RenderPipeline = FILLED_THROUGH_WALLS
}
