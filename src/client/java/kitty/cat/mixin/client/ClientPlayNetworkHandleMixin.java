package kitty.cat.mixin.client;

import kitty.cat.features.dungeons.AutoLB;
import kitty.cat.features.dungeons.Relics;
import kitty.cat.features.dungeons.Storm;
import kitty.cat.features.huds.BestiaryHud;
import kitty.cat.features.misc.ChatMacros;
import kitty.cat.features.misc.Pests;
import kitty.cat.features.visual.ArrowTracers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
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

    @Inject(method = "handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V", at = @At("HEAD"))
    void handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().packetProcessor().isSameThread()) return;

        var component = packet.content();
        var message = component.getString();
        var unformatted = ChatFormatting.stripFormatting(message);

        Pests.INSTANCE.handleChat(unformatted);
        AutoLB.INSTANCE.handleChat(unformatted);
        ChatMacros.INSTANCE.handleChat(unformatted);
        Storm.INSTANCE.handleChat(unformatted);
        Relics.INSTANCE.handleChat(unformatted);
    }

    @Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("HEAD"), cancellable = true)
    void handleOpenScreen(ClientboundOpenScreenPacket clientboundOpenScreenPacket, CallbackInfo ci) {
        Storm.INSTANCE.handleScreen(clientboundOpenScreenPacket);
    }
}
