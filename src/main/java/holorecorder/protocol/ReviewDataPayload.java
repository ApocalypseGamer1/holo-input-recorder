package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Round review: part {@code index} of {@code count} of one gzipped review record (holorecorder.review.ReviewRecord). The
 * client starts the review once all parts of {@code session} are in; {@code count} 0 tells it to stop the review now.
 */
public record ReviewDataPayload(int session, int index, int count, byte[] data) implements CustomPacketPayload {
    public static final int CHUNK = 30000;
    public static final Type<ReviewDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "review_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReviewDataPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.session); buf.writeVarInt(v.index); buf.writeVarInt(v.count); buf.writeByteArray(v.data);
            },
            buf -> new ReviewDataPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
