package kitty.cat.mixin.client;

import kitty.cat.render.state.CatTailRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements CatTailRenderState {
    @Unique
    private float kittycat$tailVerticalVelocity;

    @Override
    public float getKittycatTailVerticalVelocity() {
        return kittycat$tailVerticalVelocity;
    }

    @Override
    public void setKittycatTailVerticalVelocity(float verticalVelocity) {
        kittycat$tailVerticalVelocity = verticalVelocity;
    }
}
