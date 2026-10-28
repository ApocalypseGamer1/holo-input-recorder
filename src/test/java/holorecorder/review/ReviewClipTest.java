package holorecorder.review;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewClipTest {
    private static ReviewRecord record(int n) {
        ReviewRecord r = new ReviewRecord();
        for (int i = 0; i < n; i++) {
            ReviewRecord.Tick t = new ReviewRecord.Tick();
            t.tick = i;
            t.self.health = t.opp.health = 20;
            t.opp.x = 2;
            r.ticks.add(t);
        }
        return r;
    }

    @AfterEach
    void clear() {
        ReviewClient.reset();
    }

    @Test
    void clipStashesTheRoundAndBackRestoresIt() {
        ReviewRecord round = record(100), clip = record(41);
        ReviewClient.record = round;
        ReviewClient.analysis = ReviewAnalysis.of(round);
        ReviewClient.seek(57.5);
        ReviewClient.playClip(clip, 50);
        assertTrue(ReviewClient.inClip());
        assertFalse(ReviewClient.showingSwordbot, "your play first");
        assertEquals(41, ReviewClient.record.ticks.size());
        assertSame(round.ticks.get(50), ReviewClient.record.ticks.get(0));
        ReviewClient.show(true);
        assertSame(clip, ReviewClient.record);
        assertSame(clip, ReviewClient.analysis.record);
        assertEquals(0, ReviewClient.cursor);
        assertEquals(0.5, ReviewClient.speed);
        assertTrue(ReviewClient.playing);
        assertEquals(50, ReviewClient.clipFrom);
        ReviewClient.endClip();
        assertFalse(ReviewClient.inClip());
        assertSame(round, ReviewClient.record);
        assertSame(round, ReviewClient.analysis.record);
        assertEquals(57.5, ReviewClient.cursor);
        assertFalse(ReviewClient.playing);
    }

    @Test
    void dodgedWhenYouWereHitAndSwordbotWasNot() {
        ReviewRecord round = record(100), clip = record(41);
        round.ticks.get(60).self.hurt = true;
        ReviewClient.record = round;
        ReviewClient.analysis = ReviewAnalysis.of(round);
        ReviewClient.playClip(clip, 50);
        ReviewClient.seek(12);
        assertTrue(ReviewClient.sinceDodged() > 1e8, "no dodge on your own side");
        ReviewClient.show(true);
        ReviewClient.seek(9);
        assertTrue(ReviewClient.sinceDodged() > 1e8);
        ReviewClient.seek(12);
        assertEquals(2, ReviewClient.sinceDodged(), 1e-9);
        clip.ticks.get(11).self.hurt = true;
        assertTrue(ReviewClient.sinceDodged() > 1e8, "hit one tick later is not a dodge");
    }

    @Test
    void tallyCountsHitsAndHeartsPerSide() {
        ReviewRecord round = record(100), clip = record(41);
        round.ticks.get(55).self.hurt = true;
        round.ticks.get(55).self.health = 16;
        for (int i = 56; i < 100; i++) round.ticks.get(i).self.health = 16;
        clip.ticks.get(5).opp.hurt = true;
        clip.ticks.get(20).opp.hurt = true;
        ReviewClient.record = round;
        ReviewClient.analysis = ReviewAnalysis.of(round);
        ReviewClient.playClip(clip, 50);
        assertArrayEquals(new double[] {0, 1, 2}, ReviewClient.tally(false));
        assertArrayEquals(new double[] {2, 0, 0}, ReviewClient.tally(true));
    }

    @Test
    void botSwingsStartAtZeroAndPlayerSwingsAtMinusOne() {
        ReviewRecord r = record(6);
        byte[] bot = {127, 0, 1, 2, 0, 127}, you = {127, -1, 0, 1, 127, -1};
        for (int i = 0; i < 6; i++) {
            r.ticks.get(i).opp.swingTime = bot[i];
            r.ticks.get(i).self.swingTime = you[i];
        }
        boolean[] botStarts = {false, true, false, false, true, false}, youStarts = {false, true, false, false, false, true};
        for (int i = 0; i < 6; i++) {
            assertEquals(botStarts[i], r.swingStart(i, false), "bot " + i);
            assertEquals(youStarts[i], r.swingStart(i, true), "you " + i);
        }
    }

    @Test
    void clipStartsAtTheCursorAwayFromMarks() {
        ReviewRecord round = record(100);
        ReviewClient.record = round;
        ReviewClient.analysis = ReviewAnalysis.of(round);
        ReviewClient.seek(33.7);
        assertEquals(33, ReviewScreen.clipStart(ReviewClient.analysis));
    }
}
