package kitty.cat.mixin.client.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack")
public interface GuiScissorStackAccessor {
    @Invoker("peek")
    ScreenRectangle kittycat$peek();
}
