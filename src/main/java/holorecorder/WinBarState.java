package holorecorder;

/** Pure state of the win bar: latest server value, eased shown value, and when to hide. Times are in milliseconds. */
final class WinBarState {
    static final long STALE_MS = 3000;
    static final double SETTLE_TAU_MS = 55;
    static final double LEADER_HYSTERESIS = 0.01;

    private boolean active;
    private float target = 0.5f;
    private double shown = 0.5;
    private String nameA = "", nameB = "";
    private int viewer = 2;
    private long lastPacketMs;
    private long lastStepMs;
    private boolean bottomLeads = true;
    private Object world;

    /** The viewer's own side is at the bottom; a spectator sees A there. */
    static boolean bottomIsA(int viewer) {
        return viewer != 1;
    }

    static float sanitize(float p) {
        return Float.isNaN(p) ? 0.5f : Math.max(0.0f, Math.min(1.0f, p));
    }

    /** Chance of the bottom player for the given A chance and viewer byte. */
    static double bottomChance(float pA, int viewer) {
        float p = sanitize(pA);
        return bottomIsA(viewer) ? p : 1.0f - p;
    }

    /** Exponential smoothing step; dtMs <= 0 keeps the value. */
    static double ease(double current, double target, long dtMs) {
        if (dtMs <= 0) return current;
        return current + (target - current) * (1.0 - Math.exp(-dtMs / SETTLE_TAU_MS));
    }

    /** Leader text such as "72%" from the bottom player's chance. */
    static String leaderText(double bottom) {
        return Math.round(Math.max(bottom, 1.0 - bottom) * 100.0) + "%";
    }

    /** Keeps the previous leader inside the margin around 50% so the label does not flip-flop. */
    static boolean leaderIsBottom(boolean previous, double bottom) {
        if (bottom > 0.5 + LEADER_HYSTERESIS) return true;
        if (bottom < 0.5 - LEADER_HYSTERESIS) return false;
        return previous;
    }

    void update(boolean active, float pA, String nameA, String nameB, int viewer, Object world, long nowMs) {
        boolean wasVisible = visible(nowMs, this.world);
        this.active = active;
        if (!active) return;
        this.nameA = nameA == null ? "" : nameA;
        this.nameB = nameB == null ? "" : nameB;
        this.viewer = viewer;
        this.world = world;
        this.target = sanitize(pA);
        lastPacketMs = nowMs;
        if (!wasVisible) {
            shown = bottomChance(target, viewer);
            lastStepMs = nowMs;
            bottomLeads = shown >= 0.5;
        }
    }

    void reset() {
        active = false;
        world = null;
    }

    boolean visible(long nowMs, Object currentWorld) {
        return active && currentWorld != null && currentWorld == world && nowMs - lastPacketMs <= STALE_MS;
    }

    /** Advance the animation to nowMs; call once per frame while visible. */
    void step(long nowMs) {
        shown = ease(shown, bottomChance(target, viewer), nowMs - lastStepMs);
        lastStepMs = nowMs;
        bottomLeads = leaderIsBottom(bottomLeads, shown);
    }

    double shownBottom() { return shown; }
    boolean bottomLeads() { return bottomLeads; }
    String bottomName() { return bottomIsA(viewer) ? nameA : nameB; }
    String topName() { return bottomIsA(viewer) ? nameB : nameA; }
}
