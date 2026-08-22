package kitty.cat.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kitty.cat.features.kuudra.Supplies;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class PlayerMixin {
    @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
    private double modifyEntityInteractionRange(double original) {
        return Supplies.INSTANCE.changeReach(original);
    }
}
