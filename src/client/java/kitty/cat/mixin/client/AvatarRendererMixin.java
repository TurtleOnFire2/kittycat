package kitty.cat.mixin.client;

import kitty.cat.render.state.CatTailRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void kittycat$captureVerticalVelocity(
            Avatar avatar,
            AvatarRenderState renderState,
            float partialTick,
            CallbackInfo ci
    ) {
        ((CatTailRenderState) renderState).setKittycatTailVerticalVelocity((float) avatar.getDeltaMovement().y);
    }
}
