package holorecorder.review;

import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Tick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Names the sword techniques a fighter used in one {@link ReviewRecord}. Rule-based, no Minecraft types.
 *
 * <p>A hit is a swing start {@code s} (swingTime != NOT_SWINGING and the previous tick was not swinging or it went down)
 * on the same tick the other fighter is {@code hurt}, or the tick before. Its state is read at {@code s - 1}, because the
 * attack is processed before the tick's own movement. Self keys: tick i+1's {@code keys} were pressed during tick i.
 * Kinds marked (self) need keys; for the opponent they are inferred or absent. Every move has {@code start <= end}.
 * <ul>
 *   <li>WEAK_HIT: a hit with charge &lt;= 0.9, i.e. fewer than {@link #FULL_CHARGE} ticks since the last swing start.</li>
 *   <li>SPRINT_HIT: a charged hit while sprinting at {@code s - 1} (sprint knockback).</li>
 *   <li>CRIT: a charged hit while airborne, falling and not sprinting at {@code s - 1}.</li>
 *   <li>JUMP_HIT: a hit while airborne after a jump. Self: jump key during the airborne stretch. Opp: the stretch began with a
 *       rise of {@link #JUMP_RISE} blocks and no hit taken then, since knockback lifts a fighter the same way.</li>
 *   <li>JUMP_RESET (self): hurt on the ground, jump key down 0 to {@link #JUMP_RESET_WINDOW} ticks later. start = hurt, end = jump tick.</li>
 *   <li>W_TAP: after a hit, forward or sprint key released 1 to {@link #W_TAP_MAX} ticks (release starts from 1 tick before
 *       to {@link #TAP_WINDOW} ticks after the hit, no S pressed) and pressed again; end = the press. Opp (inferred): after a charged
 *       sprint hit the sprint flag dropped and came back within {@link #SPRINT_REGAIN} ticks (holding W never restarts it).</li>
 *   <li>S_TAP: after a hit, S pressed for 1 to {@link #S_TAP_MAX} ticks starting from 1 tick before to {@link #TAP_WINDOW} ticks after the hit, S up before it.
 *       Opp (inferred): moving away from the other fighter at {@link #BACK_SPEED} blocks/tick or more for
 *       {@link #BACK_MIN} to {@link #BACK_MAX} ticks starting within {@link #BACK_START} ticks of a hit, never hurt meanwhile.</li>
 *   <li>STRAFE_LEFT / STRAFE_RIGHT: at least {@link #STRAFE_MIN} ticks in a row within {@link #STRAFE_RANGE} blocks of the other
 *       fighter. Self: A (D) down and the other not. Opp (inferred): sideways speed of {@link #STRAFE_SPEED} blocks/tick or
 *       more across the line to the other fighter, ignoring {@link #KNOCK_TICKS} ticks after a hit.</li>
 * </ul>
 * Hit based moves start at the hit tick {@code s}; strafes start at the first tick of the run.
 */
public final class ReviewMoves {
    public enum Kind {
        CRIT, SPRINT_HIT, JUMP_HIT, WEAK_HIT, JUMP_RESET, W_TAP, S_TAP, STRAFE_LEFT, STRAFE_RIGHT;

        /** True for kinds that cannot be told from motion alone, so {@code of(r, false)} never returns them. */
        public boolean selfOnly() {
            return this == JUMP_RESET;
        }
    }

    /** Tick indexes into {@code ReviewRecord.ticks}, both inclusive. */
    public record Move(Kind kind, int start, int end) {}

    public static final int FULL_CHARGE = 11, TAP_WINDOW = 2, W_TAP_MAX = 3, S_TAP_MAX = 4, JUMP_RESET_WINDOW = 2;
    public static final int SPRINT_REGAIN = 6, BACK_START = 5, BACK_MIN = 2, BACK_MAX = 8;
    public static final int STRAFE_MIN = 4, KNOCK_TICKS = 3;
    public static final double STRAFE_RANGE = 6.0, STRAFE_SPEED = 0.08, BACK_SPEED = 0.05, JUMP_RISE = 0.3;

    private static final int K_FWD = 1, K_BACK = 2, K_LEFT = 4, K_RIGHT = 8, K_JUMP = 16, K_SPRINT = 64;
    private static final int MAX_STRETCH = 60;

    private final List<Tick> ticks;
    private final boolean self;
    private final int n;
    private final List<Move> out = new ArrayList<>();

    private ReviewMoves(ReviewRecord r, boolean self) {
        this.ticks = r.ticks;
        this.self = self;
        this.n = r.ticks.size();
    }

    /** The moves of the reviewed player ({@code self}) or of the opponent, ordered by start tick. */
    public static List<Move> of(ReviewRecord r, boolean self) {
        ReviewMoves m = new ReviewMoves(r, self);
        m.hits();
        m.strafes();
        if (self) m.jumpResets();
        m.out.sort(Comparator.comparingInt(Move::start).thenComparing(Move::kind).thenComparingInt(Move::end));
        return List.copyOf(m.out);
    }

    // ---- helpers ----

    private Fighter a(int i) {
        return self ? ticks.get(i).self : ticks.get(i).opp;
    }

    private Fighter o(int i) {
        return self ? ticks.get(i).opp : ticks.get(i).self;
    }

    private void add(Kind k, int start, int end) {
        out.add(new Move(k, start, end));
    }

    /** Keys pressed during tick k, -1 when unknown (opponent, or the last tick). */
    private int keys(int k) {
        return self && k >= 0 && k + 1 < n ? ticks.get(k + 1).keys : -1;
    }

    private boolean valid(int k) {
        return keys(k) >= 0;
    }

    private boolean has(int k, int mask) {
        int v = keys(k);
        return v >= 0 && (v & mask) != 0;
    }

    private boolean start(int i) {
        byte c = a(i).swingTime;
        return c != ReviewRecord.NOT_SWINGING && (i == 0 || a(i - 1).swingTime == ReviewRecord.NOT_SWINGING || c < a(i - 1).swingTime);
    }

    private boolean near(int k) {
        return Math.hypot(o(k).x - a(k).x, o(k).z - a(k).z) <= STRAFE_RANGE;
    }

    private boolean hurtBetween(int from, int to) {
        for (int j = Math.max(0, from); j <= Math.min(n - 1, to); j++) if (a(j).hurt) return true;
        return false;
    }

    // ---- hit based ----

    private void hits() {
        int lastSwing = -1;
        for (int h = 0; h < n; h++) {
            if (!o(h).hurt) continue;
            int s = start(h) ? h : h > 0 && start(h - 1) ? h - 1 : -1;
            if (s < 0 || s <= lastSwing) continue;
            lastSwing = s;
            boolean charged = true;
            for (int j = Math.max(0, s - FULL_CHARGE + 1); j < s; j++) if (start(j)) charged = false;
            if (!charged) add(Kind.WEAK_HIT, s, s);
            if (s < 1) continue;
            boolean air = !a(s - 1).onGround, sprint = a(s - 1).sprinting;
            if (charged && sprint) add(Kind.SPRINT_HIT, s, s);
            if (charged && air && !sprint && falling(s - 1)) add(Kind.CRIT, s, s);
            if (air && jumped(s - 1)) add(Kind.JUMP_HIT, s, s);
            if (self) {
                if (!sTap(s)) wTap(s);
            } else {
                if (charged && sprint) sprintRegain(s);
                backOff(s);
            }
        }
    }

    // fallDistance > 0: some tick of the current airborne stretch went down.
    private boolean falling(int i) {
        for (int k = i; k >= 1 && !a(k).onGround && i - k < MAX_STRETCH; k--) if (a(k).y < a(k - 1).y) return true;
        return false;
    }

    // The airborne stretch ending at tick i began with a jump.
    private boolean jumped(int i) {
        int f = i;
        while (f >= 1 && !a(f - 1).onGround && i - f < MAX_STRETCH) f--;
        if (f < 1 || !a(f - 1).onGround) return false;
        if (!self) return a(f).y - a(f - 1).y >= JUMP_RISE && !a(f).hurt && !a(f - 1).hurt;
        for (int k = f; k <= i; k++) if (has(k, K_JUMP)) return true;
        return false;
    }

    private void wTap(int s) {
        for (int mask : new int[] {K_FWD, K_SPRINT}) {
            for (int r = s - 1; r <= s + TAP_WINDOW; r++) {
                if (!valid(r) || has(r, mask) || !has(r - 1, mask)) continue;
                int len = 0;
                while (len <= W_TAP_MAX && valid(r + len) && !has(r + len, mask)) len++;
                if (len < 1 || len > W_TAP_MAX || !has(r + len, mask)) continue;
                add(Kind.W_TAP, s, r + len);
                return;
            }
        }
    }

    private boolean sTap(int s) {
        for (int r = s - 1; r <= s + TAP_WINDOW; r++) {
            if (!has(r, K_BACK) || !valid(r - 1) || has(r - 1, K_BACK)) continue;
            int len = 1;
            while (len <= S_TAP_MAX && has(r + len, K_BACK)) len++;
            if (len > S_TAP_MAX || !valid(r + len)) continue;
            add(Kind.S_TAP, s, r + len - 1);
            return true;
        }
        return false;
    }

    private void sprintRegain(int s) {
        if (a(s).sprinting) return;
        for (int k = s + 1; k <= Math.min(n - 1, s + SPRINT_REGAIN); k++) {
            if (a(k).sprinting) {
                add(Kind.W_TAP, s, k);
                return;
            }
        }
    }

    // Velocity of the fighter at tick k along the line to the other fighter, positive = closing in.
    private double radial(int k) {
        double dx = o(k - 1).x - a(k - 1).x, dz = o(k - 1).z - a(k - 1).z, len = Math.hypot(dx, dz);
        if (len < 0.1) return 0;
        return ((a(k).x - a(k - 1).x) * dx + (a(k).z - a(k - 1).z) * dz) / len;
    }

    // Sideways velocity at tick k across the line to the other fighter, positive = to the mover's left.
    private double lateral(int k) {
        double dx = o(k - 1).x - a(k - 1).x, dz = o(k - 1).z - a(k - 1).z, len = Math.hypot(dx, dz);
        if (len < 0.1) return 0;
        return ((a(k).x - a(k - 1).x) * dz - (a(k).z - a(k - 1).z) * dx) / len;
    }

    private void backOff(int s) {
        for (int r = Math.max(1, s); r <= s + BACK_START && r < n; r++) {
            if (radial(r) > -BACK_SPEED || (r - 1 >= 1 && radial(r - 1) <= -BACK_SPEED)) continue;
            int len = 0;
            while (r + len < n && radial(r + len) <= -BACK_SPEED) len++;
            if (r + len >= n || len < BACK_MIN || len > BACK_MAX || hurtBetween(s - 1, r + len)) continue;
            add(Kind.S_TAP, s, r + len - 1);
            return;
        }
    }

    // ---- not hit based ----

    private void jumpResets() {
        for (int h = 1; h < n; h++) {
            if (!a(h).hurt || !a(h - 1).onGround) continue;
            for (int k = h; k <= h + JUMP_RESET_WINDOW; k++) {
                if (has(k, K_JUMP)) {
                    add(Kind.JUMP_RESET, h, k);
                    break;
                }
            }
        }
    }

    private void strafes() {
        if (self) {
            run(Kind.STRAFE_LEFT, k -> has(k, K_LEFT) && !has(k, K_RIGHT) && near(k));
            run(Kind.STRAFE_RIGHT, k -> has(k, K_RIGHT) && !has(k, K_LEFT) && near(k));
        } else {
            run(Kind.STRAFE_LEFT, k -> k >= 1 && lateral(k) >= STRAFE_SPEED && near(k) && !hurtBetween(k - KNOCK_TICKS, k));
            run(Kind.STRAFE_RIGHT, k -> k >= 1 && lateral(k) <= -STRAFE_SPEED && near(k) && !hurtBetween(k - KNOCK_TICKS, k));
        }
    }

    private void run(Kind kind, IntPredicate on) {
        int from = -1;
        for (int k = 0; k <= n; k++) {
            boolean now = k < n && on.test(k);
            if (now && from < 0) {
                from = k;
            } else if (!now && from >= 0) {
                if (k - from >= STRAFE_MIN) add(kind, from, k - 1);
                from = -1;
            }
        }
    }
}
