package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Fixes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @ModifyArgs(method = "raycastHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    void getClosestHit(Args args) {
        if (Fixes.INSTANCE.clickThrough()) {
            args.set(2, (Predicate<Entity>) entity -> false);
        }
    }

    @ModifyArgs(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private static void getEntityHitResult(Args args) {
        if (Fixes.INSTANCE.clickThrough()) {
            args.set(4, (Predicate<Entity>) entity -> false);
        }
    }
}
