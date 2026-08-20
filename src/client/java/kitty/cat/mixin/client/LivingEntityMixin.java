package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Stun;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void hasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (
                Stun.INSTANCE.getNoBlind().getValue() &&
                        self == Minecraft.getInstance().player &&
                        effect != null &&
                        effect.is(MobEffects.BLINDNESS)
        ) {
            cir.setReturnValue(false);
        }
    }
}
