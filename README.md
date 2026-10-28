# Holo Input Recorder

A small Fabric client mod for Minecraft Java 26.3. It records exact player input timing during consented Holo practice rounds.

The mod sends one custom Minecraft packet per active round tick to the connected game server. It does not open an external network connection, upload files, or contain private service keys. It stays idle unless a compatible server starts a recorded round.

Captured fields include movement keys, jump, sneak, sprint, attack and use state, attack swings (left click) and use swings (right click) counted apart, raw left and right mouse presses, raw mouse motion before sensitivity, applied yaw and pitch, selected hotbar slot, screen state (any screen open, and whether it is your own inventory screen), and item-use state.

It also sends a sub-tick event log: each mouse movement, mouse button, bound-key press or release, scroll and swing, with the microsecond offset at which the input event was handled. Minecraft 26.x reads input through SDL3, which does not fire on a clock, so key and button timing resolves to the frame time. Mouse motion is the exception: with relative mouse mode, one event arrives per mouse report, so a 1000 Hz mouse gives about one movement per millisecond.

## Install

No building needed: download the JAR from
[the latest release](https://github.com/ApocalypseGamer1/holo-input-recorder/releases/latest)
([direct link](https://github.com/ApocalypseGamer1/holo-input-recorder/releases/download/v1.4.1/holo-input-recorder-1.4.1.jar)).

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3 (Loader 0.19.5 or newer, Java 25).
2. Install [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.3) for Minecraft 26.3.
3. Put `holo-input-recorder-1.4.1.jar` in the client `mods` folder.
4. Restart Minecraft.

The client shows `Holo input capture active for this round` when recording starts and `Holo input capture saved` when it stops.

During a sword fight on a Holo server the mod also draws a win bar on the left edge of the screen, like the evaluation bar in chess: your chance to win at the bottom, the opponent's at the top, with the leader's percentage at its end. The server sends the numbers; the bar hides when the fight ends and when F1 or F3 is on. The server has a per-player `/winbar on|off` switch.

## Build

Only needed if you want to change the mod; the release JAR above is the same build.

```text
./gradlew build
```

The JAR is written to `build/libs/`.

## Privacy

**Data protection:** To keep the training data clean, the mod also sends the server the list of your installed mods and a check that the mod itself hasn't been changed. This is only used to detect cheating, tampering with the data, and mods that aren't allowed on the server (such as combat recorders).

From version 1.4.0 the mod list includes, for each mod, the mod it is bundled in, its file size, and a SHA-512 fingerprint of its jar file. A fingerprint is a short code computed from the file; it identifies which public mod and version a file is (the same code Modrinth uses), but the file itself is never uploaded and cannot be rebuilt from it. Nothing else from your computer is read or sent: no file names or folders, no other files, no account details. The list is sent once when you join a Holo server that asks for it, over the normal Minecraft connection.

The report also carries a random install id: a number the mod makes up the first time it runs and keeps in its config folder (`config/holo-input-recorder/install-id.txt`). It lets the server notice when many accounts play from one install, to limit abuse. It is random, not taken from your hardware or your account, identifies nothing about you off this game, and you can reset it any time by deleting that file.

Input packets are sent only through the current Minecraft connection and only while the compatible server marks a consented round as active. The server checks the round ID and rejects invalid or stale packets.

The sub-tick event log is not a keylogger and cannot be used as one:

- It records nothing outside an active consented round.
- Inside your own inventory screen (the one you open with the inventory key) it keeps recording only the mouse: cursor movement, mouse buttons and scrolls. No key is recorded inside any screen, so the recipe-book search box is never seen.
- Every other screen stops it the moment it opens: chat, signs, books, anvils, the creative inventory, chests and every menu.
- It refuses any key the player has not bound to a game control, so keys that only type text are never seen.
- Typed characters are not hooked at all: the mod reads key codes, never text.

## License

Copyright (c) 2026 ApocalypseGamer1. All rights reserved. No permission is granted to copy, modify,
redistribute, or publish this source without written permission from the copyright owner.
