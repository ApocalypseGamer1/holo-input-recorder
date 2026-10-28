package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player closed the round review of {@code session}. */
public record ReviewEndPayload(int session) implements CustomPacketPayload {
    public static final Type<ReviewEndPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "review_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReviewEndPayload> CODEC = StreamCodec.of(
            (buf, v) -> buf.writeVarInt(v.session), buf -> new ReviewEndPayload(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
