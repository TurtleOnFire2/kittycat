package kitty.cat.mixin.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {

    @Accessor("guiRenderState")
    GuiRenderState getGuiRenderState();

    @Accessor("scissorStack")
    GuiGraphicsExtractor.ScissorStack getScissorStack();
}
