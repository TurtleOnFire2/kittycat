package kitty.cat.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixn {
    @Inject(method = "createParticle", at = @At("HEAD"))
    void onAddParticle(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
        // Particle handling has not been implemented yet. Keep this scaffold
        // self-contained so an unfinished feature cannot break development runs.
    }
}
