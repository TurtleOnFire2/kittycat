package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Stun;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    void onTurnPlayer(double frameTime, CallbackInfo ci) {
        double[] adjusted = Stun.INSTANCE.onTurn(this.accumulatedDX, this.accumulatedDY);
        if (adjusted == null) return;

        this.accumulatedDX = adjusted[0];
        this.accumulatedDY = adjusted[1];
    }
}
