package kitty.cat.render.world

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import kitty.cat.KittycatClient
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional

object RenderPipelines {
    val FILLED = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).apply {
            withLocation(id("pipeline/filled"))
            withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
        }.build()
    )

    val CIRCLE_FILLED = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).apply {
            withLocation(id("pipeline/circle_filled"))
            withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
        }.build()
    )

    val LINES_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).apply {
            withLocation(id("pipeline/lines_through_walls"))
            withDepthStencilState(Optional.empty())
        }.build()
    )

    val LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET).apply {
            withLocation(id("pipeline/lines"))
        }.build()
    )

    val FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).apply {
            withLocation(id("pipeline/filled_through_walls"))
            withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            withDepthStencilState(Optional.empty())
        }.build()
    )

    val CIRCLE_FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).apply {
            withLocation(id("pipeline/circle_filled_through_walls"))
            withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            withDepthStencilState(Optional.empty())
        }.build()
    )

    private fun id(path: String) = Identifier.fromNamespaceAndPath("kittycat", path)
}
