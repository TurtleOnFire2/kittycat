package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.Deathbow;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class LayerRenderStateMixin {
    @ModifyArg(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
            ),
            index = 5
    )
    private int[] kittycat$tintHeldItem(int[] tints) {
        if (!Deathbow.tintBow()) return tints;
        int n = Math.max(tints.length, 8); // cover items with no explicit tint layers
        int[] out = new int[n];
        for (int idx = 0; idx < n; idx++) {
            int argb = idx < tints.length ? tints[idx] : -1; // -1 = white, full multiplier
            out[idx] = Deathbow.tintArgb(argb);
        }
        return out;
    }
}