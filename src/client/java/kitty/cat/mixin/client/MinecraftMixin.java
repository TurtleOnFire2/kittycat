package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Drone;
import kitty.cat.features.kuudra.Fixes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    void startUseItem(CallbackInfo ci) {
        if (Fixes.INSTANCE.cancelClick()) ci.cancel();
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceGameTime(J)I"))
    int advanceTime(DeltaTracker.Timer instance, long currentMs, boolean advanceGameTime) {
        if (Drone.INSTANCE.getFreeze()) return  0;
        return instance.advanceGameTime(currentMs);
    }
}
