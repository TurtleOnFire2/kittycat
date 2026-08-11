package kitty.cat.mixin.client;

import kitty.cat.utils.RotationUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void updateRotation(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        RotationUtils.onFrame();
    }
}
