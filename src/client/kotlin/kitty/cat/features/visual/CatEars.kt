package kitty.cat.features.visual

import kitty.cat.KittycatClient.mc
import kitty.cat.features.Feature
import kitty.cat.gui.categories.Categories
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState

object CatEars : Feature(
    "Cat Ears & Tail",
    "Renders lightweight cat ears and an animated fluffy tail on your player.",
    Categories.Category.VISUAL
) {
    val ears = booleanSetting(
        name = "Ears",
        defaultValue = true,
        description = "Renders cat ears on enabled players."
    )

    val tail = booleanSetting(
        name = "Tail",
        defaultValue = true,
        description = "Renders the animated cat tail on enabled players."
    )

    val tint = colorSetting(
        name = "Tint",
        description = "Tints the player's skin texture on the ears and tail. White keeps the original skin colors."
    )

    private val otherPlayers = booleanSetting(
        name = "Other Players",
        defaultValue = false,
        description = "Also renders the enabled cat accessories on other players."
    )

    fun register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register { _, renderer, helper, _ ->
            if (renderer !is AvatarRenderer<*>) return@register

            @Suppress("UNCHECKED_CAST")
            val playerRenderer = renderer as RenderLayerParent<AvatarRenderState, PlayerModel>
            helper.register(CatEarsLayer(playerRenderer))
        }
    }

    fun shouldRender(state: AvatarRenderState): Boolean {
        if (!enabled || state.isInvisible || (!ears.value && !tail.value)) return false
        return otherPlayers.value || state.id == mc.player?.id
    }

    fun tintArgb(): Int =
        (tint.alpha shl 24) or (tint.red shl 16) or (tint.green shl 8) or tint.blue
}
