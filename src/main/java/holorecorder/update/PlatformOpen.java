package holorecorder.update;

import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Path;

/** Opens a folder or web page with the game's own helper; the only code that differs between the 26.2 and 26.3 builds. */
final class PlatformOpen {
    private PlatformOpen() {
    }

    static void path(Path folder) {
        Util.getPlatform().openPath(folder);
    }

    static void uri(URI uri) {
        Util.getPlatform().openUri(uri);
    }
}
