package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import holorecorder.review.ReviewRecord.Tick;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ReviewViewLagTest {
    /** The bot walks +x at 1 block/tick, back to the start every 20 ticks; the player stands at x=0 and, on each hit, looks where the bot was {@code lag} ticks before. */
    static ReviewRecord walking(int lag, int hits) {
        ReviewRecord r = new ReviewRecord();
        int n = 40 + hits * 20;
        for (int i = 0; i < n; i++) {
            Tick t = new Tick();
            t.tick = i;
            t.self.y = 64;
            t.opp.x = i % 20 - 15;
            t.opp.y = 64;
            t.opp.z = 4;
            r.ticks.add(t);
        }
        for (int h = 0; h < hits; h++) {
            int i = 20 + h * 20;
            Tick t = r.ticks.get(i);
            t.opp.hurt = true;
            double bx = r.ticks.get(i - 1 - lag).opp.x;
            t.self.yaw = (float) Math.toDegrees(Math.atan2(-bx, 4));
            t.self.pitch = 0;
        }
        return r;
    }

    @Test
    void fitsTheLagThatPutsHitsOnTheBot() {
        assertEquals(4, ReviewClient.viewLag(walking(4, 5)));
        assertEquals(2, ReviewClient.viewLag(walking(2, 5)));
        assertEquals(7, ReviewClient.viewLag(walking(7, 5)));
    }

    @Test
    void fewHitsUseTheDefault() {
        assertEquals(ReviewClient.DEFAULT_LAG, ReviewClient.viewLag(walking(1, 2)));
    }

    @Test
    void rayHitsBox() {
        assertTrue(ReviewClient.rayHitsBox(0, 1, 0, 0, 0, 1, -0.3, 0, 2, 0.3, 1.8, 2.6, 6));
        assertTrue(!ReviewClient.rayHitsBox(0, 1, 0, 0, 0, 1, -0.3, 0, 7, 0.3, 1.8, 7.6, 6));
        assertTrue(!ReviewClient.rayHitsBox(0, 1, 0, 1, 0, 0, -0.3, 0, 2, 0.3, 1.8, 2.6, 6));
    }

    /** Real rounds when HOLO_HRV_DIR is set (never committed): prints the fitted lag per round. */
    @Test
    void realRounds() throws Exception {
        String dir = System.getenv("HOLO_HRV_DIR");
        if (dir == null || dir.isEmpty()) return;
        try (Stream<Path> s = Files.list(Path.of(dir))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".hrv")).sorted().toList()) {
                ReviewRecord r = ReviewRecord.fromBytes(Files.readAllBytes(p));
                System.out.println("VIEWLAG " + p.getFileName() + " " + ReviewClient.viewLag(r));
            }
        }
    }
}
