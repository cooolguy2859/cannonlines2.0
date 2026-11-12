# CannonLines

Minecraft 1.21.11 • Fabric • Java 21

CannonLines renders a client-side `END_ROD` particle trail behind nearby slime cannon entities. Use `/cannonlines` to toggle the trail.

## Distance Ping

- Default key: `1` (change it in Options → Controls → Key Binds).
- Aim at a block and press the key to place a ping at that location.
- The ping is shown with particles for 10 seconds.
- The action bar reports the distance in blocks (Minecraft blocks are used as the distance unit).
- Press the ping key again to replace your current ping.

**Multiplayer note:** this version is client-side only. The ping is currently visible only to the player who placed it. Showing the same ping to other CannonLines users requires a compatible server-side companion or a server that supports the mod's networking channel; that shared feature is not included in this commit.

## Build

Run `gradle build` with Java 21 installed, or use the repository's GitHub Actions build if configured.
