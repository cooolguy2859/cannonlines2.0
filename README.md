# CannonLines

Minecraft 1.21.11 • Fabric • Java 21

CannonLines renders a client-side `END_ROD` particle trail behind nearby slime cannon entities. Use `/cannonlines` to toggle the trail.

## Ruin Party

CannonLines now has a shared party relay. Players using CannonLines can join the same party and share location pings while connected to the same Minecraft server.

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

Run `gradle build` with Java 21 installed.
