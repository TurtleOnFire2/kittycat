package kitty.cat.utils

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

object Pipelines {
    lateinit var TRAIL_3D_GLOW: RenderPipeline
        private set
    lateinit var TRAIL_GLOW_RENDER_TYPE: RenderType
        private set

    fun init() {
        val trail3dGlowSnippet = RenderPipeline.builder(
            RenderPipelines.MATRICES_FOG_SNIPPET
        ).withVertexShader(Identifier.fromNamespaceAndPath("kittycat", "trail_glow_3d"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("kittycat", "trail_glow_3d"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .buildSnippet()

        TRAIL_3D_GLOW = RenderPipelines.register(
            RenderPipeline.builder(trail3dGlowSnippet)
                .withLocation(Identifier.fromNamespaceAndPath("kittycat", "pipeline/trail_3d_glow"))
                .withBlend(BlendFunction.ADDITIVE)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()
        )

        TRAIL_GLOW_RENDER_TYPE = RenderType.create(
            "kittycat_trail_glow",
            RenderSetup.builder(TRAIL_3D_GLOW)
                .bufferSize(786432)
                .createRenderSetup()
        )
    }
}