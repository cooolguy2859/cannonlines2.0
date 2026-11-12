# CannonLines

Minecraft 1.21.11 • Fabric • Java 21

CannonLines renders a client-side `END_ROD` particle trail behind nearby slime cannon entities. Use `/cannonlines` to toggle the trail.

## Enemy Highlight

- Default key: `H` (change it in Options → Controls → Key Binds).
- Aim directly at another player and press the key to mark them.
- The marked player gets a glowing-style outline visible through blocks.
- Aim at the same player and press again to unmark them.
- The outline is applied locally on your client; it does not apply Minecraft's server-side Glowing status effect.

**Multiplayer note:** enemy marks are currently local to the player who marks them. Sharing marks with other CannonLines users requires a compatible server-side networking component and is not included yet.

## Distance Ping

- Default key: `1` (change it in Options → Controls → Key Binds).
- Aim at a block and press the key to place a ping at that location.
- The ping is shown with particles for 10 seconds.
- The action bar reports the distance in blocks.
- Press the ping key again to replace your current ping.

**Multiplayer note:** pings are currently visible only to the player who placed them. Sharing pings with other CannonLines users requires a compatible server-side networking component and is not included yet.

## Cannon trail performance

The 64-block trail is now emitted in four staggered passes, with wider particle spacing. This reduces the number of particles spawned per scan while keeping the trail visually continuous. The scan remains limited to nearby slime entities.

## Build

Run `gradle build` with Java 21 installed.
