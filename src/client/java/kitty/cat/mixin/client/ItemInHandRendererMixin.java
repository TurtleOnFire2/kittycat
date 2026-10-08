package kitty.cat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kitty.cat.features.dungeons.Storm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {


    @WrapOperation(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 2
    )
    private void kittycat$tintItem(ItemStackRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                  int light, int overlay, int outline, Operation<Void> original) {
        Storm.INSTANCE.setTintActive(true);
        try {
            original.call(state, poseStack, collector, light, overlay, outline);
        } finally {
            Storm.INSTANCE.setTintActive(false);
        }
    }
}
