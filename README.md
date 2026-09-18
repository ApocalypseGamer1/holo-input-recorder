# Holo Input Recorder

A small Fabric client mod for Minecraft 1.21.5. It records exact player input timing during consented Holo practice rounds.

The mod sends one custom Minecraft packet per active round tick to the connected game server. It does not open an external network connection, upload files, or contain private service keys. It stays idle unless a compatible server starts a recorded round.

Captured fields include movement keys, jump, sneak, sprint, attack and use state, main-hand swings, raw left and right mouse presses, raw mouse motion before sensitivity, applied yaw and pitch, selected hotbar slot, screen state, and item-use state.

It also sends a sub-tick event log: each mouse movement, mouse button, bound-key press or release, scroll and swing, with the microsecond offset at which the GLFW callback ran. GLFW does not fire on a clock, so key and button timing resolves to the frame time. Mouse motion is the exception: with the vanilla "Raw input" option on, one event arrives per mouse report, so a 1000 Hz mouse gives about one movement per millisecond.

## Install

No building needed: download the JAR from
[the latest release](https://github.com/ApocalypseGamer1/holo-input-recorder/releases/latest)
([direct link](https://github.com/ApocalypseGamer1/holo-input-recorder/releases/download/v1.1.0/holo-input-recorder-1.1.0.jar)).

1. Install Fabric Loader for Minecraft 1.21.5.
2. Install Fabric API for Minecraft 1.21.5.
3. Put `holo-input-recorder-1.1.0.jar` in the client `mods` folder. Remove any older copy first: two copies of the same mod stop Minecraft from starting.
4. Restart Minecraft.

The client shows `Holo input capture active for this round` when recording starts and `Holo input capture saved` when it stops.

## Build

Only needed if you want to change the mod; the release JAR above is the same build.

```text
./gradlew build
```

The JAR is written to `build/libs/`.

## Privacy

Input packets are sent only through the current Minecraft connection and only while the compatible server marks a consented round as active. The server checks the round ID and rejects invalid or stale packets.

The sub-tick event log is not a keylogger and cannot be used as one:

- It records nothing outside an active consented round.
- It stops the moment any screen is open, which covers chat, the inventory, signs and every menu.
- It refuses any key the player has not bound to a game control, so keys that only type text are never seen.
- Typed characters are not hooked at all: the mod reads key codes, never text.

## License

Copyright (c) 2026 ApocalypseGamer1. All rights reserved. No permission is granted to copy, modify,
redistribute, or publish this source without written permission from the copyright owner.
