package holorecorder;

/** Render-thread counters drained once at the start of each client tick. */
public final class RawInputCapture {
    public record Snapshot(double dx, double dy, int leftPresses, int rightPresses, int swings) {}

    private static double dx, dy;
    private static int leftPresses, rightPresses, swings;

    private RawInputCapture() {}

    public static synchronized void mouseTurn(double x, double y) { dx += x; dy += y; }
    public static synchronized void mousePress(int button) {
        if (button == 0) leftPresses++;
        else if (button == 1) rightPresses++;
    }
    public static synchronized void swing() { swings++; }
    public static synchronized Snapshot drain() {
        Snapshot out = new Snapshot(dx, dy, leftPresses, rightPresses, swings);
        dx = dy = 0.0; leftPresses = rightPresses = swings = 0;
        return out;
    }
    public static synchronized void clear() { drain(); }
}
