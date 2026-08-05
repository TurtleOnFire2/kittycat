package kitty.cat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import kitty.cat.features.kuudra.TinyMobs;
import kitty.cat.utils.KuudraUtils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", shift = At.Shift.AFTER))
    void scale(EntityRenderState renderState, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (
                KuudraUtils.INSTANCE.kuudra() &&
                        renderState.entityType == BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie")) &&
                        TinyMobs.INSTANCE.getEnabled()
        ) {
            float scale = (float) TinyMobs.INSTANCE.getScale().getValue();
            poseStack.scale(scale, scale, scale);
        }
    }
}
