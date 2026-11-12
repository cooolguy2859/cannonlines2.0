package dev.cannonlines;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Reconnects to the last multiplayer server after an unexpected disconnect.
 * It does not send movement or other input to avoid server AFK checks.
 */
public final class AutoReconnectManager {
    private static final int INITIAL_RETRY_TICKS = 100;
    private static final int MAX_RETRY_TICKS = 600;
    private static final int MAX_ATTEMPTS = 10;
    private static final int JOIN_COMMAND_DELAY_TICKS = 40;

    private boolean enabled;
    private ServerInfo lastServer;
    private boolean connectionWasLost;
    private boolean connectInProgress;
    private int connectAttemptTicks;
    private int retryTicks;
    private int attempts;
    private int joinCommandDelay = -1;
    private String status = "Disabled";

    public boolean isEnabled() {
        return enabled;
    }

    public String getStatus() {
        return status;
    }

    public String getServerName() {
        return lastServer == null ? "No server saved" : lastServer.name;
    }

    public int getAttempts() {
        return attempts;
    }

    public void toggle(MinecraftClient client) {
        if (enabled) {
            disable();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("CannonLines Auto-Reconnect: OFF"), true);
            }
            return;
        }

        ServerInfo current = client.getCurrentServerEntry();
        if (current != null) {
            lastServer = current;
        }
        if (lastServer == null) {
            status = "Join a multiplayer server first";
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Auto-Reconnect: join a server first so CannonLines can save it."), false);
            }
            return;
        }

        enabled = true;
        attempts = 0;
        retryTicks = 0;
        connectionWasLost = false;
        connectInProgress = false;
        joinCommandDelay = -1;
        status = "Enabled";
        if (client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("joinlifesteal");
            client.player.sendMessage(Text.literal("Auto-Reconnect: ON • sent /joinlifesteal"), true);
        }
    }

    public void disable() {
        enabled = false;
        connectionWasLost = false;
        connectInProgress = false;
        connectAttemptTicks = 0;
        retryTicks = 0;
        joinCommandDelay = -1;
        status = "Disabled";
    }

    public void tick(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            ServerInfo current = client.getCurrentServerEntry();
            if (current != null) {
                lastServer = current;
            }

            if (connectionWasLost) {
                connectionWasLost = false;
                connectInProgress = false;
                attempts = 0;
                joinCommandDelay = JOIN_COMMAND_DELAY_TICKS;
                status = "Reconnected • joining Lifesteal...";
            }

            if (joinCommandDelay >= 0) {
                if (joinCommandDelay-- == 0 && enabled && client.player.networkHandler != null) {
                    client.player.networkHandler.sendChatCommand("joinlifesteal");
                    status = "Connected • sent /joinlifesteal";
                    client.player.sendMessage(Text.literal("CannonLines: sent /joinlifesteal after reconnecting."), true);
                }
            }
            return;
        }

        if (!enabled || lastServer == null || !(client.currentScreen instanceof DisconnectedScreen)) {
            return;
        }

        if (!connectionWasLost) {
            connectionWasLost = true;
            retryTicks = INITIAL_RETRY_TICKS;
            status = "Disconnected • retrying soon";
        }

        if (connectInProgress) {
            connectAttemptTicks++;
            if (connectAttemptTicks < 40) {
                return;
            }
            connectInProgress = false;
            connectAttemptTicks = 0;
            retryTicks = Math.min(INITIAL_RETRY_TICKS * Math.max(1, attempts), MAX_RETRY_TICKS);
            status = "Reconnect failed • waiting to retry";
        }

        if (retryTicks > 0) {
            retryTicks--;
            return;
        }

        if (attempts >= MAX_ATTEMPTS) {
            disable();
            status = "Stopped after " + MAX_ATTEMPTS + " failed attempts";
            return;
        }

        attempts++;
        connectInProgress = true;
        connectAttemptTicks = 0;
        status = "Reconnecting • attempt " + attempts + "/" + MAX_ATTEMPTS;
        Screen parent = client.currentScreen;
        ConnectScreen.connect(parent, client, ServerAddress.parse(lastServer.address), lastServer, false, null);
    }
}
