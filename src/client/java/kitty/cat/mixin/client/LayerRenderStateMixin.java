package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.Storm;
import kitty.cat.render.state.PlayerAlphaContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class LayerRenderStateMixin {
    @ModifyArg(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
            ),
            index = 5,
            require = 1
    )
    private int[] kittycat$tintHeldItemNamed(int[] tints) {
        return kittycat$tintHeldItem(tints);
    }

    private int[] kittycat$tintHeldItem(int[] tints) {
        int alpha = PlayerAlphaContext.get();
        boolean tintBow = Storm.tintBow();
        if (!tintBow && alpha == 255) return tints;
        int n = tintBow ? Math.max(tints.length, 8) : tints.length;
        int[] out = new int[n];
        for (int idx = 0; idx < n; idx++) {
            int argb = idx < tints.length ? tints[idx] : -1;
            if (tintBow) argb = Storm.tintArgb(argb);
            out[idx] = (argb & 0x00FFFFFF) | ((argb >>> 24) * alpha / 255 << 24);
        }
        return out;
    }
}
