# Server side of the mod report, version 2 (holoserver)

The client (this mod, 1.4.0) sends `holoserver:client_mods_v2` once per join when the server declares that channel,
otherwise `holoserver:client_mods_v1` as before. Declare v2 to receive fingerprints; keep v1 registered while older
clients are around (a server that declares both receives v2 from 1.4.0 clients and v1 from older ones).

## Wire format (`ClientModsV2Payload`)

    VarInt version (2) | String recorderVersion (<= 100) | Long reportId | VarInt part | VarInt parts | VarInt count (<= 400)
    then per mod: String id | String version | String name | String parent (<= 100 each; parent = id of the mod this
    one is bundled in, "" for a top-level jar) | VarLong size + 1 (0 = unknown) | String sha512 (128 lowercase hex, or "")

A report is `parts` packets (1..16) sharing `reportId`, numbered 0..parts-1, each under 32000 bytes. Mods are in
order: top-level jars first, then bundled ones, each group by id. Only top-level jars carry a fingerprint; a bundled
mod's bytes are covered by its parent's.

## What the server should do

1. Reassemble per connection: keep parts until all `parts` with the same `reportId` arrived (time out after 30 s;
   treat a missing or incomplete report as "no report"). Reject a second reportId on the same connection.
2. Store the report with the player UUID and time (keep it with the existing v1 store; same retention).
3. Classify every fingerprint:
   - **blocked**: in the server's blocklist (config: list of sha512 and of mod ids, each with a reason) -> refuse.
   - **known**: in the server's local cache of identified fingerprints -> fine.
   - **unknown**: look it up with Modrinth's bulk endpoint `POST https://api.modrinth.com/v2/version_files`
     `{"hashes": [...], "algorithm": "sha512"}` from the server (never from the client); cache hits and misses (misses
     for a day). A Modrinth match gives project id, slug, title and version: if the project id or slug is blocklisted,
     refuse; otherwise known. Still unknown (private or rebuilt jar): flag it for review, do not refuse.
4. Refuse at join (or at the start of a bot round, whichever the server prefers) with a plain message:
   "<mod name> isn't allowed on Holo servers (combat recorders and similar tools). Remove it and rejoin."
   Log the refusal with the matched rule.
5. A client that declared the channel but sent no report, or whose own `recorderVersion` is below the minimum the
   server requires, is treated like a missing integrity check (existing behaviour).

The blocklist starts with the recorder seen on 2026-10-07 (its mod id and, once a 1.4.0 client reports it, its
fingerprint). Mod ids are easy to rename; fingerprints are not (only a rebuilt jar changes them), so prefer
fingerprints and project ids.

## Limits (by design)

Everything in the report is computed on the player's computer, so a modified client can lie. The report is a rule at
the door that honest players follow and casual cheaters trip over; the protection that cannot be bypassed is
server-side (a daily cap on bot rounds per player, no bot-vs-bot spectating).
