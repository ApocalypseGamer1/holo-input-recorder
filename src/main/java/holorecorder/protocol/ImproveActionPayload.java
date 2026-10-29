package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** What the player did on the Improve screen: {@code "back"} or {@code "close"}. */
public record ImproveActionPayload(String action) implements CustomPacketPayload {
    public static final String BACK = "back", CLOSE = "close";
    public static final Type<ImproveActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "improve_action"));
    public static final StreamCodec<ByteBuf, ImproveActionPayload> CODEC = StreamCodec.of(
            (buf, v) -> ByteBufCodecs.stringUtf8(64).encode(buf, v.action),
            buf -> new ImproveActionPayload(ByteBufCodecs.stringUtf8(64).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
