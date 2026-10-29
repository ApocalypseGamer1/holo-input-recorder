package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opens (or refreshes) the Improve screen: one UTF-8 JSON string, already in the player's language. */
public record ImproveOpenPayload(String json) implements CustomPacketPayload {
    public static final int MAX_JSON = 200_000;
    public static final Type<ImproveOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "improve_open"));
    public static final StreamCodec<ByteBuf, ImproveOpenPayload> CODEC = StreamCodec.of(
            (buf, v) -> ByteBufCodecs.stringUtf8(MAX_JSON).encode(buf, v.json),
            buf -> new ImproveOpenPayload(ByteBufCodecs.stringUtf8(MAX_JSON).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
