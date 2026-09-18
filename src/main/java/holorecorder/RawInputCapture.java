package holorecorder;

import holorecorder.protocol.ClientEventsPayload;

/**
 * Render-thread counters drained once at the start of each client tick, plus the sub-tick GLFW event log.
 *
 * <p>The counters keep the tick row exactly as it was. The event log is additive: every GLFW callback that
 * fires between two drains is stored with the microsecond offset at which it ran, so a 1000 Hz mouse with raw
 * input on lands about one move event per millisecond.
 *
 * <p>Capture is gated for privacy. {@link #armed} is only true inside a consented round with no screen open,
 * and key events are refused unless the key is one the player has bound to a game control. Typing is never
 * captured: a screen closes the gate, and character input is not hooked at all.
 */
public final class RawInputCapture {
    public record Snapshot(double dx, double dy, int leftPresses, int rightPresses, int swings) {}

    /** One tick of sub-tick events, already packed into the parallel arrays the payload sends. */
    public record Events(int[] offsetMicros, byte[] types, float[] a, float[] b, int dropped, long baseUnixMs) {
        public int size() { return offsetMicros.length; }
    }

    private static final int CAPACITY = ClientEventsPayload.MAX_EVENTS;

    private static double dx, dy;
    private static int leftPresses, rightPresses, swings;

    private static final int[] offsets = new int[CAPACITY];
    private static final byte[] types = new byte[CAPACITY];
    private static final float[] valuesA = new float[CAPACITY];
    private static final float[] valuesB = new float[CAPACITY];
    private static int count, dropped;
    private static long baseNanos = System.nanoTime();
    private static long baseUnixMs = System.currentTimeMillis();

    private static volatile boolean armed;
    private static volatile KeyFilter keyFilter = key -> false;

    /** Answers whether a GLFW key code is bound to a game control, so unbound keys are never logged. */
    public interface KeyFilter { boolean allows(int glfwKey); }

    private RawInputCapture() {}

    public static void arm(boolean on, KeyFilter filter) {
        keyFilter = filter == null ? key -> false : filter;
        armed = on;
    }

    public static boolean armed() { return armed; }

    public static synchronized void mouseTurn(double x, double y) { dx += x; dy += y; }

    public static synchronized void mousePress(int button) {
        if (button == 0) leftPresses++;
        else if (button == 1) rightPresses++;
    }

    public static synchronized void swing() {
        swings++;
        record(ClientEventsPayload.TYPE_SWING, 0.0f, 0.0f);
    }

    public static synchronized void move(double rawDx, double rawDy) {
        if (rawDx == 0.0 && rawDy == 0.0) return;
        record(ClientEventsPayload.TYPE_MOUSE_MOVE, (float) rawDx, (float) rawDy);
    }

    public static synchronized void button(int button, boolean down, int modifiers) {
        record(down ? ClientEventsPayload.TYPE_BUTTON_DOWN : ClientEventsPayload.TYPE_BUTTON_UP,
                button, modifiers);
    }

    public static synchronized void key(int glfwKey, int action, int modifiers) {
        if (!keyFilter.allows(glfwKey)) return;
        byte type = switch (action) {
            case 1 -> ClientEventsPayload.TYPE_KEY_DOWN;
            case 0 -> ClientEventsPayload.TYPE_KEY_UP;
            case 2 -> ClientEventsPayload.TYPE_KEY_REPEAT;
            default -> -1;
        };
        if (type >= 0) record(type, glfwKey, modifiers);
    }

    public static synchronized void scroll(double xOffset, double yOffset) {
        record(ClientEventsPayload.TYPE_SCROLL, (float) xOffset, (float) yOffset);
    }

    private static void record(byte type, float a, float b) {
        if (!armed) return;
        if (count >= CAPACITY) { dropped++; return; }
        long micros = (System.nanoTime() - baseNanos) / 1000L;
        offsets[count] = micros < 0 ? 0 : micros > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) micros;
        types[count] = type;
        valuesA[count] = a;
        valuesB[count] = b;
        count++;
    }

    public static synchronized Snapshot drain() {
        Snapshot out = new Snapshot(dx, dy, leftPresses, rightPresses, swings);
        dx = dy = 0.0; leftPresses = rightPresses = swings = 0;
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
        dx = dy = 0.0; leftPresses = rightPresses = swings = 0;
        resetWindow();
    }
}
