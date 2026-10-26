# CannonLines (source project)

This source project is set up for Minecraft 1.21.11, Fabric, and Java 21.

It renders a client-side `END_ROD` particle trail associated with nearby slime entities (the cannon entity in your screenshot). The trail begins just behind the entity and extends opposite its facing direction. Use `/cannonlines` to toggle the effect.

## Build
Run `gradle build` with Java 21 installed, or push the project to GitHub and download the `cannonlines-jar` artifact from Actions.

## Notes
This was reconstructed from the compiled JAR you uploaded, since the original source ZIP was not provided. The server's cannon rotation may use a custom convention; if the trail comes out the wrong side, the forward/rear direction can be inverted in `CannonLinesClient.java`.
