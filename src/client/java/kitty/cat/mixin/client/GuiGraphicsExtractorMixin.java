package kitty.cat.mixin.client;

import kitty.cat.utils.NameChanger;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiTextRenderState.class)
public abstract class GuiGraphicsExtractorMixin {

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private static FormattedCharSequence kittycat$replaceName(
            FormattedCharSequence str
    ) {
        return NameChanger.INSTANCE.replace(str);
    }
}
