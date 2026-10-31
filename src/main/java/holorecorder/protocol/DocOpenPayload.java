package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opens (or refreshes) the guide, rules or privacy document screen: one UTF-8 JSON string, already in the player's language. */
public record DocOpenPayload(String json) implements CustomPacketPayload {
    public static final int MAX_JSON = 200_000;
    public static final Type<DocOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "doc_open"));
    public static final StreamCodec<ByteBuf, DocOpenPayload> CODEC = StreamCodec.of(
            (buf, v) -> ByteBufCodecs.stringUtf8(MAX_JSON).encode(buf, v.json),
            buf -> new DocOpenPayload(ByteBufCodecs.stringUtf8(MAX_JSON).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
