# Holo Input Recorder

A small Fabric client mod for Minecraft 1.21.5. It records exact player input timing during consented Holo practice rounds.

The mod sends one custom Minecraft packet per active round tick to the connected game server. It does not open an external network connection, upload files, or contain private service keys. It stays idle unless a compatible server starts a recorded round.

Captured fields include movement keys, jump, sneak, sprint, attack and use state, main-hand swings, raw left and right mouse presses, raw mouse motion before sensitivity, applied yaw and pitch, selected hotbar slot, screen state, and item-use state.

## Install

1. Install Fabric Loader for Minecraft 1.21.5.
2. Install Fabric API for Minecraft 1.21.5.
3. Put the release JAR in the client `mods` folder.
4. Restart Minecraft.

The client shows `Holo input capture active for this round` when recording starts and `Holo input capture saved` when it stops.

## Build

```text
./gradlew build
```

The JAR is written to `build/libs/`.

## Privacy

Input packets are sent only through the current Minecraft connection and only while the compatible server marks a consented round as active. The server checks the round ID and rejects invalid or stale packets.

## License

Copyright (c) 2026 ApocalypseGamer1. All rights reserved. No permission is granted to copy, modify,
redistribute, or publish this source without written permission from the copyright owner.
