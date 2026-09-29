package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Drone;
import kitty.cat.features.kuudra.Fixes;
import kitty.cat.features.misc.FarmHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean continueFarmAttackWithoutFocus(boolean attacking) {
        return attacking || FarmHelper.INSTANCE.shouldContinueUnfocusedAttack();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    void startUseItem(CallbackInfo ci) {
        if (Fixes.INSTANCE.cancelClick()) ci.cancel();
    }
}
