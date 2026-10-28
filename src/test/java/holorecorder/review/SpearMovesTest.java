package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import holorecorder.review.ReviewAnalysis.Kind;
import holorecorder.review.ReviewAnalysis.Mark;
import holorecorder.review.ReviewRecord.Tick;
import holorecorder.review.SpearMoves.Move;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpearMovesTest {
    static final int SWORD = 1, AXE = 2, MACE = 3, SHIELD = 4, SPEAR = 12;

    // Both fighters 2 blocks apart, standing, spear in hand, no swings.
    static ReviewRecord rec(int n) {
        ReviewRecord r = new ReviewRecord();
        r.mode = "spearmace";
        for (int i = 0; i < n; i++) {
            Tick t = new Tick();
            t.tick = i;
            t.self.y = 64;
            t.opp.x = 2;
            t.opp.y = 64;
            t.pSelf = 0.5f;
            t.selfSpear.main = SPEAR;
            t.oppSpear.main = SPEAR;
            t.selfSpear.totems = t.oppSpear.totems = 4;
            t.selfSpear.pearls = t.oppSpear.pearls = 16;
            t.selfSpear.charges = t.oppSpear.charges = 64;
            t.selfSpear.apples = t.oppSpear.apples = 64;
            r.ticks.add(t);
        }
        return r;
    }

    // Self swings at {@code at} and the opponent is hurt that tick.
    static void selfHit(ReviewRecord r, int at) {
        r.ticks.get(at).self.swingTime = 0;
        r.ticks.get(at).opp.hurt = true;
    }

    static void oppHit(ReviewRecord r, int at) {
        r.ticks.get(at).opp.swingTime = 0;
        r.ticks.get(at).self.hurt = true;
    }

    static Move one(List<Move> moves, SpearMoves.Kind k) {
        Move found = null;
        for (Move m : moves) {
            if (m.kind() != k) continue;
            assertEquals(null, found, "more than one " + k);
            found = m;
        }
        assertTrue(found != null, "no " + k + " in " + moves);
        return found;
    }

    @Test
    void swordRecordsHaveNoSpearMoves() {
        ReviewRecord r = rec(10);
        r.mode = "sword";
        selfHit(r, 5);
        assertTrue(SpearMoves.of(r, true).isEmpty());
    }

    @Test
    void maceAfterAFallIsASmash() {
        ReviewRecord r = rec(10);
        for (Tick t : r.ticks) t.selfSpear.main = MACE;
        r.ticks.get(4).selfSpear.fallDistance = 6f;
        r.ticks.get(5).selfSpear.fallDistance = 0f;
        selfHit(r, 5);
        Move m = one(SpearMoves.of(r, true), SpearMoves.Kind.MACE_SMASH);
        assertEquals(5, m.start());
        assertEquals(6f, m.fall());
        assertEquals("Mace smash", m.name());
        assertEquals("after a 6 block fall", m.detail());
    }

    @Test
    void maceOnTheGroundIsAPlainHit() {
        ReviewRecord r = rec(10);
        for (Tick t : r.ticks) t.selfSpear.main = MACE;
        r.ticks.get(5).selfSpear.fallDistance = 1f;
        selfHit(r, 5);
        one(SpearMoves.of(r, true), SpearMoves.Kind.MACE_HIT);
    }

    @Test
    void spearHitsSplitByChargeAndSpeed() {
        ReviewRecord r = rec(30);
        selfHit(r, 5);
        r.ticks.get(15).selfSpear.use = SPEAR;
        selfHit(r, 15);
        r.ticks.get(25).self.x = 0.8;
        selfHit(r, 25);
        List<Move> moves = SpearMoves.of(r, true);
        assertEquals(5, one(moves, SpearMoves.Kind.SPEAR_JAB).start());
        assertEquals(15, one(moves, SpearMoves.Kind.SPEAR_CHARGE).start());
        assertEquals(25, one(moves, SpearMoves.Kind.SPEAR_LUNGE).start());
    }

    @Test
    void aHitOnTheTickAfterTheSwingCounts() {
        ReviewRecord r = rec(10);
        r.ticks.get(4).self.swingTime = 0;
        r.ticks.get(5).self.swingTime = 1;
        r.ticks.get(5).opp.hurt = true;
        Move m = one(SpearMoves.of(r, true), SpearMoves.Kind.SPEAR_JAB);
        assertEquals(4, m.start());
        assertEquals(5, m.end());
    }

    @Test
    void anItemChangeJustBeforeTheHitIsASwap() {
        ReviewRecord r = rec(10);
        for (int i = 4; i < 10; i++) r.ticks.get(i).selfSpear.main = SWORD;
        selfHit(r, 5);
        Move m = one(SpearMoves.of(r, true), SpearMoves.Kind.SWORD_HIT);
        assertTrue(m.swapped());
        assertEquals("Swap + sword hit", m.name());
    }

    @Test
    void anAxeHitThatDisablesTheShieldIsAShieldBreak() {
        ReviewRecord r = rec(20);
        for (Tick t : r.ticks) t.selfSpear.main = AXE;
        selfHit(r, 5);
        r.ticks.get(6).oppSpear.shieldCooldown = 100;
        selfHit(r, 15);
        List<Move> moves = SpearMoves.of(r, true);
        assertEquals(5, one(moves, SpearMoves.Kind.SHIELD_BREAK).start());
        assertEquals(15, one(moves, SpearMoves.Kind.AXE_HIT).start());
    }

    @Test
    void windChargesLaunchOnlyWhenTheUserRises() {
        ReviewRecord r = rec(40);
        for (int i = 5; i < 40; i++) r.ticks.get(i).selfSpear.charges = 63;
        for (int i = 7; i < 12; i++) r.ticks.get(i).self.y = 66;
        for (int i = 25; i < 40; i++) r.ticks.get(i).selfSpear.charges = 62;
        List<Move> moves = SpearMoves.of(r, true);
        assertEquals(5, one(moves, SpearMoves.Kind.WIND_LAUNCH).start());
        assertEquals(25, one(moves, SpearMoves.Kind.WIND_CHARGE).start());
    }

    @Test
    void aPearlEndsAtTheTeleport() {
        ReviewRecord r = rec(40);
        for (int i = 5; i < 40; i++) r.ticks.get(i).selfSpear.pearls = 15;
        for (int i = 20; i < 40; i++) r.ticks.get(i).self.x = -12;
        Move m = one(SpearMoves.of(r, true), SpearMoves.Kind.PEARL);
        assertEquals(5, m.start());
        assertEquals(20, m.end());
    }

    @Test
    void countDropsAndBuffsAreUses() {
        ReviewRecord r = rec(20);
        for (int i = 3; i < 20; i++) r.ticks.get(i).oppSpear.totems = 3;
        for (int i = 6; i < 20; i++) r.ticks.get(i).oppSpear.apples = 63;
        for (int i = 9; i < 20; i++) r.ticks.get(i).oppSpear.strength = true;
        for (int i = 12; i < 20; i++) r.ticks.get(i).oppSpear.speed = true;
        List<Move> moves = SpearMoves.of(r, false);
        assertEquals(3, one(moves, SpearMoves.Kind.TOTEM_POP).start());
        assertEquals(6, one(moves, SpearMoves.Kind.GAPPLE).start());
        assertEquals(9, one(moves, SpearMoves.Kind.STRENGTH).start());
        assertEquals(12, one(moves, SpearMoves.Kind.SPEED).start());
        assertTrue(SpearMoves.of(r, true).isEmpty());
    }

    @Test
    void glideAndShieldAreSpans() {
        ReviewRecord r = rec(30);
        for (int i = 4; i <= 9; i++) r.ticks.get(i).selfSpear.gliding = true;
        for (int i = 15; i <= 18; i++) r.ticks.get(i).selfSpear.use = SHIELD;
        for (int i = 24; i <= 25; i++) r.ticks.get(i).selfSpear.use = SHIELD;
        List<Move> moves = SpearMoves.of(r, true);
        Move g = one(moves, SpearMoves.Kind.ELYTRA);
        assertEquals(4, g.start());
        assertEquals(9, g.end());
        Move s = one(moves, SpearMoves.Kind.SHIELD_UP);
        assertEquals(15, s.start());
        assertEquals(18, s.end());
    }

    @Test
    void movesComeInTickOrder() {
        ReviewRecord r = rec(30);
        selfHit(r, 20);
        for (int i = 5; i < 30; i++) r.ticks.get(i).selfSpear.apples = 63;
        List<Move> moves = SpearMoves.of(r, true);
        assertEquals(SpearMoves.Kind.GAPPLE, moves.get(0).kind());
        assertEquals(SpearMoves.Kind.SPEAR_JAB, moves.get(1).kind());
    }

    @Test
    void theMistakeBoxNamesSpearCauses() {
        ReviewRecord r = rec(40);
        for (Tick t : r.ticks) t.oppSpear.main = MACE;
        r.ticks.get(19).oppSpear.fallDistance = 8f;
        oppHit(r, 20);
        for (int i = 22; i < 40; i++) r.ticks.get(i).selfSpear.totems = 3;
        List<String> c = ReviewAnalysis.of(r).causes(new Mark(Kind.BLUNDER, 20, 18, 30, -0.4, false), false);
        assertTrue(c.contains("The bot mace-smashed you 1 time (up to 8 blocks)"), c.toString());
        assertTrue(c.contains("You popped a totem"), c.toString());
        assertFalse(c.stream().anyMatch(s -> s.startsWith("Your aim")), c.toString());
    }
}
