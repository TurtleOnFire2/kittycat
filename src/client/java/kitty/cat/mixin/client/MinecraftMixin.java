package kitty.cat.mixin.client;

import com.mojang.blaze3d.platform.Window;
import kitty.cat.features.kuudra.Fixes;
import kitty.cat.gui.ImGuiHandler;
import kitty.cat.features.misc.FarmHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private Window window;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initImGui(GameConfig args, CallbackInfo ci) {
        ImGuiHandler.INSTANCE.initialize(window.handle());
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void closeImGui(CallbackInfo ci) {
        ImGuiHandler.INSTANCE.dispose();
    }

    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean continueFarmAttackWithoutFocus(boolean attacking) {
        return attacking || FarmHelper.INSTANCE.shouldContinueUnfocusedAttack();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    void startUseItem(CallbackInfo ci) {
        if (Fixes.INSTANCE.cancelClick()) ci.cancel();
    }
}
