package holorecorder.improve;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dev only: with {@code HOLO_IMPROVE_DEMO=<dir of screen .json files>} the first world joined opens each file's Improve
 * screen, tab by tab, at GUI scales 2 and 4, takes screenshots (run/screenshots/improve-*.png) and quits.
 */
public final class ImproveDemo {
    private static final String DIR = System.getenv("HOLO_IMPROVE_DEMO");
    private static final int[] SCALES = {2, 4};
    private static List<Runnable> steps;
    private static int wait = -1, step;

    private ImproveDemo() {
    }

    public static void tick(Minecraft mc) {
        if (DIR == null || DIR.isEmpty() || mc.level == null || mc.player == null) return;
        if (wait < 0) {
            wait = 100;
            return;
        }
        if (--wait > 0) return;
        try {
            if (steps == null) steps = plan(mc);
            if (step >= steps.size()) {
                mc.stop();
                return;
            }
            steps.get(step++).run();
        } catch (Exception e) {
            e.printStackTrace();
            mc.stop();
        }
    }

    private static List<Runnable> plan(Minecraft mc) throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(DIR))) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        List<Runnable> out = new ArrayList<>();
        for (int scale : SCALES) {
            out.add(() -> {
                mc.options.guiScale().set(scale);
                mc.resizeGui();
                wait = 10;
            });
            for (Path f : files) {
                JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                String name = f.getFileName().toString().replace(".json", "");
                int tabs = root.getAsJsonArray("tabs").size();
                for (int t = 0; t < tabs; t++) {
                    int tab = t;
                    out.add(() -> {
                        root.addProperty("tab", tab);
                        mc.gui.setScreen(new ImproveScreen(ImproveModel.parse(root.toString())));
                        wait = 10;
                    });
                    out.add(() -> shot(mc, name + "-s" + scale + "-t" + tab));
                    if (tab == 0) {
                        out.add(() -> {
                            if (mc.gui.screen() instanceof ImproveScreen s) s.mouseScrolled(s.width / 2.0, s.height / 2.0, 0, -30);
                            wait = 5;
                        });
                        out.add(() -> shot(mc, name + "-s" + scale + "-t0-scrolled"));
                    }
                }
            }
        }
        return out;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "improve-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> {});
        wait = 5;
    }
}
