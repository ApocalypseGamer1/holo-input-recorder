package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Mod list for data protection: each mod's id, version and name, sent once per connection. */
public record ClientModsPayload(int version, String recorderVersion, List<Mod> mods) implements CustomPacketPayload {
    public static final int VERSION = 1;
    /** Entries beyond this are dropped; every string is cut to {@link #MAX_STRING} characters. */
    public static final int MAX_MODS = 400;
    public static final int MAX_STRING = 100;

    public record Mod(String id, String version, String name) {}

    public static final Type<ClientModsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "client_mods_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientModsPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.version);
                buf.writeUtf(cut(v.recorderVersion), MAX_STRING);
                int n = Math.min(v.mods.size(), MAX_MODS);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    Mod m = v.mods.get(i);
                    buf.writeUtf(cut(m.id), MAX_STRING);
                    buf.writeUtf(cut(m.version), MAX_STRING);
                    buf.writeUtf(cut(m.name), MAX_STRING);
                }
            },
            buf -> {
                int version = buf.readVarInt();
                String recorderVersion = buf.readUtf(MAX_STRING);
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_MODS) throw new IllegalArgumentException("holo: mod count " + n);
                List<Mod> mods = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    mods.add(new Mod(buf.readUtf(MAX_STRING), buf.readUtf(MAX_STRING), buf.readUtf(MAX_STRING)));
                }
                return new ClientModsPayload(version, recorderVersion, mods);
            });

    /** Builds a payload with the caps applied, so the encoder never refuses an oversized string. */
    public static ClientModsPayload of(String recorderVersion, List<Mod> mods) {
        List<Mod> capped = new ArrayList<>(Math.min(mods.size(), MAX_MODS));
        for (Mod m : mods) {
            if (capped.size() == MAX_MODS) break;
            capped.add(new Mod(cut(m.id), cut(m.version), cut(m.name)));
        }
        return new ClientModsPayload(VERSION, cut(recorderVersion), List.copyOf(capped));
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() <= MAX_STRING ? s : s.substring(0, MAX_STRING);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
