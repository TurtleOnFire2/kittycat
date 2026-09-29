package kitty.cat.mixin.client;

import kitty.cat.features.kuudra.Drone;
import kitty.cat.features.kuudra.EtherwarpWaypoints;
import kitty.cat.features.kuudra.Stun;
import kitty.cat.features.kuudra.PearlWaypoints;
import kitty.cat.features.misc.KeyWaypoints;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onWaypointClick(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS
                && KeyWaypoints.INSTANCE.handleEditRightClick()) ci.cancel();
    }

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    void onTurnPlayer(double frameTime, CallbackInfo ci) {
        double[] adjusted = Stun.INSTANCE.onTurn(this.accumulatedDX, this.accumulatedDY);
        if (adjusted != null) {
            this.accumulatedDX = adjusted[0];
            this.accumulatedDY = adjusted[1];
        }

        adjusted = PearlWaypoints.INSTANCE.onTurn(this.accumulatedDX, this.accumulatedDY);
        if (adjusted != null) {
            this.accumulatedDX = adjusted[0];
            this.accumulatedDY = adjusted[1];
        }

        adjusted = EtherwarpWaypoints.INSTANCE.onTurn(this.accumulatedDX, this.accumulatedDY);
        if (adjusted != null) {
            this.accumulatedDX = adjusted[0];
            this.accumulatedDY = adjusted[1];
        }
    }
}
