package kitty.cat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import kitty.cat.features.dungeons.Deathbow;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {


    @Inject(method = "renderItem", at = @At("HEAD"))
    private void kittycat$tintBegin(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        Deathbow.INSTANCE.setTintActive(true);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void kittycat$tintEnd(CallbackInfo ci) {
        Deathbow.INSTANCE.setTintActive(false);
    }
}
