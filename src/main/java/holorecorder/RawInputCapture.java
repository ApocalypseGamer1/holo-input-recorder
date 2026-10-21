package holorecorder;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.protocol.ClientEventsPayload;

/**
 * Render-thread counters drained once at the start of each client tick, plus the sub-tick input event log.
 *
 * <p>The counters keep the tick row exactly as it was. The event log is additive: every SDL input event that
 * fires between two drains is stored with the microsecond offset at which it ran, so a 1000 Hz mouse with raw
 * input on lands about one move event per millisecond.
 *
 * <p>Capture is gated for privacy. {@link #armed} is only true inside a consented round, and the screen the player has
 * open is checked when each event fires: none, or the player's own inventory screen, which lets mouse moves, buttons and
 * scrolls through but no key at all (its recipe book has a search box). Any other screen (chat, signs, books, anvils)
 * stops everything. Key events are also refused unless the key is bound to a game control, and character input is not
 * hooked at all, so typing is never captured.
 */
public final class RawInputCapture {
    /** {@code swings} = attack swings (left click, ServerboundPunchPacket); {@code useSwings} = right-click use swings. */
    public record Snapshot(double dx, double dy, int leftPresses, int rightPresses, int swings, int useSwings) {}

    /** One tick of sub-tick events, already packed into the parallel arrays the payload sends. */
    public record Events(int[] offsetMicros, byte[] types, float[] a, float[] b, int dropped, long baseUnixMs) {
        public int size() { return offsetMicros.length; }
    }

    private static final int CAPACITY = ClientEventsPayload.MAX_EVENTS;

    private static double dx, dy;
    private static int leftPresses, rightPresses, swings, useSwings;

    private static final int[] offsets = new int[CAPACITY];
    private static final byte[] types = new byte[CAPACITY];
    private static final float[] valuesA = new float[CAPACITY];
    private static final float[] valuesB = new float[CAPACITY];
    private static int count, dropped;
    private static long baseNanos = System.nanoTime();
    private static long baseUnixMs = System.currentTimeMillis();

    private static volatile boolean armed;
    private static volatile KeyFilter keyFilter = key -> false;

    public static final int SCREEN_NONE = 0, SCREEN_INVENTORY = 1, SCREEN_OTHER = 2;

    /** The kind of screen open right now, asked on the thread that delivers the input events. */
    public interface ScreenProbe { int kind(); }

    private static volatile ScreenProbe screenProbe = () -> SCREEN_OTHER;

    /** Answers whether a key code is bound to a game control, so unbound keys are never logged. */
    public interface KeyFilter { boolean allows(int key); }

    private RawInputCapture() {}

    public static void arm(boolean on, KeyFilter filter) {
        keyFilter = filter == null ? key -> false : filter;
        armed = on;
    }

    public static boolean armed() { return armed; }

    public static void screenProbe(ScreenProbe probe) { screenProbe = probe == null ? () -> SCREEN_OTHER : probe; }

    /** Cursor moves with the mouse released: only inside the player inventory screen. */
    public static boolean cursorCapture() { return armed && screenProbe.kind() == SCREEN_INVENTORY; }

    public static synchronized void mouseTurn(double x, double y) { dx += x; dy += y; }

    public static synchronized void mousePress(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) leftPresses++;
        else if (button == InputConstants.MOUSE_BUTTON_RIGHT) rightPresses++;
    }

    public static synchronized void swing() {
        swings++;
        record(ClientEventsPayload.TYPE_SWING, 0.0f, 0.0f);
    }

    public static synchronized void useSwing() {
        useSwings++;
        record(ClientEventsPayload.TYPE_SWING, 0.0f, ClientEventsPayload.SWING_USE);
    }

    public static synchronized void move(double rawDx, double rawDy) {
        if (rawDx == 0.0 && rawDy == 0.0) return;
        record(ClientEventsPayload.TYPE_MOUSE_MOVE, (float) rawDx, (float) rawDy);
    }

    public static synchronized void button(int button, boolean down, int modifiers) {
        record(down ? ClientEventsPayload.TYPE_BUTTON_DOWN : ClientEventsPayload.TYPE_BUTTON_UP,
                button, modifiers);
    }

    public static synchronized void key(int key, int action, int modifiers) {
        if (!keyFilter.allows(key)) return;
        byte type = switch (action) {
            case 1 -> ClientEventsPayload.TYPE_KEY_DOWN;
            case 0 -> ClientEventsPayload.TYPE_KEY_UP;
            case -1 -> ClientEventsPayload.TYPE_KEY_REPEAT;
            default -> -1;
        };
        if (type >= 0) record(type, key, modifiers);
    }

    public static synchronized void scroll(double xOffset, double yOffset) {
        record(ClientEventsPayload.TYPE_SCROLL, (float) xOffset, (float) yOffset);
    }

    private static void record(byte type, float a, float b) {
        if (!armed) return;
        int screen = screenProbe.kind();
        if (screen == SCREEN_OTHER) return;
        if (screen == SCREEN_INVENTORY && (type == ClientEventsPayload.TYPE_KEY_DOWN || type == ClientEventsPayload.TYPE_KEY_UP
                || type == ClientEventsPayload.TYPE_KEY_REPEAT)) return;
        if (count >= CAPACITY) { dropped++; return; }
        long micros = (System.nanoTime() - baseNanos) / 1000L;
        offsets[count] = micros < 0 ? 0 : micros > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) micros;
        types[count] = type;
        valuesA[count] = a;
        valuesB[count] = b;
        count++;
    }

    public static synchronized Snapshot drain() {
        Snapshot out = new Snapshot(dx, dy, leftPresses, rightPresses, swings, useSwings);
        dx = dy = 0.0; leftPresses = rightPresses = swings = useSwings = 0;
        return out;
    }

    /** Takes the events of the tick that just ended and starts a new window. */
    public static synchronized Events drainEvents() {
        Events out = new Events(java.util.Arrays.copyOf(offsets, count), java.util.Arrays.copyOf(types, count),
                java.util.Arrays.copyOf(valuesA, count), java.util.Arrays.copyOf(valuesB, count),
                dropped, baseUnixMs);
        resetWindow();
        return out;
    }

    private static void resetWindow() {
        count = 0; dropped = 0;
        baseNanos = System.nanoTime();
        baseUnixMs = System.currentTimeMillis();
    }

    public static synchronized void clear() {
        dx = dy = 0.0; leftPresses = rightPresses = swings = useSwings = 0;
        resetWindow();
    }
}
