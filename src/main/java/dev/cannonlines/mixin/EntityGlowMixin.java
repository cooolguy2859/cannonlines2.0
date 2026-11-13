package dev.cannonlines.mixin;

import dev.cannonlines.CannonLinesClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces the locally selected enemy through Minecraft's vanilla outline pass. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void cannonlines$showMarkedPlayerEsp(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof PlayerEntity player
            && player.getUuid().equals(CannonLinesClient.getMarkedPlayerId())) {
            cir.setReturnValue(true);
        }
    }
}
