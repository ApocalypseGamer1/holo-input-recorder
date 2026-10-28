package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Win chance of side A in a sword fight. viewer: 0 = this client is A, 1 = this client is B, 2 = spectator. */
public record WinBarPayload(boolean active, float pA, String nameA, String nameB, byte viewer) implements CustomPacketPayload {
    public static final Type<WinBarPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "winbar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WinBarPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.active);
                buf.writeFloat(value.pA);
                buf.writeUtf(value.nameA, 16);
                buf.writeUtf(value.nameB, 16);
                buf.writeByte(value.viewer);
            },
            buf -> new WinBarPayload(buf.readBoolean(), buf.readFloat(), buf.readUtf(16), buf.readUtf(16), buf.readByte()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
