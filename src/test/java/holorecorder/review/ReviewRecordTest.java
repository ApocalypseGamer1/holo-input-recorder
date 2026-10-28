package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import holorecorder.review.ReviewRecord.Fighter;
import holorecorder.review.ReviewRecord.Tick;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ReviewRecordTest {
    static ReviewRecord sample() {
        ReviewRecord r = new ReviewRecord();
        r.roundId = "round-1";
        r.arenaId = "arena-3";
        r.startMs = 1_793_000_000_000L;
        r.selfName = "MoboHost";
        r.selfUuid = "d72a40f1-0000-0000-0000-000000000001";
        r.oppName = "Swordbot";
        r.oppUuid = "00000000-0000-0000-0000-000000000002";
        r.oppSkin = "dGV4dHVyZQ==";
        r.oppSkinSig = "c2ln";
        r.outcome = ReviewRecord.OUTCOME_WIN;
        r.coachName = "sword-teacher3.rlw";
        r.coachSha = "15f05d00";
        r.branches = new int[] {9, 2, 2, 2, 3, 2, 2};
        r.yawBuckets = new float[] {-10f, 0f, 10f};
        r.pitchBuckets = new float[] {-5f, 5f};
        r.selfItems = new String[] {"minecraft:diamond_sword", "", "minecraft:diamond_boots", "", "", "minecraft:diamond_helmet"};
        for (int i = 0; i < 3; i++) {
            Tick t = new Tick();
            t.tick = i;
            t.self.x = 100.5 + i * 0.25;
            t.self.y = 64;
            t.self.z = -20.125;
            t.self.yaw = -90f + i;
            t.self.pitch = 3.5f;
            t.self.health = 20f - i;
            t.self.onGround = true;
            t.self.sprinting = i == 1;
            t.self.swingTime = i == 2 ? (byte) -1 : ReviewRecord.NOT_SWINGING;
            t.opp.x = 104.0;
            t.opp.y = 64.5;
            t.opp.z = -20;
            t.opp.yaw = 90f;
            t.opp.hurt = i == 2;
            t.opp.health = 20f - 2 * i;
            t.pSelf = i == 0 ? Float.NaN : 0.25f * i;
            t.keys = i == 1 ? 1 | 64 : 0;
            if (i > 0) {
                t.coach = new byte[r.coachWidth()];
                for (int k = 0; k < t.coach.length; k++) t.coach[k] = (byte) (k * 11 + i);
            }
            r.ticks.add(t);
        }
        return r;
    }

    private static byte[] fixture() throws IOException {
        try (InputStream in = ReviewRecordTest.class.getResourceAsStream("/review/fixture.hrv")) {
            assertNotNull(in, "fixture.hrv missing");
            return in.readAllBytes();
        }
    }

    @Test
    void fixtureReadsAsSample() throws IOException {
        assertSame(sample(), ReviewRecord.fromBytes(fixture()));
    }

    @Test
    void sampleWritesTheFixtureBytes() throws IOException {
        assertArrayEquals(fixture(), sample().toBytes());
    }

    @Test
    void roundTripKeepsEverything() throws IOException {
        ReviewRecord s = sample();
        assertSame(s, ReviewRecord.fromBytes(s.toBytes()));
    }

    @Test
    void badInputIsRejected() {
        assertThrows(IOException.class, () -> ReviewRecord.fromBytes(new byte[] {1, 2, 3}));
    }

    @Test
    void coachProbabilityReadsTheRightBranch() {
        ReviewRecord r = sample();
        Tick t = r.ticks.get(1);
        assertEquals(0, r.coachP(r.ticks.get(0), 0, 0));
        assertEquals((t.coach[9] & 0xFF) / 255.0, r.coachP(t, 1, 0));
        assertEquals((t.coach[9 + 2 + 2 + 2 + 3 + 1] & 0xFF) / 255.0, r.coachP(t, 5, 1));
    }

    private static void assertSame(ReviewRecord e, ReviewRecord a) {
        assertEquals(e.roundId, a.roundId);
        assertEquals(e.mode, a.mode);
        assertEquals(e.arenaId, a.arenaId);
        assertEquals(e.startMs, a.startMs);
        assertEquals(e.selfName, a.selfName);
        assertEquals(e.selfUuid, a.selfUuid);
        assertEquals(e.selfSkin, a.selfSkin);
        assertEquals(e.selfSkinSig, a.selfSkinSig);
        assertEquals(e.oppName, a.oppName);
        assertEquals(e.oppUuid, a.oppUuid);
        assertEquals(e.oppSkin, a.oppSkin);
        assertEquals(e.oppSkinSig, a.oppSkinSig);
        assertEquals(e.outcome, a.outcome);
        assertEquals(e.coachName, a.coachName);
        assertEquals(e.coachSha, a.coachSha);
        assertArrayEquals(e.branches, a.branches);
        assertArrayEquals(e.yawBuckets, a.yawBuckets);
        assertArrayEquals(e.pitchBuckets, a.pitchBuckets);
        assertArrayEquals(e.selfItems, a.selfItems);
        assertArrayEquals(e.oppItems, a.oppItems);
        assertEquals(e.ticks.size(), a.ticks.size());
        for (int i = 0; i < e.ticks.size(); i++) {
            Tick x = e.ticks.get(i), y = a.ticks.get(i);
            assertEquals(x.tick, y.tick, "tick " + i);
            assertFighter(x.self, y.self, "self " + i);
            assertFighter(x.opp, y.opp, "opp " + i);
            assertEquals(x.pSelf, y.pSelf, "pSelf " + i);
            assertEquals(x.keys, y.keys, "keys " + i);
            if (x.coach == null) assertNull(y.coach, "coach " + i);
            else assertArrayEquals(x.coach, y.coach, "coach " + i);
        }
    }

    private static void assertFighter(Fighter e, Fighter a, String what) {
        assertEquals(e.x, a.x, what);
        assertEquals(e.y, a.y, what);
        assertEquals(e.z, a.z, what);
        assertEquals(e.yaw, a.yaw, what);
        assertEquals(e.pitch, a.pitch, what);
        assertEquals(e.health, a.health, what);
        assertEquals(e.onGround, a.onGround, what);
        assertEquals(e.sprinting, a.sprinting, what);
        assertEquals(e.sneaking, a.sneaking, what);
        assertEquals(e.hurt, a.hurt, what);
        assertEquals(e.swingTime, a.swingTime, what);
    }
}
