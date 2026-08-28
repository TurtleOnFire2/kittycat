package kitty.cat.mixin.client;

import kitty.cat.utils.SmoothGradientRenderer;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedSheetGlyph.class)
public abstract class BakedSheetGlyphMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void kittycat$beginSmoothGradientQuad(CallbackInfo ci) {
        SmoothGradientRenderer.beginQuad();
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            ),
            index = 0
    )
    private int kittycat$applySmoothGradient(int color) {
        return SmoothGradientRenderer.vertexColor(color);
    }
}
