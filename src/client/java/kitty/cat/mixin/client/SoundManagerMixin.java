package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Build;
import kitty.cat.utils.KuudraUtils;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @ModifyVariable(method = "getSoundEvent", at = @At("HEAD"), argsOnly = true, name = "location")
    private Identifier kittycat$replaceBuildSound(Identifier original) {
        if (Build.INSTANCE.getEnabled()
                && !Build.INSTANCE.getPlayingStunSound()
                && Build.INSTANCE.getReplaceSounds().getValue()
                && KuudraUtils.INSTANCE.build()) {
            return Identifier.parse(Build.INSTANCE.getReplacementSound().getValue());
        }
        return original;
    }
}
