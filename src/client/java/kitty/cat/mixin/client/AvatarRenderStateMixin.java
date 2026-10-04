package kitty.cat.mixin.client;

import kitty.cat.render.state.CatTailRenderState;
import kitty.cat.render.state.PlayerAlphaRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements CatTailRenderState, PlayerAlphaRenderState {
    @Unique
    private float kittycat$tailVerticalVelocity;

    @Unique
    private int kittycat$playerAlpha = 255;

    @Override
    public int kittycat$getPlayerAlpha() {
        return kittycat$playerAlpha;
    }

    @Override
    public void kittycat$setPlayerAlpha(int alpha) {
        kittycat$playerAlpha = alpha;
    }

    @Override
    public float getKittycatTailVerticalVelocity() {
        return kittycat$tailVerticalVelocity;
    }

    @Override
    public void setKittycatTailVerticalVelocity(float verticalVelocity) {
        kittycat$tailVerticalVelocity = verticalVelocity;
    }
}
