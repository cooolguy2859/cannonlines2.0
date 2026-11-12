# CannonLines

Minecraft 1.21.11 • Fabric • Java 21 runtime

CannonLines renders a client-side `END_ROD` particle trail behind nearby slime cannon entities. Use `/cannonlines` to toggle the trail.

## Auto-Reconnect

- Run `/afk` while connected to toggle Auto-Reconnect on or off.
- When enabled, CannonLines saves the current multiplayer server and sends `/joinlifesteal`.
- If a temporary network failure disconnects you, it retries the last server with increasing delays.
- After reconnecting, it waits briefly and sends `/joinlifesteal` again.
- Press **Right Shift** to open the Auto-Reconnect menu. Use the button to enable or disable it.
- Check the current state with `/afk status`.
- Retries are limited to recognizable network errors. Server-initiated kicks, AFK removals, bans, and other policy disconnects are not automatically retried.
- The feature does not simulate movement or input to avoid server AFK checks.

## Ruin Party

CannonLines has a shared party relay. Players using CannonLines can join the same party and share location pings while connected to the same Minecraft server.

- Join or create the party: `/cannonparty join Ruin`
- Reconnects automatically on later game sessions after joining once.
- Check your party: `/cannonparty status`
- Leave the party: `/cannonparty leave`
- Place a ping with the `1` key. Other online Ruin party members on the same server and in the same dimension receive the ping.
- Party pings expire after 20 seconds.
- Party sync uses the CannonLines online relay; it does not require a special Minecraft server plugin.

Everyone who should see the pings must have the compatible CannonLines version installed and join the same party code. Party codes are not private passwords; anyone who knows `Ruin` can join.

## Enemy Highlight

- Default key: `H` (change it in Options → Controls → Key Binds).
- Aim directly at another player and press the key to mark them.
- The marked player gets a glowing-style outline intended to be visible through blocks.
- Aim at the same player and press again to unmark them.
- The outline is applied locally on your client; it does not apply Minecraft's server-side Glowing status effect.
- Enemy marks are still local only; party-wide player-mark syncing is not included yet.

## Distance Ping

- Default key: `1` (change it in Options → Controls → Key Binds).
- Aim at a block and press the key to place a ping at that location.
- The ping is shown with particles for 10 seconds locally; party members receive the shared ping for up to 20 seconds.
- The action bar reports the distance in Minecraft blocks.
- Press the ping key again to replace your current ping.

## Cannon trail performance

The 64-block trail is emitted in four staggered passes, with wider particle spacing. This reduces particle work while keeping the trail visually continuous. The scan remains limited to nearby slime entities.

## Build

The mod targets Java 21 bytecode. Build with Java 25 installed because the selected Fabric Loom version requires Java 25 to run.


Minecraft 26.2 port: the branch uses Java 25, the 26.2 Fabric API, updated key mapping/command APIs, and the new GUI extraction lifecycle.
