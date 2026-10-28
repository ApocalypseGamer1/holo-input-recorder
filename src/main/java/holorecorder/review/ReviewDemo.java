package holorecorder.review;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev only: with {@code HOLO_REVIEW_DEMO=<file.hrv>} the first world joined plays that review at the player's spot, takes
 * a few screenshots (run/screenshots/review-*.png) and quits. Never active in a normal game.
 */
public final class ReviewDemo {
    private static final String FILE = System.getenv("HOLO_REVIEW_DEMO");
    private static int wait = -1, step;

    private ReviewDemo() {
    }

    public static void tick(Minecraft mc) {
        if (FILE == null || FILE.isEmpty() || mc.level == null || mc.player == null) return;
        if (wait < 0) {
            wait = 100;
            return;
        }
        if (--wait > 0) return;
        try {
            switch (step++) {
                case 0 -> {
                    ReviewRecord r = ReviewRecord.fromBytes(Files.readAllBytes(Path.of(FILE)));
                    ReviewRecord.Fighter first = r.ticks.get(0).self;
                    double dx = mc.player.getX() - first.x, dy = mc.player.getY() - first.y, dz = mc.player.getZ() - first.z;
                    for (ReviewRecord.Tick t : r.ticks) {
                        for (ReviewRecord.Fighter f : new ReviewRecord.Fighter[] {t.self, t.opp}) {
                            f.x += dx;
                            f.y += dy;
                            f.z += dz;
                        }
                    }
                    ReviewClient.startLocal(mc, r);
                    ReviewClient.seek(100);
                    ReviewClient.playing = false;
                    wait = 40;
                }
                case 1 -> shot(mc, "you", 20);
                case 2 -> {
                    ReviewAnalysis.Mark m = ReviewClient.analysis.marks().isEmpty() ? null : ReviewClient.analysis.marks().get(0);
                    ReviewClient.seek(m == null ? 190 : m.index());
                    wait = 20;
                }
                case 3 -> shot(mc, "mark", 5);
                case 4 -> {
                    ReviewClient.coachView = true;
                    wait = 30;
                }
                case 5 -> shot(mc, "botview", 5);
                case 6 -> {
                    ReviewClient.coachView = false;
                    ReviewClient.seek(60);
                    ReviewClient.speed = 1;
                    ReviewClient.playing = true;
                    wait = 50;
                }
                case 7 -> shot(mc, "playing", 40);
                case 8 -> shot(mc, "playing2", 60);
                case 9 -> {
                    ReviewClient.stop(mc, true);
                    wait = 20;
                }
                case 10 -> shot(mc, "after", 10);
                case 11 -> {
                    ReviewRecord r = ReviewRecord.fromBytes(Files.readAllBytes(Path.of(FILE)));
                    ReviewRecord.Fighter first = r.ticks.get(0).self;
                    double dx = mc.player.getX() - first.x, dy = mc.player.getY() - first.y, dz = mc.player.getZ() - first.z;
                    for (ReviewRecord.Tick t : r.ticks) {
                        for (ReviewRecord.Fighter f : new ReviewRecord.Fighter[] {t.self, t.opp}) {
                            f.x += dx;
                            f.y += dy;
                            f.z += dz;
                        }
                    }
                    ReviewClient.startLocal(mc, r);
                    ReviewClient.seek(186.5);
                    ReviewClient.playing = false;
                    wait = 40;
                }
                case 12 -> shot(mc, "gothit", 5);
                case 13 -> {
                    ReviewClient.seek(150.5);
                    wait = 10;
                }
                case 14 -> shot(mc, "scrub", 5);
                case 15 -> {
                    ReviewClient.stop(mc, true);
                    wait = 10;
                }
                default -> mc.stop();
            }
        } catch (Exception e) {
            System.err.println("review demo failed: " + e);
            mc.stop();
        }
    }

    private static void shot(Minecraft mc, String name, int next) {
        Screenshot.grab(mc.gameDirectory, "review-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> {});
        wait = next;
    }
}
