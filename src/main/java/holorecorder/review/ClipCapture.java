package holorecorder.review;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev only: films the guide's fight clip. With {@code HOLO_CLIP_ARENA=<trigger file>} the client (a spectator on the local
 * review test server, watching a duel) waits for that file, then keeps the camera beside the two fighters, hides the HUD
 * and every screen (players named in {@code HOLO_CLIP_SKIP}, comma-separated, are not filmed), and saves a screenshot every other tick (run/screenshots/clip-NNN.png, 150 of them), then quits.
 * Never active in a normal game.
 */
public final class ClipCapture {
    private static final String TRIGGER = System.getenv("HOLO_CLIP_ARENA"), SKIP = System.getenv("HOLO_CLIP_SKIP");
    private static final int SHOTS = 150;
    private static int shot = -1, every;
    private static Vec3 cam;

    private ClipCapture() {
    }

    public static void tick(Minecraft mc) {
        if (TRIGGER == null || TRIGGER.isEmpty() || mc.level == null || mc.player == null) return;
        mc.options.pauseOnLostFocus = false;
        try {
            if (shot < 0) {
                if (!Files.exists(Path.of(TRIGGER))) return;
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                shot = 0;
            }
            if (mc.gui.screen() != null) mc.gui.setScreen(null);
            aim(mc);
            if (shot >= SHOTS) {
                mc.stop();
                return;
            }
            if (every++ % 2 == 0) {
                Screenshot.grab(mc.gameDirectory, String.format("clip-%03d.png", shot++), mc.gameRenderer.mainRenderTarget(), 1, c -> {});
            }
        } catch (Exception e) {
            System.err.println("clip capture failed: " + e);
            mc.stop();
        }
    }

    /** The camera 6 blocks to the side of the two fighters and a little above, looking at the middle; it follows smoothly. */
    private static void aim(Minecraft mc) {
        List<Player> fighters = new ArrayList<>();
        List<String> skip = SKIP == null ? List.of() : List.of(SKIP.split(","));
        for (Player p : mc.level.players()) {
            if (p != mc.player && !p.isSpectator() && !skip.contains(p.getScoreboardName()) && p.distanceTo(mc.player) < 96) fighters.add(p);
        }
        if (fighters.size() < 2) return;
        Vec3 a = fighters.get(0).position(), b = fighters.get(1).position(), mid = a.add(b).scale(0.5);
        Vec3 d = b.subtract(a);
        double len = Math.max(0.01, Math.hypot(d.x, d.z));
        Vec3 side = new Vec3(-d.z / len, 0, d.x / len);
        if (cam != null && cam.subtract(mid).dot(side) < 0) side = side.scale(-1);   // stay on the same side
        Vec3 want = mid.add(side.scale(6)).add(0, 2.2, 0);
        cam = cam == null ? want : cam.lerp(want, 0.12);
        Vec3 look = mid.add(0, 1.1, 0).subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        mc.player.setPos(cam.x, cam.y, cam.z);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
    }
}
