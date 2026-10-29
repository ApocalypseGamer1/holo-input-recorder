package holorecorder.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The update manifest from the official release page, checked field by field. Nothing here comes from a game server: the
 * jar address is always built from {@link #BASE} plus a tag and file name that passed the checks below.
 */
public final class UpdateManifest {
    public static final String BASE = "https://github.com/ApocalypseGamer1/holo-input-recorder/releases/";
    public static final String MANIFEST_URL = BASE + "latest/download/holoplayer-update.json";
    public static final int MAX_MANIFEST = 64 * 1024;
    public static final int MAX_JAR = 8 * 1024 * 1024;
    /** Minecraft versions that have a build. */
    static final List<String> SUPPORTED = List.of("26.3", "26.2");

    // First character is a letter or digit, so "..", ".x" and "-x" never pass.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9}){1,3}");
    private static final Pattern MC = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?");

    /** One downloadable jar. */
    public record Build(String tag, String file, String sha256, int size) {
    }

    private final String version;
    private final Map<String, Build> builds;

    private UpdateManifest(String version, Map<String, Build> builds) {
        this.version = version;
        this.builds = builds;
    }

    public String version() {
        return version;
    }

    public Build build(String mcKey) {
        return builds.get(mcKey);
    }

    /** Reads and checks a manifest; throws IllegalArgumentException for anything that is not exactly what we expect. */
    public static UpdateManifest parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST) throw new IllegalArgumentException("manifest too large");
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("manifest is not JSON");
        }
        String version = text(root, "version");
        if (!VERSION.matcher(version).matches()) throw new IllegalArgumentException("bad version");
        JsonObject all = object(root, "builds");
        Map<String, Build> out = new LinkedHashMap<>();
        for (String key : SUPPORTED) {
            JsonElement e = all.get(key);
            if (e == null) continue;
            if (!e.isJsonObject()) throw new IllegalArgumentException("bad build " + key);
            JsonObject b = e.getAsJsonObject();
            String tag = name(b, "tag"), file = name(b, "file"), sha = text(b, "sha256");
            if (!file.endsWith(".jar")) throw new IllegalArgumentException("file is not a jar");
            if (!SHA.matcher(sha).matches()) throw new IllegalArgumentException("bad sha256");
            int size;
            try {
                if (!b.get("size").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("bad size");
                size = b.get("size").getAsInt();
            } catch (ClassCastException | IllegalStateException | NumberFormatException | NullPointerException ex) {
                throw new IllegalArgumentException("bad size");
            }
            if (size < 1 || size > MAX_JAR) throw new IllegalArgumentException("bad size");
            out.put(key, new Build(tag, file, sha.toLowerCase(Locale.ROOT), size));
        }
        return new UpdateManifest(version, out);
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("missing " + key);
        return e.getAsJsonObject();
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("missing " + key);
        return e.getAsString();
    }

    private static String name(JsonObject o, String key) {
        String s = text(o, key);
        if (!NAME.matcher(s).matches()) throw new IllegalArgumentException("bad " + key);
        return s;
    }

    /** "26.3" or "26.2" for a running release of that series (26.3.1 too), else null: snapshots and other series get no update. */
    public static String buildKey(String minecraftVersion) {
        if (minecraftVersion == null) return null;
        var m = MC.matcher(minecraftVersion.strip());
        if (!m.matches()) return null;
        String key = m.group(1) + "." + m.group(2);
        return SUPPORTED.contains(key) ? key : null;
    }

    /** The jar address: always the compiled-in base, then the checked tag and file name. */
    public static String jarUrl(Build b) {
        if (b == null || !NAME.matcher(b.tag()).matches() || !NAME.matcher(b.file()).matches()) throw new IllegalArgumentException("bad build");
        return BASE + "download/" + b.tag() + "/" + b.file();
    }

    /** The build to fetch when this manifest has a higher version than {@code running} and a jar for this Minecraft. */
    public Optional<Build> newerFor(String minecraftVersion, String running) {
        String key = buildKey(minecraftVersion);
        if (key == null || !UpdateVersion.isNewer(version, running)) return Optional.empty();
        return Optional.ofNullable(builds.get(key));
    }
}
