package kitty.cat.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import kitty.cat.render.state.PlayerAlphaContext;
import kitty.cat.render.world.RenderLayers;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
    @ModifyArgs(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 0)
    )
    private void kittycat$fadeArmor(Args args, @Local(ordinal = 1) Identifier texture) {
        int alpha = PlayerAlphaContext.get();
        if (alpha == 255) return;
        int color = args.get(6);
        args.set(3, RenderLayers.armorTranslucent(texture, args.get(3) == RenderTypes.armorCutoutNoCullGlint(texture)));
        args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
    }

    @ModifyArgs(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 1)
    )
    private void kittycat$fadeArmorGlint(Args args) {
        int alpha = PlayerAlphaContext.get();
        if (alpha == 255) return;
        int color = args.get(6);
        args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
    }

    @ModifyArgs(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 2)
    )
    private void kittycat$fadeArmorTrim(Args args) {
        int alpha = PlayerAlphaContext.get();
        if (alpha == 255) return;
        int color = args.get(6);
        args.set(6, (color & 0x00FFFFFF) | ((color >>> 24) * alpha / 255 << 24));
    }
}
