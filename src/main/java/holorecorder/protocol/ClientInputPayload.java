package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientInputPayload(
        int version, String roundId, long sequence, int clientTick, long unixMs,
        double rawMouseDx, double rawMouseDy, float turnYaw, float turnPitch,
        int flags, int swings, int leftPresses, int rightPresses, int selectedSlot
) implements CustomPacketPayload {
    public static final int VERSION = 1;
    public static final Type<ClientInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("holoserver", "client_input_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientInputPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.version); buf.writeUtf(v.roundId, 64); buf.writeVarLong(v.sequence);
                buf.writeVarInt(v.clientTick); buf.writeVarLong(v.unixMs);
                buf.writeDouble(v.rawMouseDx); buf.writeDouble(v.rawMouseDy);
                buf.writeFloat(v.turnYaw); buf.writeFloat(v.turnPitch); buf.writeVarInt(v.flags);
                buf.writeVarInt(v.swings); buf.writeVarInt(v.leftPresses); buf.writeVarInt(v.rightPresses);
                buf.writeVarInt(v.selectedSlot);
            },
            buf -> new ClientInputPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
