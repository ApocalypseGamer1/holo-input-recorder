package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sub-tick input events (SDL3 on 26.x) for one client tick, sent beside {@link ClientInputPayload}.
 *
 * <p>The event loop has no per-event clock here: events are handed over while Minecraft pumps the event queue once per frame, so
 * key and button timing resolves to the frame time. Mouse motion is the exception - SDL relative mouse mode
 * delivers one un-merged report per mouse poll (to be confirmed live on 26.3), so a 1000 Hz mouse
 * produces one move event per millisecond. Every event therefore carries the microsecond offset at which the
 * callback ran, not a hardware timestamp.
 *
 * <p>26.x reads SDL3, not GLFW: key codes are SDL scancodes (the codes {@code KeyMapping} uses), mouse buttons are
 * 1 left / 2 middle / 3 right, and a key repeat is action -1. Rows with {@code client_capture_version >= 2} use these.
 *
 * <p>The arrays are parallel and all the same length. Meaning of {@code a} and {@code b} per {@code type}:
 * <ul>
 *   <li>{@link #TYPE_MOUSE_MOVE} - a = raw dx, b = raw dy (pixels, before sensitivity)</li>
 *   <li>{@link #TYPE_BUTTON_DOWN} / {@link #TYPE_BUTTON_UP} - a = button, b = modifier bits (SDL_Keymod)</li>
 *   <li>{@link #TYPE_KEY_DOWN} / {@link #TYPE_KEY_UP} / {@link #TYPE_KEY_REPEAT} - a = key (SDL scancode), b = modifier bits (SDL_Keymod)</li>
 *   <li>{@link #TYPE_SCROLL} - a = x offset, b = y offset</li>
 *   <li>{@link #TYPE_SWING} - a = 0; b = 0 for an attack swing (left click, punch packet), b = {@link #SWING_USE} for a use swing
 *       (right click that animates the arm). The backend accepts only types 0-7, so no new type was added.</li>
 * </ul>
 */
public record ClientEventsPayload(
        int version, String roundId, long sequence, int clientTick, long tickUnixMs, int dropped,
        int[] offsetMicros, byte[] types, float[] a, float[] b
) implements CustomPacketPayload {
    public static final int VERSION = 1;
    /** Refused above this; one tick of a 1000 Hz mouse is ~50 moves, so this is a wide margin. */
    public static final int MAX_EVENTS = 2048;

    public static final byte TYPE_MOUSE_MOVE = 0;
    public static final byte TYPE_BUTTON_DOWN = 1;
    public static final byte TYPE_BUTTON_UP = 2;
    public static final byte TYPE_KEY_DOWN = 3;
    public static final byte TYPE_KEY_UP = 4;
    public static final byte TYPE_KEY_REPEAT = 5;
    public static final byte TYPE_SCROLL = 6;
    public static final byte TYPE_SWING = 7;
    public static final float SWING_USE = 1.0f;

    public ClientEventsPayload {
        if (offsetMicros.length != types.length || offsetMicros.length != a.length
                || offsetMicros.length != b.length) {
            throw new IllegalArgumentException("client event arrays must be the same length: "
                    + offsetMicros.length + "/" + types.length + "/" + a.length + "/" + b.length);
        }
    }

    public static final Type<ClientEventsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "client_events_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientEventsPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.version); buf.writeUtf(v.roundId, 64); buf.writeVarLong(v.sequence);
                buf.writeVarInt(v.clientTick); buf.writeVarLong(v.tickUnixMs); buf.writeVarInt(v.dropped);
                int n = Math.min(v.offsetMicros.length, MAX_EVENTS);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeVarInt(v.offsetMicros[i]);
                    buf.writeByte(v.types[i]);
                    buf.writeFloat(v.a[i]);
                    buf.writeFloat(v.b[i]);
                }
            },
            buf -> {
                int version = buf.readVarInt();
                String roundId = buf.readUtf(64);
                long sequence = buf.readVarLong();
                int clientTick = buf.readVarInt();
                long tickUnixMs = buf.readVarLong();
                int dropped = buf.readVarInt();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_EVENTS) throw new IllegalArgumentException("holo: event count " + n);
                int[] offsets = new int[n];
                byte[] types = new byte[n];
                float[] a = new float[n];
                float[] b = new float[n];
                for (int i = 0; i < n; i++) {
                    offsets[i] = buf.readVarInt();
                    types[i] = buf.readByte();
                    a[i] = buf.readFloat();
                    b[i] = buf.readFloat();
                }
                return new ClientEventsPayload(version, roundId, sequence, clientTick, tickUnixMs, dropped,
                        offsets, types, a, b);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
