package dev.cannonlines.mixin;

import dev.cannonlines.CannonLinesClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.11 builds entity render states before drawing them.
 * Setting outlineColor on that state is what adds the entity to the outline pass.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityGlowMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void cannonlines$markPlayerOutline(Entity entity, EntityRenderState state,
                                               float tickProgress, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player
            && player.getUuid().equals(CannonLinesClient.getMarkedPlayerId())) {
            state.outlineColor = 0xFFFF3333;
        }
    }
}
