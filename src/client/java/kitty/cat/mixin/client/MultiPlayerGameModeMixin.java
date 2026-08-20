package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.Storm;
import kitty.cat.features.kuudra.AutoGFS;
import kitty.cat.features.kuudra.Fixes;
import kitty.cat.features.kuudra.Stun;
import kitty.cat.utils.BoneUtils;
import kitty.cat.features.kuudra.RendMacro;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    void useItem(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        Storm.INSTANCE.useItem(player, interactionHand, cir.getReturnValue());
        BoneUtils.INSTANCE.useItem(player, interactionHand, cir.getReturnValue());
        RendMacro.INSTANCE.useItem(player, interactionHand, cir.getReturnValue());
        Stun.INSTANCE.useItem(player, interactionHand, cir.getReturnValue());
        AutoGFS.INSTANCE.useItem(player, interactionHand, cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    void onUseItem(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
        if (Fixes.INSTANCE.cancelPlacement(player)) cir.cancel();
    }
}
