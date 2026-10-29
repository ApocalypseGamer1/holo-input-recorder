package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server hints that a newer mod exists. The text is only a hint: the client asks the official release page itself and
 * never downloads from, or trusts, anything in here. A version this client does not know has its bytes skipped.
 */
public record UpdatePromptPayload(int version, String latest) implements CustomPacketPayload {
    public static final int VERSION = 1;
    public static final int MAX_LATEST = 32;
    public static final Type<UpdatePromptPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "update_prompt"));
    public static final StreamCodec<ByteBuf, UpdatePromptPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.stringUtf8(MAX_LATEST).encode(buf, v.latest);
            },
            buf -> {
                int version = ByteBufCodecs.VAR_INT.decode(buf);
                if (version != VERSION) {
                    buf.skipBytes(buf.readableBytes());
                    return new UpdatePromptPayload(version, "");
                }
                return new UpdatePromptPayload(version, ByteBufCodecs.stringUtf8(MAX_LATEST).decode(buf));
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
