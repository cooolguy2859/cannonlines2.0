package dev.cannonlines;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class CannonLinesClient implements ClientModInitializer {
    private boolean enabled = true;
    private boolean hpDisplayEnabled = true;
    private boolean placementGuideEnabled = true;
    private boolean enemyHudEnabled = true;
    private int reachBlocks = 5;
    private final Set<String> enemyNames = new HashSet<>();
    private Path enemyFile;
    private int tick = 0;

    private static final double TRAIL_LENGTH = 64.0;
    private static final double PARTICLE_SPACING = 0.35;
    private static final int SCAN_EVERY_TICKS = 2;
    private static final int TRAIL_PARTICLE_PASSES = 4;
    private static final double MAX_SCAN_DISTANCE = 48.0;

    private static final int PING_LIFETIME_TICKS = 200;
    private static final double PING_RAYCAST_DISTANCE = 128.0;
    private KeyBinding pingKey;
    private Vec3d pingPosition;
    private int pingTicksRemaining;

    private KeyBinding markEnemyKey;
    private final PartyClient partyClient = new PartyClient();
    private final AutoReconnectManager autoReconnect = new AutoReconnectManager();
    private static volatile UUID markedPlayerId;
    private boolean markedPlayerWasGlowing;

    @Override
    public void onInitializeClient() {
        enemyFile = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cannonlines-enemies.txt");
        loadEnemyNames();
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cannonlines.distance_ping", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_1, KeyBinding.Category.MISC
        ));
        markEnemyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cannonlines.mark_enemy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("cannonlines").executes(context -> {
                enabled = !enabled;
                context.getSource().sendFeedback(Text.literal("CannonLines: " + (enabled ? "ON" : "OFF")));
                return Command.SINGLE_SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("enemy")
                .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> { String name = StringArgumentType.getString(ctx, "player");
                        if (enemyNames.add(name.toLowerCase(java.util.Locale.ROOT))) { saveEnemyNames(); ctx.getSource().sendFeedback(Text.literal("Added " + name + " to your enemy list.")); }
                        else ctx.getSource().sendFeedback(Text.literal(name + " is already on your enemy list."));
                        return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> { String name = StringArgumentType.getString(ctx, "player");
                        if (enemyNames.remove(name.toLowerCase(java.util.Locale.ROOT))) { saveEnemyNames(); ctx.getSource().sendFeedback(Text.literal("Removed " + name + " from your enemy list.")); }
                        else ctx.getSource().sendFeedback(Text.literal(name + " wasn't on your enemy list."));
                        return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(enemyNames.isEmpty() ? "Your enemy list is empty. Use /enemy add <player>." : "Enemies: " + String.join(", ", enemyNames)));
                    return Command.SINGLE_SUCCESS; })));
            dispatcher.register(ClientCommandManager.literal("enemyhud")
                .then(ClientCommandManager.literal("on").executes(ctx -> { enemyHudEnabled = true; ctx.getSource().sendFeedback(Text.literal("Enemy list HUD: ON")); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommandManager.literal("off").executes(ctx -> { enemyHudEnabled = false; ctx.getSource().sendFeedback(Text.literal("Enemy list HUD: OFF")); return Command.SINGLE_SUCCESS; })));
            dispatcher.register(ClientCommandManager.literal("nether")
                .then(ClientCommandManager.argument("x", StringArgumentType.word()).then(ClientCommandManager.argument("z", StringArgumentType.word())
                    .executes(ctx -> { int x = parseCoordinate(StringArgumentType.getString(ctx,"x")); int z = parseCoordinate(StringArgumentType.getString(ctx,"z"));
                        ctx.getSource().sendFeedback(Text.literal("Nether coordinates: X " + Math.floorDiv(x, 8) + ", Z " + Math.floorDiv(z, 8)));
                        return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("overworld").then(ClientCommandManager.argument("x", StringArgumentType.word()).then(ClientCommandManager.argument("z", StringArgumentType.word())
                    .executes(ctx -> { int x = parseCoordinate(StringArgumentType.getString(ctx,"x")); int z = parseCoordinate(StringArgumentType.getString(ctx,"z"));
                        ctx.getSource().sendFeedback(Text.literal("Overworld coordinates: X " + (x * 8) + ", Z " + (z * 8)));
                        return Command.SINGLE_SUCCESS; })))));
            dispatcher.register(ClientCommandManager.literal("canon").then(ClientCommandManager.literal("dev").executes(ctx -> {
                ctx.getSource().sendFeedback(Text.literal("Cannon Dev is active. Minecraft 1.21.11 | Development build."));
                return Command.SINGLE_SUCCESS; })));
            dispatcher.register(ClientCommandManager.literal("cannon")
                .then(ClientCommandManager.literal("gui").executes(ctx -> { ctx.getSource().sendFeedback(Text.literal("Use Right Shift to open CannonLines controls.")); return Command.SINGLE_SUCCESS; }))
                .then(ClientCommandManager.literal("reach").then(ClientCommandManager.argument("blocks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,500))
                    .executes(ctx -> { reachBlocks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"blocks");
                        ctx.getSource().sendFeedback(Text.literal("Particle line length set to " + reachBlocks + " blocks")); return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("turnoff").then(ClientCommandManager.literal("hp").executes(ctx -> { hpDisplayEnabled = false; ctx.getSource().sendFeedback(Text.literal("Block HP display: OFF")); return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("turnon").then(ClientCommandManager.literal("hp").executes(ctx -> { hpDisplayEnabled = true; ctx.getSource().sendFeedback(Text.literal("Block HP display: ON")); return Command.SINGLE_SUCCESS; })))
                .then(ClientCommandManager.literal("guide").then(ClientCommandManager.literal("on").executes(ctx -> { placementGuideEnabled = true; ctx.getSource().sendFeedback(Text.literal("Cannon placement guide: ON")); return Command.SINGLE_SUCCESS; }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> { placementGuideEnabled = false; ctx.getSource().sendFeedback(Text.literal("Cannon placement guide: OFF")); return Command.SINGLE_SUCCESS; }))));

            dispatcher.register(ClientCommandManager.literal("afk")
                .executes(context -> {
                    autoReconnect.toggle(MinecraftClient.getInstance());
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.literal("on").executes(context -> {
                    if (!autoReconnect.isEnabled()) autoReconnect.toggle(MinecraftClient.getInstance());
                    context.getSource().sendFeedback(Text.literal("Auto-Reconnect: " + autoReconnect.getStatus()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    autoReconnect.disable();
                    context.getSource().sendFeedback(Text.literal("Auto-Reconnect: OFF"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Auto-Reconnect: " + autoReconnect.getStatus()
                        + " | Server: " + autoReconnect.getServerName()
                        + " | Attempts: " + autoReconnect.getAttempts()));
                    return Command.SINGLE_SUCCESS;
                })));
            dispatcher.register(ClientCommandManager.literal("cannonparty")
                .then(ClientCommandManager.literal("join")
                    .then(ClientCommandManager.argument("code", StringArgumentType.word())
                        .executes(context -> {
                            partyClient.join(StringArgumentType.getString(context, "code"), MinecraftClient.getInstance());
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(ClientCommandManager.literal("leave").executes(context -> {
                    partyClient.leave(MinecraftClient.getInstance());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommandManager.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Text.literal("CannonLines party: " + partyClient.status()));
                    return Command.SINGLE_SUCCESS;
                })));
        });
    }


    private void loadEnemyNames() {
        try {
            if (Files.exists(enemyFile)) {
                for (String line : Files.readAllLines(enemyFile)) {
                    String name = line.trim();
                    if (!name.isEmpty()) enemyNames.add(name.toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (IOException e) { System.err.println("[CannonLines] Could not load enemy list: " + e.getMessage()); }
    }

    private void saveEnemyNames() {
        try {
            Files.createDirectories(enemyFile.getParent());
            Files.write(enemyFile, enemyNames);
        } catch (IOException e) { System.err.println("[CannonLines] Could not save enemy list: " + e.getMessage()); }
    }

    private static int parseCoordinate(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.endsWith("k")) return (int)Math.round(Double.parseDouble(v.substring(0,v.length()-1)) * 1000.0);
        return Integer.parseInt(v);
    }

    private void onTick(MinecraftClient client) {
        autoReconnect.tick(client);
        if (client.world == null || client.player == null) {
            markedPlayerId = null;
            pingPosition = null;
            pingTicksRemaining = 0;
            return;
        }

        partyClient.tick(client);
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
                client.particleManager.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            }
        }
    }

    public static UUID getMarkedPlayerId() { return markedPlayerId; }

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
    }

    private void clearMarkedPlayer(MinecraftClient client) {
        if (markedPlayerId == null) return;
        PlayerEntity previous = client.world == null ? null : client.world.getPlayerByUuid(markedPlayerId);
        // The ESP mixin only overrides the local render check; it never changes server entity state.
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
        partyClient.sendPing(client, pingPosition);
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
            client.particleManager.addParticle(ParticleTypes.HAPPY_VILLAGER, pingPosition.x, pingPosition.y + 0.15, pingPosition.z, 0.0, 0.025, 0.0);
            client.particleManager.addParticle(ParticleTypes.END_ROD, pingPosition.x, pingPosition.y + 0.45, pingPosition.z, 0.0, 0.015, 0.0);
        }

        if (pingTicksRemaining == 0) {
            client.player.sendMessage(Text.literal("Distance Ping expired."), true);
        } else if (client.world.getTime() % 10 == 0) {
            double distance = Math.sqrt(client.player.squaredDistanceTo(pingPosition));
            client.player.sendMessage(Text.literal("Ping • " + Math.round(distance) + " blocks"), true);
        }
    }
}
