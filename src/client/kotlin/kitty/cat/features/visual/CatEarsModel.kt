package kitty.cat.features.visual

import kitty.cat.render.state.CatTailRenderState
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CatEarsModel(root: ModelPart) : PlayerModel(root, false) {
    private val tailBase = body.getChild(TAIL_BASE)
    private val tailMiddle = tailBase.getChild(TAIL_MIDDLE)
    private val tailOuter = tailMiddle.getChild(TAIL_OUTER)
    private val tailTip = tailOuter.getChild(TAIL_TIP)

    override fun setupAnim(state: AvatarRenderState) {
        super.setupAnim(state)

        val speed = state.walkAnimationSpeed.coerceIn(0.0f, 1.0f)
        val stride = state.walkAnimationPos * 0.9f
        val sway = sin(stride) * 0.42f * speed
        val bounce = cos(stride * 2.0f) * 0.04f * speed
        val crouchDrop = if (state.isCrouching) -0.12f else 0.0f
        val verticalVelocity = (state as CatTailRenderState).kittycatTailVerticalVelocity
        val verticalPitch = (-verticalVelocity * 0.65f).coerceIn(-0.32f, 0.24f)

        // Spread the rotation across the chain for a soft curve instead of
        // swinging the entire tail as one rigid block. Negative X rotation
        // makes the resting tail hang down instead of curling upward.
        tailBase.xRot = -0.35f + bounce + crouchDrop + verticalPitch * 0.5f
        tailMiddle.xRot = -0.15f + bounce * 0.5f + verticalPitch * 0.3f
        tailOuter.xRot = -0.10f + verticalPitch * 0.2f
        tailTip.xRot = -0.05f

        tailBase.yRot = sway * 0.25f
        tailMiddle.yRot = sway * 0.35f
        tailOuter.yRot = sway * 0.45f
        tailTip.yRot = sway * 0.55f
    }

    companion object {
        private const val TEXTURE_SIZE = 64
        private const val EAR_TILT = (PI / 24.0).toFloat()
        private const val TAIL_BASE = "cat_tail_base"
        private const val TAIL_MIDDLE = "cat_tail_middle"
        private const val TAIL_OUTER = "cat_tail_outer"
        private const val TAIL_TIP = "cat_tail_tip"

        fun createLayer(): LayerDefinition {
            val mesh = createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false)
            val root = mesh.root.clearRecursively()
            val head = root.getChild("head")

            head.addOrReplaceChild(
                "left_cat_ear",
                createEar(),
                PartPose.offsetAndRotation(2.5f, -8.0f, 0.0f, 0.0f, 0.0f, -EAR_TILT)
            )
            head.addOrReplaceChild(
                "right_cat_ear",
                createEar(),
                PartPose.offsetAndRotation(-2.5f, -8.0f, 0.0f, 0.0f, 0.0f, EAR_TILT)
            )

            val body = root.getChild("body")
            val tailBase = body.addOrReplaceChild(
                TAIL_BASE,
                tailSegment(width = 3.0f, height = 3.0f, length = 3.75f),
                PartPose.offset(0.0f, 10.0f, 2.0f)
            )
            val tailMiddle = tailBase.addOrReplaceChild(
                TAIL_MIDDLE,
                tailSegment(width = 3.5f, height = 3.5f, length = 3.75f),
                PartPose.offset(0.0f, 0.0f, 3.25f)
            )
            val tailOuter = tailMiddle.addOrReplaceChild(
                TAIL_OUTER,
                tailSegment(width = 3.75f, height = 3.75f, length = 3.75f),
                PartPose.offset(0.0f, 0.0f, 3.25f)
            )
            tailOuter.addOrReplaceChild(
                TAIL_TIP,
                tailSegment(width = 3.25f, height = 3.25f, length = 3.5f),
                PartPose.offset(0.0f, 0.0f, 3.25f)
            )

            return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE)
        }

        private fun createEar(): CubeListBuilder = CubeListBuilder.create()
            // Three small steps form a pointed, Minecraft-style silhouette while
            // keeping the baked mesh tiny (six cuboids for both ears in total).
            .texOffs(8, 8)
            .addBox(-1.875f, -1.25f, -0.875f, 3.75f, 1.25f, 1.75f)
            .texOffs(8, 8)
            .addBox(-1.25f, -2.5f, -0.875f, 2.5f, 1.25f, 1.75f)
            .texOffs(8, 8)
            .addBox(-0.625f, -3.75f, -0.875f, 1.25f, 1.25f, 1.75f)

        private fun tailSegment(width: Float, height: Float, length: Float): CubeListBuilder =
            CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-width / 2.0f, -height / 2.0f, -0.25f, width, height, length)
    }
}
