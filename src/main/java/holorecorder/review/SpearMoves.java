package holorecorder.review;

import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Spear;
import holorecorder.review.ReviewRecord.Tick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Spearmace moves of either fighter, from the per-tick spear state of a format-2 record (server truth, so both sides read the
 * same way, whatever the hotbar layout):
 * <ul>
 *   <li>Hits (a swing start on the tick the other fighter is hurt, or the tick before) by the item in hand: mace smash (fallen
 *       more than {@link #SMASH_FALL} blocks), mace hit, spear charge (spear in use), spear lunge (moving {@link #LUNGE_SPEED}
 *       blocks/tick or more), spear jab, sword hit, axe hit, shield break (an axe hit that puts the other's shield on cooldown).
 *       {@code swapped}: the hand held something else {@link #SWAP_TICKS} ticks before.</li>
 *   <li>Item use read from the counts: wind charge (a launch when the user rises {@link #LAUNCH_RISE} blocks within
 *       {@link #LAUNCH_TICKS} ticks), ender pearl (ends at the teleport), totem pop, golden apple, strength or speed potion.</li>
 *   <li>Elytra glide and shield up (held at least {@link #SHIELD_MIN} ticks), start to end.</li>
 * </ul>
 */
public final class SpearMoves {
    public enum Kind {
        MACE_SMASH, MACE_HIT, SPEAR_CHARGE, SPEAR_LUNGE, SPEAR_JAB, SWORD_HIT, AXE_HIT, SHIELD_BREAK, HIT,
        WIND_LAUNCH, WIND_CHARGE, PEARL, TOTEM_POP, GAPPLE, STRENGTH, SPEED, ELYTRA, SHIELD_UP;

        public boolean hit() {
            return ordinal() <= HIT.ordinal();
        }
    }

    /** Tick indexes into {@code ReviewRecord.ticks}, both inclusive; {@code fall} is the smash height in blocks. */
    public record Move(Kind kind, int start, int end, boolean swapped, float fall) {
        public String name() {
            String n = switch (kind) {
                case MACE_SMASH -> "Mace smash";
                case MACE_HIT -> "Mace hit";
                case SPEAR_CHARGE -> "Spear charge";
                case SPEAR_LUNGE -> "Spear lunge";
                case SPEAR_JAB -> "Spear jab";
                case SWORD_HIT -> "Sword hit";
                case AXE_HIT -> "Axe hit";
                case SHIELD_BREAK -> "Shield break";
                case HIT -> "Hit";
                case WIND_LAUNCH -> "Wind charge launch";
                case WIND_CHARGE -> "Wind charge";
                case PEARL -> "Pearl";
                case TOTEM_POP -> "Totem pop";
                case GAPPLE -> "Golden apple";
                case STRENGTH -> "Strength potion";
                case SPEED -> "Speed potion";
                case ELYTRA -> "Elytra glide";
                case SHIELD_UP -> "Shield up";
            };
            return swapped ? "Swap + " + Character.toLowerCase(n.charAt(0)) + n.substring(1) : n;
        }

        public String detail() {
            return switch (kind) {
                case MACE_SMASH -> String.format(Locale.ROOT, "after a %.0f block fall", fall);
                case SPEAR_CHARGE -> "hit while charging the spear";
                case SPEAR_LUNGE -> "spear hit at speed";
                case SHIELD_BREAK -> "axe hit that disabled the shield";
                case WIND_LAUNCH -> "launched up by a wind charge";
                case PEARL -> "thrown, then the teleport";
                case TOTEM_POP -> "saved from death by a totem";
                default -> swapped ? "switched item just before the hit" : "";
            };
        }
    }

    static final int SWORD = 1, AXE = 2, MACE = 3, SHIELD = 4, CHARGE = 5, SPEAR = 12;
    public static final float SMASH_FALL = 1.5f;
    public static final double LUNGE_SPEED = 0.5, LAUNCH_RISE = 1.5, PEARL_JUMP = 3.0;
    public static final int SWAP_TICKS = 2, LAUNCH_TICKS = 8, PEARL_TICKS = 60, SHIELD_MIN = 3, SHIELD_TICKS = 3;

    private final ReviewRecord r;
    private final boolean self;
    private final int n;
    private final List<Move> out = new ArrayList<>();

    private SpearMoves(ReviewRecord r, boolean self) {
        this.r = r;
        this.self = self;
        this.n = r.ticks.size();
    }

    /** The moves of the reviewed player ({@code self}) or of the opponent, by start tick; empty for a sword record. */
    public static List<Move> of(ReviewRecord r, boolean self) {
        if (!r.spear()) return List.of();
        SpearMoves m = new SpearMoves(r, self);
        m.hits();
        m.uses();
        m.spans();
        m.out.sort(Comparator.comparingInt(Move::start).thenComparing(Move::kind));
        return List.copyOf(m.out);
    }

    private Fighter a(int i) {
        Tick t = r.ticks.get(i);
        return self ? t.self : t.opp;
    }

    private Fighter b(int i) {
        Tick t = r.ticks.get(i);
        return self ? t.opp : t.self;
    }

    private Spear sa(int i) {
        Tick t = r.ticks.get(i);
        return self ? t.selfSpear : t.oppSpear;
    }

    private Spear sb(int i) {
        Tick t = r.ticks.get(i);
        return self ? t.oppSpear : t.selfSpear;
    }

    private void hits() {
        int last = -10;
        for (int t = 0; t < n; t++) {
            if (!b(t).hurt) continue;
            int s = r.swingStart(t, self) ? t : t > 0 && r.swingStart(t - 1, self) ? t - 1 : -1;
            if (s < 0 || s == last) continue;
            last = s;
            Spear at = sa(s);
            int held = at.main != 0 ? at.main : s > 0 ? sa(s - 1).main : 0;
            int before = sa(Math.max(0, s - SWAP_TICKS)).main;
            boolean swapped = held != 0 && before != 0 && before != held;
            float fall = Math.max(at.fallDistance, s > 0 ? sa(s - 1).fallDistance : 0f);
            Kind k;
            if (held == MACE) k = fall > SMASH_FALL ? Kind.MACE_SMASH : Kind.MACE_HIT;
            else if (held == SPEAR) k = at.use == SPEAR ? Kind.SPEAR_CHARGE : speed(s) >= LUNGE_SPEED ? Kind.SPEAR_LUNGE : Kind.SPEAR_JAB;
            else if (held == SWORD) k = Kind.SWORD_HIT;
            else if (held == AXE) k = shieldBroken(s) ? Kind.SHIELD_BREAK : Kind.AXE_HIT;
            else k = Kind.HIT;
            out.add(new Move(k, s, t, swapped, k == Kind.MACE_SMASH ? fall : 0f));
        }
    }

    /** Horizontal blocks moved into tick {@code s}. */
    private double speed(int s) {
        if (s == 0) return 0;
        Fighter p = a(s - 1), q = a(s);
        return Math.hypot(q.x - p.x, q.z - p.z);
    }

    private boolean shieldBroken(int s) {
        for (int j = s; j <= Math.min(n - 1, s + SHIELD_TICKS); j++) {
            if (sb(j).shieldCooldown > 0 && (j == 0 || sb(j - 1).shieldCooldown == 0)) return true;
        }
        return false;
    }

    private void uses() {
        for (int t = 1; t < n; t++) {
            Spear p = sa(t - 1), q = sa(t);
            if (q.charges < p.charges) {
                boolean launch = false;
                for (int j = t; j <= Math.min(n - 1, t + LAUNCH_TICKS); j++) launch |= a(j).y - a(t - 1).y >= LAUNCH_RISE;
                out.add(new Move(launch ? Kind.WIND_LAUNCH : Kind.WIND_CHARGE, t, t, false, 0f));
            }
            if (q.pearls < p.pearls) {
                int end = t;
                for (int j = t + 1; j <= Math.min(n - 1, t + PEARL_TICKS); j++) {
                    if (Math.hypot(a(j).x - a(j - 1).x, a(j).z - a(j - 1).z) + Math.abs(a(j).y - a(j - 1).y) >= PEARL_JUMP) {
                        end = j;
                        break;
                    }
                }
                out.add(new Move(Kind.PEARL, t, end, false, 0f));
            }
            if (q.totems < p.totems) out.add(new Move(Kind.TOTEM_POP, t, t, false, 0f));
            if (q.apples < p.apples) out.add(new Move(Kind.GAPPLE, t, t, false, 0f));
            if (q.strength && !p.strength) out.add(new Move(Kind.STRENGTH, t, t, false, 0f));
            if (q.speed && !p.speed) out.add(new Move(Kind.SPEED, t, t, false, 0f));
        }
    }

    private void spans() {
        int glide = -1, shield = -1;
        for (int t = 0; t <= n; t++) {
            boolean g = t < n && sa(t).gliding, s = t < n && sa(t).use == SHIELD;
            if (g && glide < 0) glide = t;
            if (!g && glide >= 0) {
                out.add(new Move(Kind.ELYTRA, glide, t - 1, false, 0f));
                glide = -1;
            }
            if (s && shield < 0) shield = t;
            if (!s && shield >= 0) {
                if (t - shield >= SHIELD_MIN) out.add(new Move(Kind.SHIELD_UP, shield, t - 1, false, 0f));
                shield = -1;
            }
        }
    }
}
