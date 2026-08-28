package kitty.cat.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import kitty.cat.utils.SmoothGradientRenderer;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public abstract class BakedSheetGlyphInstanceMixin {
    @Shadow
    public abstract int color();

    @Shadow
    public abstract int shadowColor();

    @Shadow
    public abstract Style style();

    @Inject(method = "render", at = @At("HEAD"))
    private void kittycat$beginSmoothGradient(
            Matrix4fc matrix,
            VertexConsumer consumer,
            int light,
            boolean inverseDepth,
            CallbackInfo ci
    ) {
        SmoothGradientRenderer.beginGlyph(style(), color(), shadowColor());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kittycat$endSmoothGradient(
            Matrix4fc matrix,
            VertexConsumer consumer,
            int light,
            boolean inverseDepth,
            CallbackInfo ci
    ) {
        SmoothGradientRenderer.endGlyph();
    }
}
