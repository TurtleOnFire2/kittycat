package kitty.cat.features.visual

import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.player.PlayerModel
import kotlin.math.PI

class CatEarsModel(root: ModelPart) : PlayerModel(root, false) {
    companion object {
        private const val TEXTURE_SIZE = 64
        private const val EAR_TILT = (PI / 24.0).toFloat()

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
    }
}
