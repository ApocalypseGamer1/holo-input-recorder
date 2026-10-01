package holorecorder.protocol;

import holorecorder.RawInputCapture;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One per client tick. {@code swings} counts attack swings (left click; 26.x punch packet), the 1.21.5 meaning.
 * {@code useSwings} (new in version 2, last on the wire) counts right-click use swings; the server stores them
 * as {@code client_swings} and {@code client_use_swings}.
 */
public record ClientInputPayload(
        int version, String roundId, long sequence, int clientTick, long unixMs,
        double rawMouseDx, double rawMouseDy, float turnYaw, float turnPitch,
        int flags, int swings, int leftPresses, int rightPresses, int selectedSlot, int useSwings
) implements CustomPacketPayload {
    public static final int VERSION = 2;
    public static final Type<ClientInputPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "client_input_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientInputPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.version); buf.writeUtf(v.roundId, 64); buf.writeVarLong(v.sequence);
                buf.writeVarInt(v.clientTick); buf.writeVarLong(v.unixMs);
                buf.writeDouble(v.rawMouseDx); buf.writeDouble(v.rawMouseDy);
                buf.writeFloat(v.turnYaw); buf.writeFloat(v.turnPitch); buf.writeVarInt(v.flags);
                buf.writeVarInt(v.swings); buf.writeVarInt(v.leftPresses); buf.writeVarInt(v.rightPresses);
                buf.writeVarInt(v.selectedSlot); buf.writeVarInt(v.useSwings);
            },
            buf -> new ClientInputPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    /** The tick sample: the drained counters plus the view turn, key flags and hotbar slot. */
    public static ClientInputPayload of(String roundId, long sequence, int clientTick, long unixMs,
            RawInputCapture.Snapshot raw, float turnYaw, float turnPitch, int flags, int selectedSlot) {
        return new ClientInputPayload(VERSION, roundId, sequence, clientTick, unixMs, raw.dx(), raw.dy(),
                turnYaw, turnPitch, flags, raw.swings(), raw.leftPresses(), raw.rightPresses(), selectedSlot,
                raw.useSwings());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
