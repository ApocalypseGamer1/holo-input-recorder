package holorecorder.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Whether a launcher or modpack manages this game folder; such an instance must be updated there, never by swapping the jar. */
public enum PackInstance {
    NONE, MODRINTH, PRISM, CURSEFORGE, OTHER_PACK;

    /** File-system checks only. A folder that cannot be read counts as not managed. */
    public static PackInstance detect(Path gameDir) {
        if (gameDir == null) return NONE;
        Path dir = gameDir.toAbsolutePath().normalize();
        Path parent = dir.getParent();
        if (Files.isRegularFile(dir.resolve("modrinth.index.json")) || (parent != null && Files.isRegularFile(parent.resolve("modrinth.index.json")))
                || modrinthAppPath(dir)) return MODRINTH;
        if (parent != null && isPrismName(dir) && managedPrism(parent.resolve("instance.cfg"))) return PRISM;
        if (Files.isRegularFile(dir.resolve("minecraftinstance.json")) || (parent != null && Files.isRegularFile(parent.resolve("minecraftinstance.json")))
                || curseManifest(dir.resolve("manifest.json")) || (parent != null && curseManifest(parent.resolve("manifest.json")))) return CURSEFORGE;
        if (Files.isRegularFile(dir.resolve("pack.toml")) || (parent != null && Files.isRegularFile(parent.resolve("pack.toml")))) return OTHER_PACK;
        return NONE;
    }

    private static boolean modrinthAppPath(Path dir) {
        boolean app = false, profiles = false;
        for (Path part : dir) {
            String s = part.toString().toLowerCase(Locale.ROOT);
            if (s.equals("modrinthapp") || s.equals("com.modrinth.theseus") || s.equals("theseus")) app = true;
            if (s.equals("profiles")) profiles = true;
        }
        return app && profiles;
    }

    private static boolean isPrismName(Path dir) {
        String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
        return n.equals(".minecraft") || n.equals("minecraft");
    }

    private static boolean managedPrism(Path cfg) {
        try {
            if (!Files.isRegularFile(cfg) || Files.size(cfg) > 1 << 20) return false;
            for (String line : Files.readAllLines(cfg)) if (line.strip().equalsIgnoreCase("ManagedPack=true")) return true;
        } catch (IOException | RuntimeException e) {
            // unreadable: not managed
        }
        return false;
    }

    private static boolean curseManifest(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4 << 20) return false;
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return o.has("manifestType") && "minecraftModpack".equals(o.get("manifestType").getAsString());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
