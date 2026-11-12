package dev.cannonlines;

import com.mojang.brigadier.Command;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class CannonLinesClient implements ClientModInitializer {
    private boolean enabled = true;
    private int tick = 0;

    // Stagger trail particles over four passes to reduce particle work substantially.
    private static final double TRAIL_LENGTH = 64.0;
    private static final double PARTICLE_SPACING = 0.35;
    private static final int SCAN_EVERY_TICKS = 2;
    private static final int TRAIL_PARTICLE_PASSES = 4;
    private static final double MAX_SCAN_DISTANCE = 48.0;

    // Distance Ping: configurable key, default 1.
    private static final int PING_LIFETIME_TICKS = 200;
    private static final double PING_RAYCAST_DISTANCE = 128.0;
    private KeyBinding pingKey;
    private Vec3d pingPosition;
    private int pingTicksRemaining;

    // Client-only player marker. The server is never told to apply the glowing status effect.
    private KeyBinding markEnemyKey;
    private UUID markedPlayerId;
    private boolean markedPlayerWasGlowing;

    @Override
    public void onInitializeClient() {
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cannonlines.distance_ping",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_1,
            KeyBinding.Category.MISC
        ));
        markEnemyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cannonlines.mark_enemy",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyBinding.Category.MISC
        ));

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
        if (client.world == null || client.player == null) {
            markedPlayerId = null;
            pingPosition = null;
            pingTicksRemaining = 0;
            return;
        }

        while (pingKey.wasPressed()) createDistancePing(client);
        while (markEnemyKey.wasPressed()) toggleMarkedPlayer(client);
        updateMarkedPlayer(client);
        updateDistancePing(client);

        if (!enabled || ++tick % SCAN_EVERY_TICKS != 0) return;

        var world = client.world;
        var player = client.player;
        Box searchBox = player.getBoundingBox().expand(MAX_SCAN_DISTANCE, 24.0, MAX_SCAN_DISTANCE);

        for (Entity entity : world.getOtherEntities(player, searchBox, e -> e instanceof SlimeEntity)) {
            SlimeEntity slime = (SlimeEntity) entity;
            if (!slime.isAlive() || player.squaredDistanceTo(slime) > MAX_SCAN_DISTANCE * MAX_SCAN_DISTANCE) continue;

            Vec3d forward = slime.getRotationVec(1.0F).normalize();
            if (forward.lengthSquared() < 0.0001) continue;

            double halfWidth = Math.max(0.45, slime.getWidth() * 0.5);
            Vec3d center = new Vec3d(slime.getX(), slime.getY() + slime.getHeight() * 0.55, slime.getZ());
            Vec3d rear = center.subtract(forward.multiply(halfWidth + 0.08));

            int sampleIndex = 0;
            for (double distance = 0.0; distance <= TRAIL_LENGTH; distance += PARTICLE_SPACING) {
                if ((sampleIndex++ + tick / SCAN_EVERY_TICKS) % TRAIL_PARTICLE_PASSES != 0) continue;
                Vec3d pos = rear.subtract(forward.multiply(distance));
                world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            }
        }
    }

    private void toggleMarkedPlayer(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult entityHit
            && entityHit.getEntity() instanceof PlayerEntity target
            && target != client.player) {
            if (target.getUuid().equals(markedPlayerId)) {
                clearMarkedPlayer(client);
                client.player.sendMessage(Text.literal("Enemy highlight removed."), true);
            } else {
                clearMarkedPlayer(client);
                markedPlayerId = target.getUuid();
                markedPlayerWasGlowing = target.isGlowing();
                target.setGlowing(true);
                client.player.sendMessage(Text.literal("Marked " + target.getName().getString()), true);
            }
        } else {
            client.player.sendMessage(Text.literal("Look directly at a player to mark them."), true);
        }
    }

    private void updateMarkedPlayer(MinecraftClient client) {
        if (markedPlayerId == null) return;
        PlayerEntity target = client.world.getPlayerByUuid(markedPlayerId);
        if (target == null || !target.isAlive()) {
            markedPlayerId = null;
            return;
        }
        // Reapply locally in case entity metadata refreshes. This does not send a status effect.
        target.setGlowing(true);
    }

    private void clearMarkedPlayer(MinecraftClient client) {
        if (markedPlayerId == null) return;
        PlayerEntity previous = client.world == null ? null : client.world.getPlayerByUuid(markedPlayerId);
        if (previous != null) previous.setGlowing(markedPlayerWasGlowing);
        markedPlayerId = null;
        markedPlayerWasGlowing = false;
    }

    private void createDistancePing(MinecraftClient client) {
        HitResult hit = client.player.raycast(PING_RAYCAST_DISTANCE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            client.player.sendMessage(Text.literal("Distance Ping: look at a block or location first."), true);
            return;
        }

        pingPosition = Vec3d.ofCenter(blockHit.getBlockPos());
        pingTicksRemaining = PING_LIFETIME_TICKS;
        double distance = Math.sqrt(client.player.squaredDistanceTo(pingPosition));
        client.player.sendMessage(Text.literal("Ping placed • " + Math.round(distance) + " blocks away"), true);
    }

    private void updateDistancePing(MinecraftClient client) {
        if (pingPosition == null || pingTicksRemaining <= 0) {
            pingPosition = null;
            pingTicksRemaining = 0;
            return;
        }

        pingTicksRemaining--;
        if (client.world.getTime() % 3 == 0) {
            client.world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                pingPosition.x, pingPosition.y + 0.15, pingPosition.z, 0.0, 0.025, 0.0);
            client.world.addParticle(ParticleTypes.END_ROD,
                pingPosition.x, pingPosition.y + 0.45, pingPosition.z, 0.0, 0.015, 0.0);
        }

        if (pingTicksRemaining == 0) {
            client.player.sendMessage(Text.literal("Distance Ping expired."), true);
        } else if (client.world.getTime() % 10 == 0) {
            double distance = Math.sqrt(client.player.squaredDistanceTo(pingPosition));
            client.player.sendMessage(Text.literal("Ping • " + Math.round(distance) + " blocks"), true);
        }
    }
}