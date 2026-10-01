package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RoundStatePayload(boolean active, String roundId, String mode) implements CustomPacketPayload {
    public static final Type<RoundStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "recorder_round"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoundStatePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.active);
                buf.writeUtf(value.roundId, 64);
                buf.writeUtf(value.mode, 16);
            },
            buf -> new RoundStatePayload(buf.readBoolean(), buf.readUtf(64), buf.readUtf(16)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
