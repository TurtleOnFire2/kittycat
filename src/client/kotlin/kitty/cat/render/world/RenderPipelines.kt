package kitty.cat.render.world

import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import net.minecraft.client.renderer.RenderPipelines as VanillaRenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional


object RenderPipelines {

    val ARMOR_TRANSLUCENT_GLINT: RenderPipeline = VanillaRenderPipelines.register(
        RenderPipeline.builder(VanillaRenderPipelines.ENTITY_SNIPPET, VanillaRenderPipelines.GLINT_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/armor_translucent_glint"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build()
    )

    val LINES: RenderPipeline = VanillaRenderPipelines.LINES
    val LINES_THROUGH_WALLS: RenderPipeline = VanillaRenderPipelines.register(
        RenderPipeline.builder(VanillaRenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/lines_through_walls"))
            .withColorTargetState(ColorTargetState.DEFAULT)
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
