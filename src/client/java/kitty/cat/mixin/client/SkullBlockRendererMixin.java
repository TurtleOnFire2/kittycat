package kitty.cat.mixin.client;

import kitty.cat.render.state.PlayerAlphaContext;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererMixin {
    @ModifyArgs(
            method = "submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V")
    )
    private static void kittycat$fadeWornSkull(Args args) {
        int alpha = PlayerAlphaContext.get();
        if (alpha == 255) return;
        int color = args.get(6);
        args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
    }
}
