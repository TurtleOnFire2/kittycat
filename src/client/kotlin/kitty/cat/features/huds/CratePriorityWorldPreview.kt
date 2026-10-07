package kitty.cat.features.huds

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import kitty.cat.KittycatClient.mc
import kitty.cat.features.kuudra.CratePriority
import kitty.cat.features.kuudra.EtherwarpWaypoints
import kitty.cat.render.world.Render3D.renderBoxBounds
import kitty.cat.render.world.poseScopeWithCamera
import kitty.cat.utils.KuudraUtils.kuudra
import kitty.cat.utils.KuudraUtils.supplies
import kitty.cat.utils.renderPos
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.sprite.SpriteId
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import java.awt.Color

object CratePriorityWorldPreview {
    private const val ALPHA = 80
    private const val FULL_BRIGHT = 15728880
    private var sprites: List<TextureAtlasSprite?>? = null
    private var lavaSprite: TextureAtlasSprite? = null

    fun register() {
        LevelRenderEvents.END_MAIN.register { context ->
            if (!CratePriority.enabled || !CratePriority.worldPreview.value || !kuudra() || !supplies()) return@register
            val player = mc.player ?: return@register
            val crate = CratePriority.missing
            val snapshot = KuudraPreviewTerrain.maps[crate] ?: return@register
            val waypoint = EtherwarpWaypoints.waypointFor(crate) ?: return@register
            val palette = sprites ?: KuudraPreviewTerrain.palette.map { texture ->
                texture?.let { mc.atlasManager.get(SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.parse(it))) }
            }.also { sprites = it }
            val lava = lavaSprite ?: mc.atlasManager.get(
                SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.parse("minecraft:block/lava_still"))
            ).also { lavaSprite = it }
            val offset = player.renderPos.subtract(waypoint)

            context.poseStack().poseScopeWithCamera { stack ->
                context.submitNodeCollector().submitCustomGeometry(
                    stack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                ) { pose, buffer ->
                    snapshot.rows.forEachIndexed { z, row ->
                        row.forEachIndexed { x, code ->
                            val worldX = snapshot.originX + x + offset.x
                            val worldZ = snapshot.originZ + z + offset.z
                            val sprite = palette[code.digitToInt()]
                            if (sprite == null) {
                                lavaSurface(pose, buffer, worldX.toFloat(), (75.0 + offset.y).toFloat(), worldZ.toFloat(), lava)
                            } else {
                                val worldY = snapshot.topBlockY(x, z) + offset.y
                                cube(pose, buffer, worldX.toFloat(), worldY.toFloat(), worldZ.toFloat(), sprite)
                            }
                        }
                    }
                }
            }
            CratePriority.previewSupply(crate)?.let { supply ->
                val point = supply.position.add(offset)
                val radius = if (supply.fromChat) 0.55 else 0.25
                context.renderBoxBounds(
                    AABB(point.x - radius, point.y - radius, point.z - radius,
                        point.x + radius, point.y + radius, point.z + radius),
                    Color(255, 186, 89, 220), Color(255, 186, 89, 80),
                    depthTest = false
                )
            }
        }
    }

    private fun lavaSurface(pose: PoseStack.Pose, buffer: VertexConsumer, x: Float, y: Float, z: Float, sprite: TextureAtlasSprite) {
        fun vertex(px: Float, pz: Float, u: Float, v: Float) {
            buffer.addVertex(pose, px, y, pz)
                .setColor(255, 255, 255, ALPHA)
                .setUv(u, v)
                .setOverlay(0)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0f, 1f, 0f)
        }
        vertex(x, z, sprite.u0, sprite.v0)
        vertex(x, z + 1f, sprite.u0, sprite.v1)
        vertex(x + 1f, z + 1f, sprite.u1, sprite.v1)
        vertex(x + 1f, z, sprite.u1, sprite.v0)
    }

    private fun cube(pose: PoseStack.Pose, buffer: VertexConsumer, x: Float, y: Float, z: Float, sprite: TextureAtlasSprite) {
        val x1 = x + 1f
        val y1 = y + 1f
        val z1 = z + 1f
        fun vertex(px: Float, py: Float, pz: Float, u: Float, v: Float, nx: Float, ny: Float, nz: Float) {
            buffer.addVertex(pose, px, py, pz)
                .setColor(255, 255, 255, ALPHA)
                .setUv(u, v)
                .setOverlay(0)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz)
        }
        fun face(
            nx: Float, ny: Float, nz: Float,
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float
        ) {
            vertex(ax, ay, az, sprite.u0, sprite.v0, nx, ny, nz)
            vertex(bx, by, bz, sprite.u0, sprite.v1, nx, ny, nz)
            vertex(cx, cy, cz, sprite.u1, sprite.v1, nx, ny, nz)
            vertex(dx, dy, dz, sprite.u1, sprite.v0, nx, ny, nz)
        }
        face(0f, 1f, 0f, x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z)
        face(0f, -1f, 0f, x, y, z1, x, y, z, x1, y, z, x1, y, z1)
        face(0f, 0f, -1f, x1, y, z, x, y, z, x, y1, z, x1, y1, z)
        face(0f, 0f, 1f, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1)
        face(-1f, 0f, 0f, x, y, z, x, y, z1, x, y1, z1, x, y1, z)
        face(1f, 0f, 0f, x1, y, z1, x1, y, z, x1, y1, z, x1, y1, z1)
    }
}
