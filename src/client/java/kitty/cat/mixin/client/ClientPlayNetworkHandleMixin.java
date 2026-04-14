package kitty.cat.mixin.client;

import kitty.cat.features.huds.BestiaryHud;
import kitty.cat.features.visual.ArrowTracers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandleMixin {
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    void handleAddEntity(ClientboundAddEntityPacket clientboundAddEntityPacket, CallbackInfo ci) {
        ArrowTracers.INSTANCE.handleAddEntity(clientboundAddEntityPacket);
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    void handleRemoveEntities(ClientboundRemoveEntitiesPacket clientboundRemoveEntitiesPacket, CallbackInfo ci) {
        ArrowTracers.INSTANCE.handleRemoveEntities(clientboundRemoveEntitiesPacket);
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    void handleInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        BestiaryHud.INSTANCE.handleTabChange(packet);
    }
}
