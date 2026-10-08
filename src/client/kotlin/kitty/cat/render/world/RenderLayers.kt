package kitty.cat.render.world

import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.TextureTransform
import net.minecraft.client.renderer.feature.ItemFeatureRenderer
import net.minecraft.resources.Identifier

object RenderLayers {
    private val translucentArmorGlint = mutableMapOf<Identifier, RenderType>()

    @JvmStatic
    fun armorTranslucent(texture: Identifier, glint: Boolean): RenderType {
        if (!glint) return RenderTypes.wolfArmorCracks(texture)
        return translucentArmorGlint.getOrPut(texture) {
            RenderType.create(
                "kittycat_armor_translucent_glint",
                RenderSetup.builder(RenderPipelines.ARMOR_TRANSLUCENT_GLINT)
                    .withTexture("Sampler0", texture)
                    .withTexture("GlintSampler", ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR)
                    .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                    .useLightmap()
                    .useOverlay()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .withForcedSolidModelPhase()
                    .createRenderSetup()
            )
        }
    }

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
