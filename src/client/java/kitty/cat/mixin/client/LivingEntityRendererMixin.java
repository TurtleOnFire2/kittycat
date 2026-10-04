package kitty.cat.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import kitty.cat.render.state.PlayerAlphaContext;
import kitty.cat.render.state.PlayerAlphaRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @WrapMethod(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V")
    private void kittycat$withPlayerAlpha(LivingEntityRenderState state, PoseStack poseStack,
                                          SubmitNodeCollector collector, CameraRenderState camera,
                                          Operation<Void> original) {
        int previous = PlayerAlphaContext.get();
        int alpha = state instanceof AvatarRenderState
                ? ((PlayerAlphaRenderState) state).kittycat$getPlayerAlpha() : 255;
        PlayerAlphaContext.set(alpha);
        try {
            original.call(state, poseStack, collector, camera);
        } finally {
            PlayerAlphaContext.set(previous);
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void kittycat$translucentPlayer(LivingEntityRenderState state, boolean bodyVisible,
                                            boolean translucent, boolean glowing,
                                            CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof AvatarRenderState
                && ((PlayerAlphaRenderState) state).kittycat$getPlayerAlpha() < 255) {
            LivingEntityRenderer renderer = (LivingEntityRenderer) (Object) this;
            cir.setReturnValue(RenderTypes.entityTranslucent(renderer.getTextureLocation(state)));
        }
    }

    @ModifyReturnValue(method = "getModelTint", at = @At("RETURN"))
    private int kittycat$fadePlayer(int original, LivingEntityRenderState state) {
        if (!(state instanceof AvatarRenderState)) return original;
        int alpha = ((PlayerAlphaRenderState) state).kittycat$getPlayerAlpha();
        return (original & 0x00FFFFFF) | ((original >>> 24) * alpha / 255 << 24);
    }

}
