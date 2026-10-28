package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import holorecorder.review.ReviewMoves.Kind;
import holorecorder.review.ReviewMoves.Move;
import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Tick;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewMovesTest {
    static final int W = 1, S = 2, A = 4, D = 8, JUMP = 16, SPRINT = 64;
    static final int HIT = 20;

    // Self at the origin, opponent 2 blocks east, both standing on the ground.
    static ReviewRecord rec(int n) {
        ReviewRecord r = new ReviewRecord();
        for (int i = 0; i < n; i++) {
            Tick t = new Tick();
            t.tick = i;
            t.self.y = 64;
            t.self.onGround = true;
            t.opp.x = 2;
            t.opp.y = 64;
            t.opp.onGround = true;
            r.ticks.add(t);
        }
        return r;
    }

    static Fighter act(ReviewRecord r, int i, boolean self) {
        return self ? r.ticks.get(i).self : r.ticks.get(i).opp;
    }

    static Fighter vic(ReviewRecord r, int i, boolean self) {
        return self ? r.ticks.get(i).opp : r.ticks.get(i).self;
    }

    static void swing(ReviewRecord r, boolean self, int at) {
        act(r, at, self).swingTime = -1;
    }

    static void hit(ReviewRecord r, boolean self, int s) {
        swing(r, self, s);
        vic(r, s, self).hurt = true;
    }

    // Keys pressed during tick k are stored on tick k + 1.
    static void press(ReviewRecord r, int mask, int from, int to) {
        for (int k = from; k <= to; k++) r.ticks.get(k + 1).keys |= mask;
    }

    // Airborne ticks 12..19: a jump from the ground at 12, rising to 15, falling after.
    static void arc(ReviewRecord r, boolean self, boolean falls) {
        double[] y = falls ? new double[] {64.42, 64.75, 64.99, 65.0, 64.9, 64.7, 64.4, 64.0}
                : new double[] {64.42, 64.75, 64.99, 65.2, 65.4, 65.6, 65.8, 66.0};
        for (int i = 0; i < 8; i++) {
            Fighter f = act(r, 12 + i, self);
            f.onGround = false;
            f.y = y[i];
        }
    }

    static boolean has(List<Move> moves, Kind k) {
        for (Move m : moves) if (m.kind() == k) return true;
        return false;
    }

    static boolean has(List<Move> moves, Kind k, int start, int end) {
        for (Move m : moves) if (m.kind() == k && m.start() == start && m.end() == end) return true;
        return false;
    }

    static int count(List<Move> moves, Kind k) {
        int c = 0;
        for (Move m : moves) if (m.kind() == k) c++;
        return c;
    }

    // ---- hit based, both fighters ----

    @Test
    void critIsAChargedFallingHitWithoutSprint() {
        for (boolean self : new boolean[] {true, false}) {
            ReviewRecord r = rec(40);
            arc(r, self, true);
            press(r, JUMP, 12, 12);
            hit(r, self, HIT);
            List<Move> m = ReviewMoves.of(r, self);
            assertTrue(has(m, Kind.CRIT, HIT, HIT), "crit " + self);
            assertTrue(has(m, Kind.JUMP_HIT, HIT, HIT), "jump hit " + self);
            assertFalse(has(m, Kind.SPRINT_HIT) || has(m, Kind.WEAK_HIT));
        }
    }

    @Test
    void hitWhileSprintingIsNotACrit() {
        for (boolean self : new boolean[] {true, false}) {
            ReviewRecord r = rec(40);
            arc(r, self, true);
            press(r, JUMP, 12, 12);
            act(r, HIT - 1, self).sprinting = true;
            hit(r, self, HIT);
            List<Move> m = ReviewMoves.of(r, self);
            assertFalse(has(m, Kind.CRIT), "crit " + self);
            assertTrue(has(m, Kind.SPRINT_HIT, HIT, HIT));
            assertTrue(has(m, Kind.JUMP_HIT));
        }
    }

    @Test
    void risingOrGroundedHitsAreNotCrits() {
        for (boolean self : new boolean[] {true, false}) {
            ReviewRecord rising = rec(40);
            arc(rising, self, false);
            press(rising, JUMP, 12, 12);
            hit(rising, self, HIT);
            List<Move> m = ReviewMoves.of(rising, self);
            assertFalse(has(m, Kind.CRIT), "rising " + self);
            assertTrue(has(m, Kind.JUMP_HIT));

            ReviewRecord ground = rec(40);
            hit(ground, self, HIT);
            m = ReviewMoves.of(ground, self);
            assertFalse(has(m, Kind.CRIT) || has(m, Kind.JUMP_HIT) || has(m, Kind.SPRINT_HIT) || has(m, Kind.WEAK_HIT));
        }
    }

    @Test
    void chargeNeedsElevenTicksSinceTheLastSwing() {
        for (boolean self : new boolean[] {true, false}) {
            ReviewRecord r = rec(40);
            arc(r, self, true);
            swing(r, self, HIT - 11);
            hit(r, self, HIT);
            List<Move> m = ReviewMoves.of(r, self);
            assertTrue(has(m, Kind.CRIT), "11 ticks is charged " + self);
            assertFalse(has(m, Kind.WEAK_HIT));

            r = rec(40);
            arc(r, self, true);
            swing(r, self, HIT - 10);
            hit(r, self, HIT);
            m = ReviewMoves.of(r, self);
            assertFalse(has(m, Kind.CRIT), "10 ticks is not " + self);
            assertTrue(has(m, Kind.WEAK_HIT, HIT, HIT));
        }
    }

    @Test
    void weakSprintHitGetsNoSprintKnockback() {
        ReviewRecord r = rec(40);
        r.ticks.get(HIT - 1).self.sprinting = true;
        swing(r, true, HIT - 5);
        hit(r, true, HIT);
        List<Move> m = ReviewMoves.of(r, true);
        assertTrue(has(m, Kind.WEAK_HIT, HIT, HIT));
        assertFalse(has(m, Kind.SPRINT_HIT));
        r.ticks.get(HIT - 5).self.swingTime = ReviewRecord.NOT_SWINGING;
        assertTrue(has(ReviewMoves.of(r, true), Kind.SPRINT_HIT, HIT, HIT));
    }

    @Test
    void hitNeedsTheVictimHurtAndASwingStart() {
        ReviewRecord r = rec(40);
        r.ticks.get(HIT).self.swingTime = -1;
        assertTrue(ReviewMoves.of(r, true).isEmpty(), "a miss is not a hit");
        r = rec(40);
        r.ticks.get(HIT).opp.hurt = true;
        assertTrue(ReviewMoves.of(r, true).isEmpty(), "damage without a swing is not a hit");
        r = rec(40);
        r.ticks.get(HIT - 1).self.swingTime = -1;
        r.ticks.get(HIT).opp.hurt = true;
        r.ticks.get(HIT - 2).self.sprinting = true;
        assertTrue(has(ReviewMoves.of(r, true), Kind.SPRINT_HIT, HIT - 1, HIT - 1), "hurt lands a tick after the swing");
    }

    @Test
    void jumpHitNeedsAJumpNotAFall() {
        ReviewRecord r = rec(40);
        arc(r, true, true);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.JUMP_HIT), "no jump key: walked off an edge");
        press(r, JUMP, 14, 14);
        assertTrue(has(ReviewMoves.of(r, true), Kind.JUMP_HIT));

        r = rec(40);
        arc(r, false, true);
        r.ticks.get(12).opp.hurt = true;
        hit(r, false, HIT);
        assertFalse(has(ReviewMoves.of(r, false), Kind.JUMP_HIT), "knockback lift is not a jump");
    }

    // ---- self only ----

    @Test
    void jumpResetIsAJumpRightAfterBeingHitOnTheGround() {
        ReviewRecord r = rec(60);
        r.ticks.get(30).self.hurt = true;
        press(r, JUMP, 31, 32);
        assertTrue(has(ReviewMoves.of(r, true), Kind.JUMP_RESET, 30, 31));

        r = rec(60);
        r.ticks.get(30).self.hurt = true;
        press(r, JUMP, 30, 30);
        assertTrue(has(ReviewMoves.of(r, true), Kind.JUMP_RESET, 30, 30));
    }

    @Test
    void jumpResetNegatives() {
        ReviewRecord r = rec(60);
        r.ticks.get(30).self.hurt = true;
        press(r, JUMP, 33, 33);
        assertFalse(has(ReviewMoves.of(r, true), Kind.JUMP_RESET), "three ticks late");

        r = rec(60);
        r.ticks.get(30).self.hurt = true;
        r.ticks.get(29).self.onGround = false;
        press(r, JUMP, 30, 31);
        assertFalse(has(ReviewMoves.of(r, true), Kind.JUMP_RESET), "was in the air");

        r = rec(60);
        r.ticks.get(30).self.hurt = true;
        r.ticks.get(30).opp.hurt = true;
        r.ticks.get(30).opp.swingTime = -1;
        press(r, JUMP, 30, 31);
        assertFalse(has(ReviewMoves.of(r, false), Kind.JUMP_RESET), "opponent has no keys");
        assertTrue(Kind.JUMP_RESET.selfOnly());
        assertFalse(Kind.W_TAP.selfOnly());
    }

    @Test
    void wTapIsAShortReleaseRightAfterTheHit() {
        ReviewRecord r = rec(60);
        press(r, W, 5, 20);
        press(r, W, 23, 30);
        hit(r, true, HIT);
        assertTrue(has(ReviewMoves.of(r, true), Kind.W_TAP, HIT, 23));

        r = rec(60);
        press(r, W, 5, 19);
        press(r, W, 21, 30);
        hit(r, true, HIT);
        assertTrue(has(ReviewMoves.of(r, true), Kind.W_TAP, HIT, 21), "released on the hit tick");
    }

    @Test
    void wTapMayStartOneTickBeforeTheHit() {
        ReviewRecord r = rec(60);
        press(r, W, 5, 18);
        press(r, W, 21, 30);
        hit(r, true, HIT);
        assertTrue(has(ReviewMoves.of(r, true), Kind.W_TAP, HIT, 21));
    }

    @Test
    void sprintKeyTapCountsAsAWTap() {
        ReviewRecord r = rec(60);
        press(r, W, 5, 30);
        press(r, SPRINT, 5, 20);
        press(r, SPRINT, 23, 30);
        hit(r, true, HIT);
        assertTrue(has(ReviewMoves.of(r, true), Kind.W_TAP, HIT, 23));
    }

    @Test
    void wTapNegatives() {
        ReviewRecord r = rec(60);
        press(r, W, 5, 20);
        press(r, W, 25, 30);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "released four ticks");

        r = rec(60);
        press(r, W, 5, 30);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "never released");

        r = rec(60);
        press(r, W, 5, 22);
        press(r, W, 24, 30);
        hit(r, true, HIT);
        press(r, W, 24, 24);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "release starts three ticks after the hit");

        r = rec(60);
        press(r, W, 5, 15);
        press(r, W, 18, 30);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "released before the hit, back on before it");

        r = rec(60);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "W was never down");

        r = rec(60);
        press(r, W, 5, 20);
        press(r, W, 23, 30);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "no hit");
    }

    @Test
    void wTapNeedsTheKeyToComeBackBeforeTheRecordEnds() {
        ReviewRecord r = rec(23);
        press(r, W, 5, 20);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP));
    }

    @Test
    void sTapIsAShortBackPressRightAfterTheHit() {
        ReviewRecord r = rec(60);
        press(r, S, 21, 22);
        hit(r, true, HIT);
        List<Move> m = ReviewMoves.of(r, true);
        assertTrue(has(m, Kind.S_TAP, HIT, 22));
        assertFalse(has(m, Kind.W_TAP));

        r = rec(60);
        press(r, W, 5, 20);
        press(r, W, 24, 30);
        press(r, S, 21, 23);
        hit(r, true, HIT);
        m = ReviewMoves.of(r, true);
        assertTrue(has(m, Kind.S_TAP, HIT, 23), "S while W is released is an S-tap");
        assertFalse(has(m, Kind.W_TAP));
    }

    @Test
    void sTapNegatives() {
        ReviewRecord r = rec(60);
        press(r, S, 21, 25);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.S_TAP), "five ticks is a retreat");

        r = rec(60);
        press(r, S, 15, 22);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.S_TAP), "S was already down");

        r = rec(60);
        press(r, S, 23, 24);
        hit(r, true, HIT);
        assertFalse(has(ReviewMoves.of(r, true), Kind.S_TAP), "starts three ticks after the hit");
    }

    @Test
    void selfStrafeNeedsFourTicksOfOneSideKeyNearTheOpponent() {
        ReviewRecord r = rec(60);
        press(r, A, 10, 16);
        press(r, D, 30, 33);
        press(r, A, 40, 42);
        press(r, A | D, 50, 56);
        List<Move> m = ReviewMoves.of(r, true);
        assertEquals(1, count(m, Kind.STRAFE_LEFT));
        assertTrue(has(m, Kind.STRAFE_LEFT, 10, 16));
        assertTrue(has(m, Kind.STRAFE_RIGHT, 30, 33));
        assertEquals(1, count(m, Kind.STRAFE_RIGHT));
    }

    @Test
    void strafeNeedsTheOpponentWithinSixBlocks() {
        ReviewRecord r = rec(40);
        for (Tick t : r.ticks) t.opp.x = 8;
        press(r, A, 10, 20);
        assertTrue(ReviewMoves.of(r, true).isEmpty());
    }

    @Test
    void strafeRunSplitsWhereTheKeyIsReleased() {
        ReviewRecord r = rec(40);
        press(r, A, 5, 9);
        press(r, A, 11, 16);
        List<Move> m = ReviewMoves.of(r, true);
        assertTrue(has(m, Kind.STRAFE_LEFT, 5, 9));
        assertTrue(has(m, Kind.STRAFE_LEFT, 11, 16));
    }

    // ---- opponent, inferred ----

    @Test
    void opponentStrafesAreReadFromSidewaysMotion() {
        ReviewRecord r = rec(60);
        for (int k = 10; k < 60; k++) {
            r.ticks.get(k).opp.z = k <= 16 ? 0.1 * (k - 9) : 0.6 - (k >= 30 && k <= 34 ? 0.1 * (k - 29) : k > 34 ? 0.5 : 0);
        }
        List<Move> m = ReviewMoves.of(r, false);
        assertTrue(has(m, Kind.STRAFE_LEFT, 10, 16), m.toString());
        assertTrue(has(m, Kind.STRAFE_RIGHT, 30, 34), m.toString());
    }

    @Test
    void opponentMovingStraightInOrOutIsNotStrafing() {
        ReviewRecord r = rec(60);
        for (int k = 0; k < 60; k++) r.ticks.get(k).opp.x = 4 - 0.05 * k;
        assertTrue(ReviewMoves.of(r, false).isEmpty());
    }

    @Test
    void opponentKnockbackIsNotAStrafe() {
        ReviewRecord r = rec(60);
        r.ticks.get(10).opp.hurt = true;
        for (int k = 10; k < 14; k++) r.ticks.get(k).opp.z = 0.3 * (k - 9);
        for (int k = 14; k < 60; k++) r.ticks.get(k).opp.z = 1.2;
        assertEquals(0, count(ReviewMoves.of(r, false), Kind.STRAFE_LEFT));
    }

    @Test
    void opponentWTapIsTheSprintComingBackFast() {
        ReviewRecord r = rec(60);
        r.ticks.get(HIT - 1).opp.sprinting = true;
        for (int k = HIT + 1; k < 40; k++) r.ticks.get(k).opp.sprinting = true;
        hit(r, false, HIT);
        List<Move> m = ReviewMoves.of(r, false);
        assertTrue(has(m, Kind.SPRINT_HIT, HIT, HIT));
        assertTrue(has(m, Kind.W_TAP, HIT, HIT + 1));
        assertFalse(has(ReviewMoves.of(r, true), Kind.W_TAP), "self W-taps come from keys");
    }

    @Test
    void opponentWTapNegatives() {
        ReviewRecord r = rec(60);
        r.ticks.get(HIT - 1).opp.sprinting = true;
        for (int k = HIT + 7; k < 40; k++) r.ticks.get(k).opp.sprinting = true;
        hit(r, false, HIT);
        assertFalse(has(ReviewMoves.of(r, false), Kind.W_TAP), "sprint back after seven ticks");

        r = rec(60);
        for (int k = HIT - 1; k < 40; k++) r.ticks.get(k).opp.sprinting = true;
        hit(r, false, HIT);
        assertFalse(has(ReviewMoves.of(r, false), Kind.W_TAP), "sprint never dropped");

        r = rec(60);
        r.ticks.get(HIT - 1).opp.sprinting = true;
        for (int k = HIT + 1; k < 40; k++) r.ticks.get(k).opp.sprinting = true;
        swing(r, false, HIT - 5);
        hit(r, false, HIT);
        assertFalse(has(ReviewMoves.of(r, false), Kind.W_TAP), "uncharged hit does not stop the sprint");
    }

    // Opponent stands at x = 2 and backs away east for ticks from..to, then holds still.
    static ReviewRecord backOff(int from, int to) {
        ReviewRecord r = rec(60);
        for (int k = from; k < 60; k++) r.ticks.get(k).opp.x = 2 + 0.1 * (Math.min(k, to) - from + 1);
        hit(r, false, HIT);
        return r;
    }

    @Test
    void opponentSTapIsABriefStepAwayAfterTheHit() {
        List<Move> m = ReviewMoves.of(backOff(HIT + 2, HIT + 4), false);
        assertTrue(has(m, Kind.S_TAP, HIT, HIT + 4), m.toString());
    }

    @Test
    void opponentSTapNegatives() {
        assertFalse(has(ReviewMoves.of(backOff(HIT + 2, HIT + 2), false), Kind.S_TAP), "one tick of drift");
        assertFalse(has(ReviewMoves.of(backOff(HIT + 2, HIT + 12), false), Kind.S_TAP), "a long retreat");
        assertFalse(has(ReviewMoves.of(backOff(HIT + 8, HIT + 10), false), Kind.S_TAP), "starts too late");
        ReviewRecord r = backOff(HIT + 2, HIT + 4);
        r.ticks.get(HIT + 3).opp.hurt = true;
        assertFalse(has(ReviewMoves.of(r, false), Kind.S_TAP), "hit meanwhile: that is knockback");
    }

    @Test
    void opponentIgnoresTheKeysOfTheReviewedPlayer() {
        ReviewRecord r = rec(60);
        press(r, W | S | A | D | JUMP | SPRINT, 0, 55);
        r.ticks.get(30).self.hurt = true;
        r.ticks.get(HIT).opp.hurt = true;
        r.ticks.get(HIT).self.swingTime = -1;
        List<Move> m = ReviewMoves.of(r, false);
        assertTrue(m.isEmpty(), m.toString());
    }

    // ---- structure ----

    @Test
    void movesAreSortedAndClamped() {
        ReviewRecord r = rec(60);
        arc(r, true, true);
        press(r, JUMP, 12, 12);
        press(r, A, 2, 8);
        press(r, W, 5, 20);
        press(r, W, 23, 30);
        hit(r, true, HIT);
        List<Move> m = ReviewMoves.of(r, true);
        assertTrue(m.size() >= 4, m.toString());
        for (int i = 0; i < m.size(); i++) {
            assertTrue(m.get(i).start() <= m.get(i).end() && m.get(i).start() >= 0 && m.get(i).end() < 60);
            if (i > 0) assertTrue(m.get(i - 1).start() <= m.get(i).start());
        }
    }

    @Test
    void emptyAndTinyRecordsDoNotCrash() {
        for (int n = 0; n <= 3; n++) {
            assertTrue(ReviewMoves.of(rec(n), true).isEmpty());
            assertTrue(ReviewMoves.of(rec(n), false).isEmpty());
        }
        ReviewRecord r = rec(2);
        hit(r, true, 0);
        hit(r, false, 1);
        ReviewMoves.of(r, true);
        ReviewMoves.of(r, false);
    }

    @Test
    void fixtureRoundRunsClean() throws IOException {
        try (InputStream in = ReviewMovesTest.class.getResourceAsStream("/review/fixture.hrv")) {
            assertNotNull(in, "fixture.hrv missing");
            ReviewRecord r = ReviewRecord.fromBytes(in.readAllBytes());
            for (boolean self : new boolean[] {true, false}) {
                List<Move> m = ReviewMoves.of(r, self);
                for (int i = 0; i < m.size(); i++) {
                    Move x = m.get(i);
                    assertTrue(0 <= x.start() && x.start() <= x.end() && x.end() < r.ticks.size(), x.toString());
                    assertFalse(!self && x.kind().selfOnly());
                    if (i > 0) assertTrue(m.get(i - 1).start() <= x.start());
                }
            }
        }
    }
}
