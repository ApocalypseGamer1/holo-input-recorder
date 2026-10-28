package holorecorder.review;

import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Tick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Chess.com-style review of one {@link ReviewRecord}: the win bar the player saw, what the coach would have played, how
 * close the player came, and the moments worth replaying. No Minecraft types, so it runs in plain unit tests.
 */
public final class ReviewAnalysis {
    public enum Kind { BLUNDER, MISTAKE, INACCURACY, BEST, MISS }

    /** A moment to replay: jump to {@code index}, play {@code from..to}. {@code delta} is the bar change over the window (0 for MISS). */
    public record Mark(Kind kind, int index, int from, int to, double delta, boolean forced) {}

    /** The coach's argmax reply to the state at one tick (it applies to the next tick). */
    public record Suggestion(int move, boolean forward, boolean back, boolean left, boolean right,
                             boolean jump, boolean sprint, boolean sneak, double attackP,
                             double yawDeg, double pitchDeg, double targetYaw, double targetPitch) {}

    public static final int HORIZON = 20, SPACING = 30;
    public static final double DROP_MIN = 0.05, MISTAKE_MIN = 0.10, BLUNDER_MIN = 0.20, RISE_MIN = 0.10;
    public static final double FORCED_AGREEMENT = 0.85, BEST_AGREEMENT = 0.75, MISS_ATTACK_P = 0.7, REACH = 3.0;
    public static final int SWORD_RECHARGE = 12, HURT_TICKS = 9, MISS_SPACING = 40;
    public static final int EXPLAIN_LEAD = 20, EXPLAIN_LINES = 5;

    private static final int MOVE = 0, JUMP = 1, SPRINT = 2, ATTACK = 3, YAW = 4, PITCH = 5, SNEAK = 6, BRANCHES = 7;
    private static final int K_FWD = 1, K_BACK = 2, K_LEFT = 4, K_RIGHT = 8, K_JUMP = 16, K_SNEAK = 32, K_SPRINT = 64;
    private static final double MIN_P = 1.0 / 255.0;

    public final ReviewRecord record;
    /** The bar value the player saw each tick (the server's smoothing of pSelf). */
    public final float[] shown;
    private final int n, width;
    private final Suggestion[] suggestions;
    private final double[] agreement, logp;
    private final List<Mark> marks;

    private ReviewAnalysis(ReviewRecord r) {
        record = r;
        n = r.ticks.size();
        width = r.coachWidth();
        shown = computeShown(r);
        suggestions = new Suggestion[n];
        agreement = new double[n];
        logp = new double[n];
        for (int i = 0; i < n; i++) {
            suggestions[i] = hasCoach(r.ticks.get(i)) ? suggest(r, r.ticks.get(i)) : null;
            agreement[i] = Double.NaN;
            logp[i] = Double.NaN;
        }
        for (int i = 0; i + 1 < n; i++) if (suggestions[i] != null) score(i);
        marks = List.copyOf(computeMarks());
    }

    public static ReviewAnalysis of(ReviewRecord r) {
        return new ReviewAnalysis(r);
    }

    public int length() {
        return n;
    }

    /** Null when the coach did not run at tick {@code i}. */
    public Suggestion suggestion(int i) {
        return suggestions[i];
    }

    /** Mean over the seven branches of p(player's choice) / p(coach's top choice); NaN without coach data or at the last tick. */
    public double agreement(int i) {
        return agreement[i];
    }

    public double logp(int i) {
        return logp[i];
    }

    /** 0..100, NaN when no tick has a value. */
    public double accuracy() {
        double sum = 0;
        int cnt = 0;
        for (double a : agreement) {
            if (Double.isNaN(a)) continue;
            sum += a;
            cnt++;
        }
        return cnt == 0 ? Double.NaN : sum / cnt * 100.0;
    }

    public List<Mark> marks() {
        return marks;
    }

    public int count(Kind k) {
        int c = 0;
        for (Mark m : marks) if (m.kind() == k) c++;
        return c;
    }

    /** The mark whose replay window (from {@link #EXPLAIN_LEAD} ticks before {@code from}, to {@code to}) holds {@code tick}; the nearest index wins. */
    public Mark markAt(int tick) {
        Mark best = null;
        for (Mark m : marks) {
            if (tick < Math.max(0, m.from() - EXPLAIN_LEAD) || tick > m.to()) continue;
            if (best == null || Math.abs(m.index() - tick) < Math.abs(best.index() - tick)) best = m;
        }
        return best;
    }

