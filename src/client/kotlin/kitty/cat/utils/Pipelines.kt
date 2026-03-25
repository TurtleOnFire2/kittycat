package kitty.cat.utils

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object Pipelines {

    // ── 3D (world-space) ─────────────────────────────────────────────────────

    private val TRAIL_3D_SNIPPET = RenderPipeline.builder(
        RenderPipelines.MATRICES_FOG_SNIPPET
    ).withVertexShader(Identifier.fromNamespaceAndPath("kittycat", "trail_3d"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("kittycat", "trail_3d"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .buildSnippet()

    private val TRAIL_3D_GLOW_SNIPPET = RenderPipeline.builder(
        RenderPipelines.MATRICES_FOG_SNIPPET
    ).withVertexShader(Identifier.fromNamespaceAndPath("kittycat", "trail_glow_3d"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("kittycat", "trail_glow_3d"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .buildSnippet()

    val TRAIL_3D: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(TRAIL_3D_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/trail_3d"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    )

    val TRAIL_3D_GLOW: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(TRAIL_3D_GLOW_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/trail_3d_glow"))
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()
    )
}
