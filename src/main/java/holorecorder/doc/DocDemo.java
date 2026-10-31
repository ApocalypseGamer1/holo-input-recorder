package holorecorder.doc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dev only: with {@code HOLO_DOC_DEMO=<dir of document .json files>} the first world joined opens each file's document
 * screen at GUI scales 2 and 4, shows its first, second and last page, takes screenshots (run/screenshots/doc-*.png) and quits.
 */
public final class DocDemo {
    private static final String DIR = System.getenv("HOLO_DOC_DEMO");
    private static final int[] SCALES = {2, 4};
    private static List<Runnable> steps;
    private static int wait = -1, step;

    private DocDemo() {
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
                String json = Files.readString(f);
                String name = f.getFileName().toString().replace(".json", "");
                int pages = DocModel.parse(json).pages().size();
                List<Integer> shown = new ArrayList<>(List.of(0));
                if (pages > 2) shown.add(1);
                if (pages > 1) shown.add(pages - 1);
                for (int pg : shown) {
                    out.add(() -> {
                        DocModel.Doc d = DocModel.parse(json);
                        DocModel.Doc at = new DocModel.Doc(d.title(), d.subtitle(), d.accent(), pg, d.back(), d.close(), d.pages());
                        mc.gui.setScreen(new DocScreen(at));
                        wait = 10;
                    });
                    out.add(() -> shot(mc, name + "-s" + scale + "-p" + pg));
                }
            }
        }
        return out;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "doc-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> {});
        wait = 5;
    }
}
