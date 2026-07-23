package kitty.cat.utils

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import java.util.Optional


object RenderPipelines {

    val LINES: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation("pipeline/lines")
            .build()
    )

    val LINES_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation("pipeline/lines")
            .withDepthStencilState(Optional.empty())
            .build()
    )

    val FILLED: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/debug_filled_box")
            .build()
    )

    val FILLED_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/debug_filled_box")
            .withDepthStencilState(Optional.empty())
            .build()
    )


    val QUADS_THROUGH_WALLS: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/debug_quads_through_walls")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(Optional.empty())
            .build()
    )
}
