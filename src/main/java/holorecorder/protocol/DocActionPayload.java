package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** What the player did on the document screen: {@code "back"} or {@code "close"}. */
public record DocActionPayload(String action) implements CustomPacketPayload {
    public static final String BACK = "back", CLOSE = "close";
    public static final Type<DocActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "doc_action"));
    public static final StreamCodec<ByteBuf, DocActionPayload> CODEC = StreamCodec.of(
            (buf, v) -> ByteBufCodecs.stringUtf8(64).encode(buf, v.action),
            buf -> new DocActionPayload(ByteBufCodecs.stringUtf8(64).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
