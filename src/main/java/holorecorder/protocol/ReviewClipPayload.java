package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Round review: the coach's play from {@code tick} as a gzipped review record (holorecorder.review.ReviewRecord) whose "self" is
 * the coach in the player's place and whose opponent moves as recorded. {@code status} is not {@link #OK} when there is no clip
 * ({@code data} is then empty).
 */
public record ReviewClipPayload(int session, int tick, int status, byte[] data) implements CustomPacketPayload {
    public static final int OK = 0, BUSY = 1, UNAVAILABLE = 2, NO_BOT = 3, TOO_LATE = 4, FAILED = 5;
    public static final int MAX_BYTES = 200_000;
    public static final Type<ReviewClipPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "review_clip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReviewClipPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.session); buf.writeVarInt(v.tick); buf.writeVarInt(v.status); buf.writeByteArray(v.data);
            },
            buf -> new ReviewClipPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_BYTES)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
