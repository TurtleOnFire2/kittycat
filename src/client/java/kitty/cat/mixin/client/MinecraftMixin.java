package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Fixes;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    void startUseItem(CallbackInfo ci) {
        if (Fixes.INSTANCE.cancelClick()) ci.cancel();
    }
}
