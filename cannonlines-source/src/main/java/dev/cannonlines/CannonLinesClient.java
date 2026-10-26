package dev.cannonlines;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class CannonLinesClient implements ClientModInitializer {
    private boolean enabled = true;
    private int tick = 0;

    // Short, close-spaced particles look like a continuous glowing trail.
    private static final double TRAIL_LENGTH = 64.0;
    private static final double PARTICLE_SPACING = 0.22;
    private static final int SCAN_EVERY_TICKS = 2;
    private static final double MAX_SCAN_DISTANCE = 48.0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("cannonlines").executes(context -> {
                enabled = !enabled;
                context.getSource().sendFeedback(Text.literal("CannonLines: " + (enabled ? "ON" : "OFF")));
                return Command.SINGLE_SUCCESS;
            }))
        );
    }

    private void onTick(MinecraftClient client) {
        if (!enabled || client.world == null || client.player == null) return;
        if (++tick % SCAN_EVERY_TICKS != 0) return;

        var world = client.world;
        var player = client.player;
        Box searchBox = player.getBoundingBox().expand(MAX_SCAN_DISTANCE, 24.0, MAX_SCAN_DISTANCE);

        for (Entity entity : world.getOtherEntities(player, searchBox, e -> e instanceof SlimeEntity)) {
            SlimeEntity slime = (SlimeEntity) entity;
            if (!slime.isAlive() || player.squaredDistanceTo(slime) > MAX_SCAN_DISTANCE * MAX_SCAN_DISTANCE) continue;

            // Entity rotation is the cannon's forward direction. Start just behind its rear face.
            Vec3d forward = slime.getRotationVec(1.0F).normalize();
            if (forward.lengthSquared() < 0.0001) continue;

            double halfWidth = Math.max(0.45, slime.getWidth() * 0.5);
            Vec3d center = new Vec3d(slime.getX(), slime.getY() + slime.getHeight() * 0.55, slime.getZ());
            Vec3d rear = center.subtract(forward.multiply(halfWidth + 0.08));

            // Spawn a line of End Rod particles extending backward from the cannon.
            // Each particle is spawned client-side, so this is only a visual effect.
            for (double distance = 0.0; distance <= TRAIL_LENGTH; distance += PARTICLE_SPACING) {
                Vec3d pos = rear.subtract(forward.multiply(distance));
                world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            }
        }
    }
}
