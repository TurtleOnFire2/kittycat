package kitty.cat.mixin.client.gui;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Skija requires the sampled-texture usage bit when wrapping a Vulkan image.
 * Vanilla PIP color targets use 13; regular render targets use 15.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
    @ModifyConstant(
            method = "prepareTexturesAndProjection",
            constant = @Constant(intValue = 13)
    )
    private int kittycat$makeColorTargetSkijaCompatible(int usage) {
        return usage | 2;
    }
}
