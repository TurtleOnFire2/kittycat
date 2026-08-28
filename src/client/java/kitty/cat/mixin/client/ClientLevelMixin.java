package kitty.cat.mixin.client;

import kitty.cat.features.misc.FarmHelper;
import kitty.cat.features.misc.Pests;
import kitty.cat.utils.BoneUtils;
import kitty.cat.utils.KuudraUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void onAddEntity(Entity entity, CallbackInfo ci) {
        BoneUtils.INSTANCE.addEntity(entity);
        KuudraUtils.INSTANCE.addEntity(entity);
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onRemoveEntity(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        FarmHelper.INSTANCE.handleEntityRemoved(id);
    }
}
