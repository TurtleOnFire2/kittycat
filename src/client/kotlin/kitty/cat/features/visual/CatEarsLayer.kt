package kitty.cat.features.visual

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes

class CatEarsLayer(
    parent: RenderLayerParent<AvatarRenderState, PlayerModel>
) : RenderLayer<AvatarRenderState, PlayerModel>(parent) {
    // The ears and tail are baked once and share a single model submission.
    private val model = CatEarsModel(CatEarsModel.createLayer().bakeRoot())

    override fun submit(
        poseStack: PoseStack,
        nodeCollector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float
    ) {
        if (!CatEars.shouldRender(state)) return

        val texture = state.skin.body().texturePath()
        val tint = CatEars.tintArgb()
        val renderType = if (CatEars.tint.alpha == 255) {
            RenderTypes.entityCutout(texture)
        } else {
            RenderTypes.entityTranslucent(texture)
        }

        nodeCollector.submitModel(
            model,
            state,
            poseStack,
            renderType,
            packedLight,
            LivingEntityRenderer.getOverlayCoords(state, 0.0f),
            tint,
            null,
            state.outlineColor,
            null
        )
    }
}
