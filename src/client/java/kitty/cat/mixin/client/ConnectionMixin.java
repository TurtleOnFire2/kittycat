package kitty.cat.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import kitty.cat.features.dungeons.Relics;
import kitty.cat.features.dungeons.Storm;
import kitty.cat.features.huds.SupplyHud;
import kitty.cat.features.kuudra.*;
import kitty.cat.features.misc.ChatMacros;
import kitty.cat.features.misc.FarmHelper;
import kitty.cat.utils.KuudraUtils;
import kitty.cat.utils.LocationUtils;
import kitty.cat.utils.Schedule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Unique
    private void handlePacket(Packet<?> packet) {
        if (packet instanceof ClientboundPingPacket common) {
            if (common.getId() == 0) return;
            Storm.INSTANCE.serverTick();
            Schedule.INSTANCE.tickServer();
            PearlWaypoints.INSTANCE.serverTick();
            SupplyHud.INSTANCE.serverTick();
            BackboneAlert.INSTANCE.serverTick();
            Build.INSTANCE.serverTick();
            Alerts.INSTANCE.serverTick();
        }

        if (packet instanceof ClientboundSystemChatPacket systemChat) {
            var message = systemChat.content().getString();
            var unformatted = ChatFormatting.stripFormatting(message);

            Minecraft.getInstance().execute(() -> {
                ChatMacros.INSTANCE.handleChat(unformatted);
                Storm.INSTANCE.handleChat(unformatted);
                Relics.INSTANCE.handleChat(unformatted);
                LocationUtils.INSTANCE.handleChat(unformatted);
                KuudraUtils.INSTANCE.handleChat(unformatted);
                Build.INSTANCE.handleChat(unformatted);
                AutoGFS.INSTANCE.handleChat(unformatted);
                Stun.INSTANCE.handleChat(unformatted);
                FarmHelper.INSTANCE.handleChat(unformatted);
                CratePriority.INSTANCE.handleChat(unformatted);
                Supplies.INSTANCE.handleChat(unformatted);
                EtherwarpWaypoints.INSTANCE.handleChat(unformatted);
                AutoWarp.INSTANCE.handleChat(unformatted);
                Alerts.INSTANCE.handleChat(unformatted);
            });
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        handlePacket(packet);
    }
}
