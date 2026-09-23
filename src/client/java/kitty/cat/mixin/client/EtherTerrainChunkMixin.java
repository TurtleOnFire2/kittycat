package kitty.cat.mixin.client;

import kitty.cat.features.misc.EtherPath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class EtherTerrainChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void invalidateBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (cir.getReturnValue() != null && chunk.getLevel() instanceof ClientLevel level) {
            EtherPath.INSTANCE.invalidateTerrain(level, pos);
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
    private void invalidatePacket(CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ClientLevel level) {
            EtherPath.INSTANCE.invalidateChunk(level, chunk.getPos().x(), chunk.getPos().z());
        }
    }
}
