package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Round review: show how the coach would play on from {@code tick} of the review {@code session}. */
public record ReviewClipRequestPayload(int session, int tick) implements CustomPacketPayload {
    public static final Type<ReviewClipRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "review_clip_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReviewClipRequestPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.session); buf.writeVarInt(v.tick); },
            buf -> new ReviewClipRequestPayload(buf.readVarInt(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
