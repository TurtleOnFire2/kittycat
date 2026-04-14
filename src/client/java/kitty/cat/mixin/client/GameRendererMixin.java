package kitty.cat.mixin.client;

import imgui.ImGui;
import kitty.cat.gui.ImGuiHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void render(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof final ImGuiHandler.RenderInterface renderInterface) {
            ImGuiHandler.INSTANCE.start();
            renderInterface.render(ImGui.getIO());
            ImGuiHandler.INSTANCE.end();
        }
    }
}
