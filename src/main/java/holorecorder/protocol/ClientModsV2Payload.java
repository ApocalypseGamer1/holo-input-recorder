package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod list for data protection, version 2: each mod's id, version, name, the mod it is bundled in (if any), its file
 * size and the SHA-512 of its jar (lowercase hex, or "" for bundled mods and anything that is not a plain jar file).
 * Only fingerprints are sent, never files. One report may span several parts (same {@code reportId}, parts
 * {@code 0 .. parts - 1}), each under the serverbound payload limit; sent once per connection to a server that
 * declared the channel.
 */
public record ClientModsV2Payload(int version, String recorderVersion, String installId, long reportId, int part,
        int parts, List<Mod> mods) implements CustomPacketPayload {
    public static final int VERSION = 2;
    public static final int MAX_STRING = 100;
    public static final int HASH_LENGTH = 128;
    public static final int MAX_MODS_PER_PART = 400;
    public static final int MAX_PARTS = 16;

    /** {@code parent} is the id of the mod this one is bundled in ("" for a top-level jar); {@code size} -1 if unknown. */
    public record Mod(String id, String version, String name, String parent, long size, String sha512) {}

    public static final Type<ClientModsV2Payload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "client_mods_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientModsV2Payload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.version);
                buf.writeUtf(cut(v.recorderVersion), MAX_STRING);
                buf.writeUtf(cut(v.installId), MAX_STRING);
                buf.writeLong(v.reportId);
                buf.writeVarInt(v.part);
                buf.writeVarInt(v.parts);
                int n = Math.min(v.mods.size(), MAX_MODS_PER_PART);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Mod m = v.mods.get(i);
                    buf.writeUtf(cut(m.id), MAX_STRING);
                    buf.writeUtf(cut(m.version), MAX_STRING);
                    buf.writeUtf(cut(m.name), MAX_STRING);
                    buf.writeUtf(cut(m.parent), MAX_STRING);
                    buf.writeVarLong(Math.max(-1L, m.size) + 1L);
                    buf.writeUtf(hash(m.sha512), HASH_LENGTH);
                }
            },
            buf -> {
                int version = buf.readVarInt();
                String recorderVersion = buf.readUtf(MAX_STRING);
                String installId = buf.readUtf(MAX_STRING);
                long reportId = buf.readLong();
                int part = buf.readVarInt();
                int parts = buf.readVarInt();
                if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) {
                    throw new IllegalArgumentException("holo: report part " + part + " of " + parts);
                }
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_MODS_PER_PART) throw new IllegalArgumentException("holo: mod count " + n);
                List<Mod> mods = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    String id = buf.readUtf(MAX_STRING), ver = buf.readUtf(MAX_STRING), name = buf.readUtf(MAX_STRING);
                    String parent = buf.readUtf(MAX_STRING);
                    long size = buf.readVarLong() - 1L;
                    String sha = buf.readUtf(HASH_LENGTH);
                    if (!sha.isEmpty() && !isHex(sha)) throw new IllegalArgumentException("holo: bad sha512 for " + id);
                    mods.add(new Mod(id, ver, name, parent, size, sha));
                }
                return new ClientModsV2Payload(version, recorderVersion, installId, reportId, part, parts, List.copyOf(mods));
            });

    /** A part with the caps applied, so the encoder never refuses it. */
    public static ClientModsV2Payload of(String recorderVersion, String installId, long reportId, int part, int parts,
            List<Mod> mods) {
        List<Mod> capped = new ArrayList<>(Math.min(mods.size(), MAX_MODS_PER_PART));
        for (Mod m : mods) {
            if (capped.size() == MAX_MODS_PER_PART) break;
            capped.add(capped(m));
        }
        return new ClientModsV2Payload(VERSION, cut(recorderVersion), cut(installId), reportId, part, parts, List.copyOf(capped));
    }

    /** The entry as it goes on the wire: strings cut to {@link #MAX_STRING}, a malformed hash dropped, size >= -1. */
    public static Mod capped(Mod m) {
        return new Mod(cut(m.id), cut(m.version), cut(m.name), cut(m.parent), Math.max(-1L, m.size), hash(m.sha512));
    }

    /** Exactly 128 lowercase hex characters, or "". */
    static boolean isHex(String s) {
        if (s.length() != HASH_LENGTH) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String hash(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        return isHex(lower) ? lower : "";
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() <= MAX_STRING ? s : s.substring(0, MAX_STRING);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
