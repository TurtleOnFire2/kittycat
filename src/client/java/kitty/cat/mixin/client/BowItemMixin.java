package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.Storm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public class BowItemMixin {
    @Inject(method = "Lnet/minecraft/world/item/BowItem;releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"))
    void releaseBow(ItemStack itemStack, Level level, LivingEntity livingEntity, int i, CallbackInfoReturnable<Boolean> cir) {
        Storm.INSTANCE.bowReleased(itemStack, livingEntity);
    }
}