    /** Up to {@link #EXPLAIN_LINES} short plain-English lines on why the mark happened, most important first. */
    public List<String> explain(Mark m) {
        List<String> out = new ArrayList<>();
        int lo = Math.max(0, m.from() - EXPLAIN_LEAD), hi = Math.min(n - 1, m.to());
        if (lo <= hi) {
            explainHits(out, m, lo, hi);
            int runs = missRuns(lo, hi);
            if (runs > 0) out.add("You missed " + runs + (runs == 1 ? " hit" : " hits") + " you could have made");
            explainMoves(out, lo, hi);
            double aim = meanAimError(lo, hi);
            if (aim > 25.0) out.add("Your aim was off the bot by " + Math.round(aim) + "\u00b0");
            double from = distance(record.ticks.get(lo)), to = distance(record.ticks.get(hi));
            if (Math.abs(to - from) >= 1.5) out.add(String.format(Locale.ROOT, "Distance went from %.1f to %.1f blocks", from, to));
        }
        if (out.isEmpty()) out.add("No clear cause: small mistakes added up");
        return List.copyOf(out.subList(0, Math.min(out.size(), EXPLAIN_LINES)));
    }

    // ---- helpers ----

    /** Degrees into [-180, 180]. */
    public static double wrap(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) d -= 360.0;
        else if (d < -180.0) d += 360.0;
        return d;
    }

    /** Index of the bucket closest to {@code v} (the lower index on a tie), -1 when there are none. */
    public static int nearestBucket(float[] buckets, double v) {
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < buckets.length; i++) {
            double d = Math.abs(buckets[i] - v);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Self eye to the nearest point of the opponent's box (0.6 wide, 1.8 tall) within {@link #REACH}. */
    public static boolean inReach(Fighter self, Fighter opp) {
        double dx = gap(self.x, opp.x - 0.3, opp.x + 0.3);
        double dy = gap(self.y + 1.62, opp.y, opp.y + 1.8);
        double dz = gap(self.z, opp.z - 0.3, opp.z + 0.3);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    private static double gap(double v, double lo, double hi) {
        return v < lo ? lo - v : v > hi ? v - hi : 0;
    }

    private static float[] computeShown(ReviewRecord r) {
        float[] out = new float[r.ticks.size()];
        double k = 1.0 - Math.exp(-1.0 / 8.0);
        double s = 0.5;
        boolean started = false;
        for (int i = 0; i < out.length; i++) {
            float p = r.ticks.get(i).pSelf;
            if (!Float.isNaN(p)) {
                if (!started) {
                    s = p;
                    started = true;
                } else {
                    s += (p - s) * k;
                }
            }
            out[i] = (float) s;
        }
        return out;
    }

    private boolean hasCoach(Tick t) {
        return t.coach != null && t.coach.length == width && record.branches.length == BRANCHES;
    }

    private static int argmax(ReviewRecord r, Tick t, int branch) {
        int best = -1;
        double bestP = -1;
        for (int i = 0; i < r.branches[branch]; i++) {
            double p = r.coachP(t, branch, i);
            if (p > bestP) {
                bestP = p;
                best = i;
            }
        }
        return best;
    }

    private static Suggestion suggest(ReviewRecord r, Tick t) {
        int mv = Math.max(0, argmax(r, t, MOVE));
        boolean fwd = mv >= 1 && mv <= 3, back = mv >= 6;
        boolean left = mv == 2 || mv == 4 || mv == 7, right = mv == 3 || mv == 5 || mv == 8;
        int yi = argmax(r, t, YAW), pi = argmax(r, t, PITCH);
        double yaw = yi >= 0 && yi < r.yawBuckets.length ? r.yawBuckets[yi] : 0;
        double pitch = pi >= 0 && pi < r.pitchBuckets.length ? r.pitchBuckets[pi] : 0;
        return new Suggestion(mv, fwd, back, left, right,
                on(r, t, JUMP), on(r, t, SPRINT), on(r, t, SNEAK), r.coachP(t, ATTACK, 1),
                yaw, pitch, t.self.yaw + yaw, Math.max(-90.0, Math.min(90.0, t.self.pitch + pitch)));
    }

    private static boolean on(ReviewRecord r, Tick t, int branch) {
        return r.coachP(t, branch, 1) >= 0.5;
    }

    // W+S or A+D cancel out.
    private static int humanMove(int keys) {
        boolean w = (keys & K_FWD) != 0, s = (keys & K_BACK) != 0, a = (keys & K_LEFT) != 0, d = (keys & K_RIGHT) != 0;
        int fb = w == s ? 0 : w ? 1 : -1;
        int lr = a == d ? 0 : a ? -1 : 1;
        if (fb == 1) return lr == 0 ? 1 : lr < 0 ? 2 : 3;
        if (fb == 0) return lr == 0 ? 0 : lr < 0 ? 4 : 5;
        return lr == 0 ? 6 : lr < 0 ? 7 : 8;
    }

    // The coach at tick i picks the reply for tick i+1, so the player's choice is read from i+1.
    private void score(int i) {
        ReviewRecord r = record;
        Tick a = r.ticks.get(i), b = r.ticks.get(i + 1);
        int[] choice = {
            humanMove(b.keys),
            (b.keys & K_JUMP) != 0 ? 1 : 0,
            (b.keys & K_SPRINT) != 0 ? 1 : 0,
            b.self.swingTime == -1 ? 1 : 0,
            nearestBucket(r.yawBuckets, wrap(b.self.yaw - a.self.yaw)),
            nearestBucket(r.pitchBuckets, b.self.pitch - a.self.pitch),
            (b.keys & K_SNEAK) != 0 ? 1 : 0};
        double ratioSum = 0, lp = 0;
        for (int br = 0; br < BRANCHES; br++) {
            double max = 0;
            for (int k = 0; k < r.branches[br]; k++) max = Math.max(max, r.coachP(a, br, k));
            double p = choice[br] >= 0 && choice[br] < r.branches[br] ? r.coachP(a, br, choice[br]) : 0;
            ratioSum += max <= 0 ? 1.0 : p / max;
            lp += Math.log(Math.max(p, MIN_P));
        }
        agreement[i] = ratioSum / BRANCHES;
        logp[i] = lp;
    }

    private double windowMean(int from, int to) {
        double sum = 0;
        int cnt = 0;
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(agreement[i])) continue;
            sum += agreement[i];
            cnt++;
        }
        return cnt == 0 ? Double.NaN : sum / cnt;
    }

    private int lowestAgreement(int from, int to, int fallback) {
        int best = fallback;
        double low = Double.POSITIVE_INFINITY;
        for (int i = from; i <= to; i++) {
            if (!Double.isNaN(agreement[i]) && agreement[i] < low) {
                low = agreement[i];
                best = i;
            }
        }
        return best;
    }

    private static void cover(boolean[] used, int center) {
        for (int j = Math.max(0, center - SPACING); j <= Math.min(used.length - 1, center + SPACING); j++) used[j] = true;
    }

    private List<Mark> computeMarks() {
        List<Mark> out = new ArrayList<>();
        if (n < 2) return out;
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = (double) shown[Math.min(i + HORIZON, n - 1)] - shown[i];

        List<Mark> drops = new ArrayList<>();
        boolean[] used = new boolean[n];
        while (true) {
            int at = -1;
            for (int i = 0; i < n; i++) if (!used[i] && (at < 0 || d[i] < d[at])) at = i;
            if (at < 0 || d[at] > -DROP_MIN) break;
            int to = Math.min(at + HORIZON, n - 1);
            double mag = -d[at];
            Kind kind = mag >= BLUNDER_MIN ? Kind.BLUNDER : mag >= MISTAKE_MIN ? Kind.MISTAKE : Kind.INACCURACY;
            double mean = windowMean(at, to);
            drops.add(new Mark(kind, lowestAgreement(at, to, at), at, to, d[at], !Double.isNaN(mean) && mean >= FORCED_AGREEMENT));
            cover(used, at);
        }
        out.addAll(drops);

        // A rise that fails the agreement gate or sits near a drop is still consumed, so its neighbours are not retried.
        used = new boolean[n];
        while (true) {
            int at = -1;
            for (int i = 0; i < n; i++) if (!used[i] && (at < 0 || d[i] > d[at])) at = i;
            if (at < 0 || d[at] < RISE_MIN) break;
            int to = Math.min(at + HORIZON, n - 1);
            double mean = windowMean(at, to);
            boolean nearDrop = false;
            for (Mark m : drops) if (Math.abs(m.from() - at) <= SPACING) nearDrop = true;
            if (!nearDrop && !Double.isNaN(mean) && mean >= BEST_AGREEMENT) out.add(new Mark(Kind.BEST, at, at, to, d[at], false));
            cover(used, at);
        }

        int lastMiss = -1_000_000;
        boolean prev = false;
        for (int i = 0; i < n; i++) {
            boolean q = missCandidate(i);
            if (q && !prev) {
                boolean covered = false;
                for (Mark m : drops) if (i >= m.from() && i <= m.to()) covered = true;
                if (!covered && i - lastMiss >= MISS_SPACING) {
                    out.add(new Mark(Kind.MISS, i, Math.max(0, i - 10), Math.min(n - 1, i + 10), 0, false));
                    lastMiss = i;
                }
            }
            prev = q;
        }

        out.sort(Comparator.comparingInt(Mark::index).thenComparingInt(Mark::from).thenComparing(Mark::kind));
        return out;
    }

    private boolean missCandidate(int i) {
        Suggestion s = suggestions[i];
        if (s == null || s.attackP() < MISS_ATTACK_P) return false;
        Tick t = record.ticks.get(i);
        if (!inReach(t.self, t.opp)) return false;
        // Only a full-strength hit counts: the sword is recharged and the bot is out of its hurt time.
        for (int j = Math.max(0, i - SWORD_RECHARGE); j <= Math.min(n - 1, i + 3); j++) {
            if (record.ticks.get(j).self.swingTime == -1) return false;
        }
        for (int j = Math.max(0, i - HURT_TICKS); j <= i; j++) {
            if (record.ticks.get(j).opp.hurt) return false;
        }
        return true;
    }

    private static String times(int k) {
        return k + (k == 1 ? " time" : " times");
    }

    // Bot hits go first unless the player won the trade on a BEST mark.
    private void explainHits(List<String> out, Mark m, int lo, int hi) {
        int took = 0, landed = 0;
        double lost = 0;
        for (int i = lo; i <= hi; i++) {
            Tick t = record.ticks.get(i);
            if (t.self.hurt) took++;
            if (t.opp.hurt) landed++;
            if (i > 0) lost += Math.max(0, record.ticks.get(i - 1).self.health - t.self.health);
        }
        String bot = took == 0 ? null : "The bot hit you " + times(took)
                + (lost > 0 ? String.format(Locale.ROOT, " (-%.1f hearts)", lost / 2.0) : "");
        String you = landed == 0 ? null : "You hit the bot " + times(landed);
        boolean botFirst = took > 0 && (landed == 0 || m.kind() != Kind.BEST);
        if (botFirst && bot != null) out.add(bot);
        if (you != null) out.add(you);
        if (!botFirst && bot != null) out.add(bot);
    }

    // Runs of consecutive miss candidates, one per run.
    private int missRuns(int lo, int hi) {
        int runs = 0;
        boolean prev = false;
        for (int i = lo; i <= hi; i++) {
            boolean q = missCandidate(i);
            if (q && !prev) runs++;
            prev = q;
        }
        return runs;
    }

    // Player keys are read at i+1 for the coach's reply at i. Shares are of the ticks with coach data, except sprint and "jumped too much".
    private void explainMoves(List<String> out, int lo, int hi) {
        int c = 0, back = 0, noW = 0, wantSprint = 0, noSprint = 0, coachNoJump = 0, jumped = 0, missedJump = 0;
        for (int i = lo; i <= Math.min(hi, n - 2); i++) {
            Suggestion s = suggestions[i];
            if (s == null) continue;
            int keys = record.ticks.get(i + 1).keys;
            c++;
            if (s.forward()) {
                if ((keys & K_BACK) != 0) back++;
                if ((keys & K_FWD) == 0) noW++;
            }
            if (s.sprint()) {
                wantSprint++;
                if ((keys & K_SPRINT) == 0) noSprint++;
            }
            if (s.jump()) {
                if ((keys & K_JUMP) == 0) missedJump++;
            } else {
                coachNoJump++;
                if ((keys & K_JUMP) != 0) jumped++;
            }
        }
        if (c == 0) return;
        if (back >= 0.4 * c) out.add("You backed off; the bot would push in");
        else if (noW >= 0.4 * c) out.add("You stopped moving in");
        if (wantSprint > 0 && noSprint >= 0.5 * wantSprint) out.add("You were not sprinting");
        if (coachNoJump > 0 && jumped >= 0.3 * coachNoJump) out.add("You jumped too much");
        if (missedJump >= 0.3 * c) out.add("The bot would jump here");
    }

    /** Mean degrees between the player's look yaw and the direction to the bot; ticks on top of the bot are skipped. */
    private double meanAimError(int lo, int hi) {
        double sum = 0;
        int cnt = 0;
        for (int i = lo; i <= hi; i++) {
            Tick t = record.ticks.get(i);
            double dx = t.opp.x - t.self.x, dz = t.opp.z - t.self.z;
            if (Math.hypot(dx, dz) < 0.1) continue;
            sum += Math.abs(wrap(t.self.yaw - Math.toDegrees(Math.atan2(-dx, dz))));
            cnt++;
        }
        return cnt == 0 ? 0 : sum / cnt;
    }

    private static double distance(Tick t) {
        return Math.hypot(t.opp.x - t.self.x, t.opp.z - t.self.z);
    }
}
