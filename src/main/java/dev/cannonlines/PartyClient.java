package dev.cannonlines;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

final class PartyClient {
    private static final String API = "https://cannonlines-party.firewolfjm10.workers.dev";
    private static final long PING_LIFETIME_MS = 20_000L;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, SharedPing> pings = new ConcurrentHashMap<>();
    private final Set<String> seenPings = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("cannonlines-party.txt");
    private volatile String partyCode;
    private int ticksSincePoll;
    private String lastServerKey;

    PartyClient() {
        try {
            if (Files.exists(configFile)) {
                String saved = Files.readString(configFile).trim();
                if (saved.matches("[A-Za-z0-9_-]{3,24}")) partyCode = saved;
            }
        } catch (IOException ignored) {
        }
    }

    void join(String code, MinecraftClient client) {
        String clean = code == null ? "" : code.trim();
        if (!clean.matches("[A-Za-z0-9_-]{3,24}")) {
            client.player.sendMessage(Text.literal("Party code must be 3-24 letters, numbers, _ or -."), false);
            return;
        }
        partyCode = clean;
        pings.clear();
        seenPings.clear();
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, clean);
        } catch (IOException ignored) {
        }
        post("/party/join", "{"code":" + quote(clean) + "}", client, "Joined CannonLines party " + clean + ".");
        ticksSincePoll = 20;
    }

    void leave(MinecraftClient client) {
        partyCode = null;
        pings.clear();
        seenPings.clear();
        try { Files.deleteIfExists(configFile); } catch (IOException ignored) {}
        if (client.player != null) client.player.sendMessage(Text.literal("Left the CannonLines party."), false);
    }

    String status() {
        return partyCode == null ? "not in a party" : "connected to " + partyCode;
    }

    void sendPing(MinecraftClient client, Vec3d position) {
        if (partyCode == null || client.player == null || client.world == null) return;
        String body = "{"player":" + quote(client.player.getName().getString())
            + ","dimension":" + quote(client.world.getRegistryKey().getValue().toString())
            + ","x":" + position.x + ","y":" + position.y + ","z":" + position.z + "}";
        post("/party/ping", body, client, null);
    }

    void tick(MinecraftClient client) {
        if (partyCode == null || client.player == null || client.world == null) return;
        String currentServer = serverKey(client);
        if (!currentServer.equals(lastServerKey)) {
            lastServerKey = currentServer;
            pings.clear();
            seenPings.clear();
        }
        if (++ticksSincePoll >= 20) {
            ticksSincePoll = 0;
            poll(client);
        }
        long now = System.currentTimeMillis();
        String dimension = client.world.getRegistryKey().getValue().toString();
        for (SharedPing ping : pings.values()) {
            if (now - ping.createdAt > PING_LIFETIME_MS) {
                pings.remove(ping.id);
                continue;
            }
            if (!ping.dimension.equals(dimension) || ping.player.equalsIgnoreCase(client.player.getName().getString())) continue;
            if (client.world.getTime() % 3 == 0) {
                client.world.addParticle(ParticleTypes.HAPPY_VILLAGER, false, true,
                    ping.x, ping.y + 0.15, ping.z, 0.0, 0.025, 0.0);
                client.world.addParticle(ParticleTypes.END_ROD, false, true,
                    ping.x, ping.y + 0.45, ping.z, 0.0, 0.015, 0.0);
            }
        }
    }

    private void poll(MinecraftClient client) {
        if (!polling.compareAndSet(false, true)) return;
        String ownName = client.player.getName().getString();
        String url = endpoint("/party/state");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            polling.set(false);
            if (error != null || response == null || response.statusCode() != 200) return;
            try {
                JsonArray array = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("pings");
                long now = System.currentTimeMillis();
                for (JsonElement element : array) {
                    JsonObject obj = element.getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    SharedPing ping = new SharedPing(id,
                        obj.get("player").getAsString(),
                        obj.get("dimension").getAsString(),
                        obj.get("x").getAsDouble(), obj.get("y").getAsDouble(), obj.get("z").getAsDouble(),
                        obj.get("createdAt").getAsLong());
                    if (now - ping.createdAt <= PING_LIFETIME_MS) {
                        pings.put(id, ping);
                        if (seenPings.add(id) && !ping.player.equalsIgnoreCase(ownName)) {
                            client.execute(() -> {
                                if (client.player != null) client.player.sendMessage(
                                    Text.literal(ping.player + " placed a party ping."), true);
                            });
                        }
                    }
                }
                if (seenPings.size() > 256) seenPings.clear();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private void post(String path, String body, MinecraftClient client, String successMessage) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(path)))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (successMessage != null) {
                client.execute(() -> {
                    if (client.player == null) return;
                    if (error == null && response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                        client.player.sendMessage(Text.literal(successMessage), false);
                    } else {
                        client.player.sendMessage(Text.literal("Party connection failed. Check your internet and try again."), false);
                    }
                });
            }
        });
    }

    private String endpoint(String path) {
        MinecraftClient client = MinecraftClient.getInstance();
        return API + path + "?code=" + encode(partyCode) + "&server=" + encode(serverKey(client));
    }

    private String serverKey(MinecraftClient client) {
        return client.getCurrentServerEntry() == null
            ? "singleplayer"
            : client.getCurrentServerEntry().address.toLowerCase();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        return """ + value.replace("\", "\\").replace(""", "\"") + """;
    }

    private record SharedPing(String id, String player, String dimension,
                              double x, double y, double z, long createdAt) {}
}
