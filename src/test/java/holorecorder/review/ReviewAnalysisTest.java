package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import holorecorder.review.ReviewAnalysis.Kind;
import holorecorder.review.ReviewAnalysis.Mark;
import holorecorder.review.ReviewAnalysis.Suggestion;
import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Tick;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReviewAnalysisTest {
    static final int W = 1, S = 2, A = 4, D = 8, JUMP = 16, SNEAK = 32, SPRINT = 64;
    static final int[] MOVE_KEYS = {0, W, W | A, W | D, A, D, S, S | A, S | D};

    // Opponent 10 blocks away (out of reach), bar flat at 0.5, no coach.
    static ReviewRecord rec(int n) {
        ReviewRecord r = new ReviewRecord();
        r.branches = new int[] {9, 2, 2, 2, 3, 2, 2};
        r.yawBuckets = new float[] {-10f, 0f, 10f};
        r.pitchBuckets = new float[] {-5f, 5f};
        for (int i = 0; i < n; i++) {
            Tick t = new Tick();
            t.tick = i;
            t.self.y = 64;
            t.opp.x = 10;
            t.opp.y = 64;
            t.pSelf = 0.5f;
            r.ticks.add(t);
        }
        return r;
    }

    // One-hot per branch; attackOn is the 0..255 byte for "attack on".
    static byte[] coach(int move, int jump, int sprint, int attackOn, int yaw, int pitch, int sneak) {
        byte[] c = new byte[22];
        c[move] = (byte) 255;
        c[9 + jump] = (byte) 255;
        c[11 + sprint] = (byte) 255;
        c[13] = (byte) (255 - attackOn);
        c[14] = (byte) attackOn;
        c[15 + yaw] = (byte) 255;
        c[18 + pitch] = (byte) 255;
        c[20 + sneak] = (byte) 255;
        return c;
    }

    static void coachAll(ReviewRecord r, byte[] c) {
        for (Tick t : r.ticks) t.coach = c.clone();
    }

    static void keysAll(ReviewRecord r, int keys) {
        for (Tick t : r.ticks) t.keys = keys;
    }

    // Coach says W, nothing else; the player holds W.
    static void agreeing(ReviewRecord r) {
        coachAll(r, coach(1, 0, 0, 0, 1, 0, 0));
        keysAll(r, W);
    }

    static void step(ReviewRecord r, int at, float before, float after) {
        for (int i = 0; i < r.ticks.size(); i++) r.ticks.get(i).pSelf = i < at ? before : after;
    }

    static Mark only(ReviewAnalysis a, Kind k) {
        Mark found = null;
        for (Mark m : a.marks()) {
            if (m.kind() != k) continue;
            assertNull(found, "more than one " + k);
            found = m;
        }
        assertNotNull(found, "no " + k);
        return found;
    }

    @Test
    void flatBarGivesNoMarks() {
        ReviewRecord r = rec(100);
        assertTrue(ReviewAnalysis.of(r).marks().isEmpty());
        coachAll(r, coach(6, 1, 1, 255, 0, 1, 1));
        assertTrue(ReviewAnalysis.of(r).marks().isEmpty());
    }

    @Test
    void sharpDropIsABlunder() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.8f, 0.3f);
        agreeing(r);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.marks().size());
        Mark m = only(a, Kind.BLUNDER);
        assertTrue(m.from() >= 47 && m.from() <= 50, "from " + m.from());
        assertEquals(m.from() + ReviewAnalysis.HORIZON, m.to());
        assertTrue(m.delta() < -0.4);
        assertTrue(m.forced(), "player copied the coach all along");
    }

    @Test
    void moderateDropIsAMistake() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.6f, 0.45f);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.marks().size());
        assertEquals(1, a.count(Kind.MISTAKE));
        assertEquals(0, a.count(Kind.BLUNDER));
    }

    @Test
    void smallDropIsAnInaccuracy() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.6f, 0.53f);
        assertEquals(1, ReviewAnalysis.of(r).count(Kind.INACCURACY));
    }

    @Test
    void dropBelowThresholdIsIgnored() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.6f, 0.56f);
        assertTrue(ReviewAnalysis.of(r).marks().isEmpty());
    }

    @Test
    void twoFarApartDropsBothMark() {
        ReviewRecord r = rec(160);
        for (int i = 0; i < 160; i++) r.ticks.get(i).pSelf = i < 40 ? 0.8f : i < 100 ? 0.5f : 0.2f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(2, a.count(Kind.BLUNDER));
        assertEquals(2, a.marks().size());
        assertTrue(a.marks().get(0).index() < a.marks().get(1).index());
    }

    @Test
    void dropIndexIsTheLeastAgreedTick() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.8f, 0.3f);
        agreeing(r);
        r.ticks.get(55).coach = coach(6, 0, 0, 0, 1, 0, 0);
        Mark m = only(ReviewAnalysis.of(r), Kind.BLUNDER);
        assertEquals(55, m.index());
        assertTrue(m.from() <= 55 && 55 <= m.to());
    }

    @Test
    void disagreementIsNotForced() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.8f, 0.3f);
        coachAll(r, coach(6, 0, 0, 255, 1, 0, 0));
        assertFalse(only(ReviewAnalysis.of(r), Kind.BLUNDER).forced());
    }

    @Test
    void dropWithoutCoachUsesItsOwnTick() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.8f, 0.3f);
        Mark m = only(ReviewAnalysis.of(r), Kind.BLUNDER);
        assertEquals(m.from(), m.index());
        assertFalse(m.forced());
    }

    @Test
    void dropNearTheEndClampsItsWindow() {
        ReviewRecord r = rec(60);
        step(r, 55, 0.8f, 0.3f);
        Mark m = only(ReviewAnalysis.of(r), Kind.BLUNDER);
        assertEquals(59, m.to());
        assertTrue(m.from() >= 0 && m.from() <= m.index() && m.index() <= m.to());
    }

    @Test
    void agreedRiseIsBest() {
        ReviewRecord r = rec(120);
        step(r, 40, 0.3f, 0.6f);
        agreeing(r);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.marks().size());
        Mark m = only(a, Kind.BEST);
        assertTrue(m.from() >= 37 && m.from() <= 40, "from " + m.from());
        assertTrue(m.delta() > 0.25);
    }

    @Test
    void riseWithoutAgreementOrCoachIsNotBest() {
        ReviewRecord r = rec(120);
        step(r, 40, 0.3f, 0.6f);
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.BEST));
        coachAll(r, coach(6, 0, 0, 255, 1, 0, 0));
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.BEST));
    }

    @Test
    void riseJustAfterADropIsNotBest() {
        ReviewRecord r = rec(200);
        for (int i = 0; i < 200; i++) r.ticks.get(i).pSelf = i < 40 ? 0.8f : i < 60 ? 0.3f : 0.9f;
        agreeing(r);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.count(Kind.BLUNDER));
        assertEquals(0, a.count(Kind.BEST));
    }

    @Test
    void shownFollowsTheServerSmoothing() {
        ReviewRecord r = rec(20);
        for (int i = 0; i < 20; i++) r.ticks.get(i).pSelf = i == 0 ? 0f : 1f;
        float[] s = ReviewAnalysis.of(r).shown;
        assertEquals(0f, s[0]);
        assertEquals(1 - Math.exp(-1), s[8], 1e-6);
        assertEquals(1 - Math.exp(-2), s[16], 1e-6);
    }

    @Test
    void shownBeforeFirstValueIsHalfAndNaNKeepsTheLast() {
        ReviewRecord r = rec(6);
        float nan = Float.NaN;
        float[] p = {nan, nan, 0.8f, nan, nan, nan};
        for (int i = 0; i < 6; i++) r.ticks.get(i).pSelf = p[i];
        float[] s = ReviewAnalysis.of(r).shown;
        assertEquals(0.5f, s[0]);
        assertEquals(0.5f, s[1]);
        assertEquals(0.8f, s[2]);
        assertEquals(0.8f, s[5]);
    }

    @Test
    void missWhenCoachWantsAHitAndPlayerDoesNotSwing() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (Tick t : r.ticks) t.opp.x = 2;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.marks().size());
        Mark m = only(a, Kind.MISS);
        assertEquals(20, m.index());
        assertEquals(10, m.from());
        assertEquals(30, m.to());
        assertEquals(0, m.delta());
    }

    @Test
    void missGoesAwayWhenPlayerSwingsOnTheNextTick() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(21).self.swingTime = -1;
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
    }

    @Test
    void swingOutsideTheWindowStillMisses() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(24).self.swingTime = -1;
        assertEquals(1, ReviewAnalysis.of(r).count(Kind.MISS));
        r.ticks.get(24).self.swingTime = ReviewRecord.NOT_SWINGING;
        r.ticks.get(19).self.swingTime = -1;
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
    }

    @Test
    void noMissWhileTheSwordRechargesOrTheBotIsInHurtTime() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(10).self.swingTime = -1;
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
        r.ticks.get(10).self.swingTime = ReviewRecord.NOT_SWINGING;
        r.ticks.get(5).self.swingTime = -1;
        assertEquals(1, ReviewAnalysis.of(r).count(Kind.MISS));
        r.ticks.get(15).opp.hurt = true;
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
    }

    @Test
    void noMissOutOfReach() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
    }

    @Test
    void noMissBelowAttackThreshold() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(20).coach = coach(0, 0, 0, 170, 1, 0, 0);
        for (Tick t : r.ticks) t.opp.x = 2;
        assertEquals(0, ReviewAnalysis.of(r).count(Kind.MISS));
    }

    @Test
    void missRunsCollapseAndAreSpaced() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        for (Tick t : r.ticks) t.opp.x = 2;
        for (int i = 20; i <= 24; i++) r.ticks.get(i).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (int i = 35; i <= 36; i++) r.ticks.get(i).coach = coach(0, 0, 0, 230, 1, 0, 0);
        for (int i = 60; i <= 61; i++) r.ticks.get(i).coach = coach(0, 0, 0, 230, 1, 0, 0);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(2, a.count(Kind.MISS));
        assertEquals(20, a.marks().get(0).index());
        assertEquals(60, a.marks().get(1).index());
    }

    @Test
    void missInsideADropWindowIsSkipped() {
        ReviewRecord r = rec(120);
        step(r, 50, 0.8f, 0.3f);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(55).coach = coach(0, 0, 0, 230, 1, 0, 0);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1, a.count(Kind.BLUNDER));
        assertEquals(0, a.count(Kind.MISS));
    }

    @Test
    void marksAreSortedByTick() {
        ReviewRecord r = rec(200);
        step(r, 120, 0.8f, 0.3f);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(20).coach = coach(0, 0, 0, 230, 1, 0, 0);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(2, a.marks().size());
        assertEquals(Kind.MISS, a.marks().get(0).kind());
        assertEquals(Kind.BLUNDER, a.marks().get(1).kind());
    }

    @Test
    void agreementIsOneWhenThePlayerCopiesEveryArgmax() {
        int n = 60;
        ReviewRecord r = rec(n);
        for (int t = 0; t < n; t++) {
            int move = (t * 7) % 9, jump = t % 2, sprint = (t / 2) % 2, atk = t % 3 == 0 ? 1 : 0;
            int yaw = t % 3, pitch = t % 2, sneak = (t / 3) % 2;
            r.ticks.get(t).coach = coach(move, jump, sprint, atk * 255, yaw, pitch, sneak);
            if (t + 1 < n) {
                Tick cur = r.ticks.get(t), next = r.ticks.get(t + 1);
                next.keys = MOVE_KEYS[move] | (jump == 1 ? JUMP : 0) | (sprint == 1 ? SPRINT : 0) | (sneak == 1 ? SNEAK : 0);
                next.self.swingTime = atk == 1 ? (byte) -1 : ReviewRecord.NOT_SWINGING;
                next.self.yaw = cur.self.yaw + r.yawBuckets[yaw];
                next.self.pitch = cur.self.pitch + r.pitchBuckets[pitch];
            }
        }
        ReviewAnalysis a = ReviewAnalysis.of(r);
        for (int i = 0; i < n - 1; i++) {
            assertEquals(1.0, a.agreement(i), 1e-12, "tick " + i);
            assertEquals(0.0, a.logp(i), 1e-12, "tick " + i);
        }
        assertTrue(Double.isNaN(a.agreement(n - 1)));
        assertEquals(100.0, a.accuracy(), 1e-9);
    }

    @Test
    void coachAtTSpeaksForTickTPlusOne() {
        ReviewRecord r = rec(10);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(5).coach = coach(1, 0, 0, 0, 1, 0, 0);
        r.ticks.get(6).keys = W;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(1.0, a.agreement(5), 1e-12);
        assertEquals(1.0, a.agreement(4), 1e-12);
        assertEquals(1.0, a.agreement(6), 1e-12);

        r.ticks.get(6).keys = 0;
        r.ticks.get(5).keys = W;
        a = ReviewAnalysis.of(r);
        assertEquals(6.0 / 7.0, a.agreement(5), 1e-12);
        assertEquals(6.0 / 7.0, a.agreement(4), 1e-12);
    }

    @Test
    void wrongChoiceCostsLogProbability() {
        ReviewRecord r = rec(4);
        coachAll(r, coach(1, 0, 0, 0, 1, 0, 0));
        keysAll(r, 0);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(6.0 / 7.0, a.agreement(0), 1e-12);
        assertEquals(Math.log(1.0 / 255.0), a.logp(0), 1e-12);
    }

    @Test
    void humanYawAndPitchSnapToTheNearestBucket() {
        ReviewRecord r = rec(3);
        coachAll(r, coach(0, 0, 0, 0, 2, 1, 0));
        r.ticks.get(1).self.yaw = 7f;
        r.ticks.get(1).self.pitch = 2f;
        assertEquals(1.0, ReviewAnalysis.of(r).agreement(0), 1e-12);
        r.ticks.get(1).self.yaw = 3f;
        assertEquals(6.0 / 7.0, ReviewAnalysis.of(r).agreement(0), 1e-12);
    }

    @Test
    void yawDeltaWrapsAroundTheSeam() {
        ReviewRecord r = rec(3);
        coachAll(r, coach(0, 0, 0, 0, 2, 0, 0));
        r.ticks.get(0).self.yaw = 175f;
        r.ticks.get(1).self.yaw = -175f;
        assertEquals(1.0, ReviewAnalysis.of(r).agreement(0), 1e-12);
    }

    @Test
    void conflictingKeysCancelOut() {
        ReviewRecord r = rec(3);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        r.ticks.get(1).keys = W | S | A | D;
        assertEquals(1.0, ReviewAnalysis.of(r).agreement(0), 1e-12);
    }

    @Test
    void suggestionReadsTheArgmax() {
        ReviewRecord r = rec(3);
        r.ticks.get(1).coach = coach(2, 1, 0, 230, 2, 1, 1);
        r.ticks.get(1).self.yaw = 30f;
        r.ticks.get(1).self.pitch = 88f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertNull(a.suggestion(0));
        Suggestion s = a.suggestion(1);
        assertEquals(2, s.move());
        assertTrue(s.forward() && s.left());
        assertFalse(s.back() || s.right());
        assertTrue(s.jump() && s.sneak());
        assertFalse(s.sprint());
        assertEquals(230 / 255.0, s.attackP(), 1e-12);
        assertEquals(10.0, s.yawDeg());
        assertEquals(5.0, s.pitchDeg());
        assertEquals(40.0, s.targetYaw());
        assertEquals(90.0, s.targetPitch());
        assertTrue(Double.isNaN(a.agreement(0)));
    }

    @Test
    void suggestionMoveTableCoversAllNine() {
        boolean[][] wsad = {{false, false, false, false}, {true, false, false, false}, {true, false, true, false},
            {true, false, false, true}, {false, false, true, false}, {false, false, false, true},
            {false, true, false, false}, {false, true, true, false}, {false, true, false, true}};
        ReviewRecord r = rec(10);
        for (int m = 0; m < 9; m++) {
            r.ticks.get(m).coach = coach(m, 0, 0, 0, 1, 0, 0);
        }
        ReviewAnalysis a = ReviewAnalysis.of(r);
        for (int m = 0; m < 9; m++) {
            Suggestion s = a.suggestion(m);
            assertEquals(wsad[m][0], s.forward(), "fwd " + m);
            assertEquals(wsad[m][1], s.back(), "back " + m);
            assertEquals(wsad[m][2], s.left(), "left " + m);
            assertEquals(wsad[m][3], s.right(), "right " + m);
        }
    }

    @Test
    void emptyAndTinyRecordsDoNotCrash() {
        for (int n = 0; n <= 1; n++) {
            ReviewRecord r = rec(n);
            ReviewAnalysis a = ReviewAnalysis.of(r);
            assertEquals(n, a.shown.length);
            assertTrue(a.marks().isEmpty());
            assertTrue(Double.isNaN(a.accuracy()));
            assertEquals(0, a.count(Kind.BLUNDER));
            if (n == 1) {
                assertNull(a.suggestion(0));
                assertTrue(Double.isNaN(a.agreement(0)));
            }
            coachAll(r, coach(1, 0, 0, 255, 1, 0, 0));
            a = ReviewAnalysis.of(r);
            assertTrue(a.marks().isEmpty());
            assertTrue(Double.isNaN(a.accuracy()));
        }
    }

    @Test
    void recordWithoutCoachHasNoAccuracy() {
        ReviewAnalysis a = ReviewAnalysis.of(rec(30));
        assertTrue(Double.isNaN(a.accuracy()));
        assertNull(a.suggestion(3));
    }

    @Test
    void wrapStaysInHalfTurn() {
        assertEquals(-170.0, ReviewAnalysis.wrap(190), 1e-9);
        assertEquals(170.0, ReviewAnalysis.wrap(-190), 1e-9);
        assertEquals(0.0, ReviewAnalysis.wrap(360), 1e-9);
        assertEquals(10.0, ReviewAnalysis.wrap(730), 1e-9);
        assertEquals(180.0, ReviewAnalysis.wrap(180), 1e-9);
        assertEquals(-45.0, ReviewAnalysis.wrap(-45), 1e-9);
    }

    @Test
    void nearestBucketPicksTheClosest() {
        float[] b = {-10f, 0f, 10f};
        assertEquals(0, ReviewAnalysis.nearestBucket(b, -100));
        assertEquals(1, ReviewAnalysis.nearestBucket(b, 4.9));
        assertEquals(2, ReviewAnalysis.nearestBucket(b, 5.1));
        assertEquals(1, ReviewAnalysis.nearestBucket(b, 0));
        assertEquals(0, ReviewAnalysis.nearestBucket(new float[] {-5f, 5f}, 0));
        assertEquals(-1, ReviewAnalysis.nearestBucket(new float[0], 0));
    }

    @Test
    void reachMeasuresEyeToBox() {
        Fighter self = new Fighter(), opp = new Fighter();
        self.y = 64;
        opp.y = 64;
        opp.x = 3.2;
        assertTrue(ReviewAnalysis.inReach(self, opp));
        opp.x = 3.31;
        assertFalse(ReviewAnalysis.inReach(self, opp));
        opp.x = 0;
        opp.y = 70;
        assertFalse(ReviewAnalysis.inReach(self, opp));
        opp.y = 67.5;
        assertTrue(ReviewAnalysis.inReach(self, opp));
    }

    // Everyone looks straight at the bot (10 blocks east, yaw -90), so the aim line stays quiet.
    static void aimed(ReviewRecord r) {
        for (Tick t : r.ticks) t.self.yaw = -90f;
    }

    static ReviewAnalysis dropCase(ReviewRecord r) {
        step(r, 50, 0.8f, 0.3f);
        aimed(r);
        return ReviewAnalysis.of(r);
    }

    static final String NO_CAUSE = "No clear cause: small mistakes added up";

    @Test
    void botHitsLineCountsHitsAndHearts() {
        ReviewRecord r = rec(120);
        for (int i = 0; i < 120; i++) r.ticks.get(i).self.health = i < 45 ? 20 : i < 52 ? 17 : 14;
        r.ticks.get(45).self.hurt = true;
        r.ticks.get(52).self.hurt = true;
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("The bot hit you 2 times (-3.0 hearts)"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void oneHitIsSingularAndNoHealthChangeSkipsHearts() {
        ReviewRecord r = rec(120);
        r.ticks.get(50).self.hurt = true;
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("The bot hit you 1 time"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void youHitLineOnABestMark() {
        ReviewRecord r = rec(120);
        step(r, 40, 0.3f, 0.6f);
        agreeing(r);
        aimed(r);
        for (int i : new int[] {45, 50, 55}) r.ticks.get(i).opp.hurt = true;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(List.of("You hit the bot 3 times"), a.explain(only(a, Kind.BEST)));
    }

    @Test
    void botHitsComeFirstWhenYouLandedNone() {
        ReviewRecord r = rec(120);
        r.ticks.get(50).self.hurt = true;
        r.ticks.get(55).opp.hurt = true;
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("The bot hit you 1 time", "You hit the bot 1 time"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void missedHitsAreCountedAsRuns() {
        ReviewRecord r = rec(60);
        coachAll(r, coach(0, 0, 0, 0, 1, 0, 0));
        aimed(r);
        for (Tick t : r.ticks) t.opp.x = 2;
        for (int i : new int[] {20, 21, 28}) r.ticks.get(i).coach = coach(0, 0, 0, 230, 1, 0, 0);
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(List.of("You missed 2 hits you could have made"), a.explain(only(a, Kind.MISS)));
        r.ticks.get(28).coach = coach(0, 0, 0, 0, 1, 0, 0);
        a = ReviewAnalysis.of(r);
        assertEquals(List.of("You missed 1 hit you could have made"), a.explain(only(a, Kind.MISS)));
    }

    @Test
    void backedOffWhenThePlayerPressesBackAndTheCoachWantsForward() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(1, 0, 0, 0, 1, 0, 0));
        keysAll(r, S);
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("You backed off; the bot would push in"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void stoppedMovingInWhenTheForwardKeyIsReleased() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(1, 0, 0, 0, 1, 0, 0));
        keysAll(r, 0);
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("You stopped moving in"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void sprintLineWhenTheCoachSprintsAndThePlayerDoesNot() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(1, 0, 1, 0, 1, 0, 0));
        keysAll(r, W);
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("You were not sprinting"), a.explain(only(a, Kind.BLUNDER)));
        keysAll(r, W | SPRINT);
        a = ReviewAnalysis.of(r);
        assertEquals(List.of(NO_CAUSE), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void jumpLines() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(1, 0, 0, 0, 1, 0, 0));
        keysAll(r, W | JUMP);
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of("You jumped too much"), a.explain(only(a, Kind.BLUNDER)));
        coachAll(r, coach(1, 1, 0, 0, 1, 0, 0));
        keysAll(r, W);
        a = ReviewAnalysis.of(r);
        assertEquals(List.of("The bot would jump here"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void aimLineWhenLookingFortyDegreesOff() {
        ReviewRecord r = rec(120);
        dropCase(r);
        for (Tick t : r.ticks) t.self.yaw = -50f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(List.of("Your aim was off the bot by 40°"), a.explain(only(a, Kind.BLUNDER)));
        for (Tick t : r.ticks) t.self.yaw = -70f;
        a = ReviewAnalysis.of(r);
        assertEquals(List.of(NO_CAUSE), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void aimUsesTheMinecraftYawConvention() {
        ReviewRecord r = rec(120);
        for (Tick t : r.ticks) {
            t.opp.x = 0;
            t.opp.z = 10;
        }
        step(r, 50, 0.8f, 0.3f);
        for (Tick t : r.ticks) t.self.yaw = 0f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(List.of(NO_CAUSE), a.explain(only(a, Kind.BLUNDER)));
        for (Tick t : r.ticks) t.self.yaw = 180f;
        a = ReviewAnalysis.of(r);
        assertEquals(List.of("Your aim was off the bot by 180°"), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void distanceLineWhenTheGapChanges() {
        ReviewRecord r = rec(120);
        for (int i = 0; i < 120; i++) r.ticks.get(i).opp.x = 3 + 0.05 * i;
        ReviewAnalysis a = dropCase(r);
        Mark m = only(a, Kind.BLUNDER);
        int lo = Math.max(0, m.from() - ReviewAnalysis.EXPLAIN_LEAD);
        String expect = String.format(Locale.ROOT, "Distance went from %.1f to %.1f blocks", 3 + 0.05 * lo, 3 + 0.05 * m.to());
        assertEquals(List.of(expect), a.explain(m));
    }

    @Test
    void fallbackWhenNothingApplies() {
        ReviewRecord r = rec(120);
        ReviewAnalysis a = dropCase(r);
        assertEquals(List.of(NO_CAUSE), a.explain(only(a, Kind.BLUNDER)));
    }

    @Test
    void neverMoreThanFiveShortLines() {
        ReviewRecord r = rec(120);
        coachAll(r, coach(1, 0, 1, 0, 1, 0, 0));
        keysAll(r, S | JUMP);
        for (Tick t : r.ticks) t.opp.x = 2;
        r.ticks.get(40).coach = coach(1, 0, 1, 230, 1, 0, 0);
        r.ticks.get(55).self.hurt = true;
        r.ticks.get(60).opp.hurt = true;
        for (int i = 0; i < 120; i++) r.ticks.get(i).self.health = i < 55 ? 20 : 16;
        ReviewAnalysis a = dropCase(r);
        List<String> lines = a.explain(only(a, Kind.BLUNDER));
        assertEquals(List.of("The bot hit you 1 time (-2.0 hearts)", "You hit the bot 1 time",
                "You missed 1 hit you could have made", "You backed off; the bot would push in", "You were not sprinting"), lines);
        for (String l : lines) assertTrue(l.length() <= 48, l);
    }

    @Test
    void explainSurvivesAnEmptyRecordAndOddMarks() {
        ReviewAnalysis a = ReviewAnalysis.of(rec(0));
        assertEquals(List.of(NO_CAUSE), a.explain(new Mark(Kind.MISS, 3, 0, 5, 0, false)));
        ReviewRecord r = rec(5);
        aimed(r);
        a = ReviewAnalysis.of(r);
        assertEquals(List.of(NO_CAUSE), a.explain(new Mark(Kind.MISS, 3, 0, 40, 0, false)));
    }

    @Test
    void markAtFindsTheWindowAndNullOutside() {
        ReviewRecord r = rec(160);
        for (int i = 0; i < 160; i++) r.ticks.get(i).pSelf = i < 40 ? 0.8f : i < 100 ? 0.5f : 0.2f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        Mark m0 = a.marks().get(0), m1 = a.marks().get(1);
        int lead = ReviewAnalysis.EXPLAIN_LEAD;
        assertEquals(m0, a.markAt(m0.index()));
        assertEquals(m0, a.markAt(m0.to()));
        assertEquals(m0, a.markAt(m0.from() - lead));
        assertEquals(m1, a.markAt(m1.index()));
        assertNull(a.markAt(m0.from() - lead - 1));
        assertTrue(m0.to() + 1 < m1.from() - lead, "gap between the windows");
        assertNull(a.markAt(m0.to() + 1));
        assertNull(a.markAt(m1.to() + 1));
        assertNull(ReviewAnalysis.of(rec(50)).markAt(10));
    }

    @Test
    void markAtPrefersTheNearestIndexWhereWindowsOverlap() {
        ReviewRecord r = rec(200);
        for (int i = 0; i < 200; i++) r.ticks.get(i).pSelf = i < 50 ? 0.9f : i < 80 ? 0.6f : 0.3f;
        ReviewAnalysis a = ReviewAnalysis.of(r);
        assertEquals(2, a.marks().size());
        Mark m0 = a.marks().get(0), m1 = a.marks().get(1);
        int lo1 = Math.max(0, m1.from() - ReviewAnalysis.EXPLAIN_LEAD);
        assertTrue(lo1 <= m0.to(), "windows overlap");
        for (int t = lo1; t <= m0.to(); t++) {
            Mark want = Math.abs(m0.index() - t) <= Math.abs(m1.index() - t) ? m0 : m1;
            assertEquals(want, a.markAt(t), "tick " + t);
        }
        assertEquals(m0, a.markAt(lo1));
        assertEquals(m1, a.markAt(m0.to()));
    }
}
