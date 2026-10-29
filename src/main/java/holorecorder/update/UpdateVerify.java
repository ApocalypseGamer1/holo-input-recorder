package holorecorder.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Checks downloaded bytes against the manifest before they are kept. */
final class UpdateVerify {
    static final String MOD_ID = "holo_input_recorder";
    private static final int MAX_MOD_JSON = 64 * 1024;

    private UpdateVerify() {
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Throws IOException with a short reason unless the bytes are exactly the jar the manifest promised. */
    static void check(byte[] jar, UpdateManifest.Build build, String manifestVersion) throws IOException {
        if (jar.length != build.size()) throw new IOException("size differs");
        if (!sha256(jar).equals(build.sha256())) throw new IOException("sha256 differs");
        JsonObject o = modObject(jar);
        if (!MOD_ID.equals(str(o, "id"))) throw new IOException("wrong mod id");
        if (!UpdateVersion.base(str(o, "version")).equals(UpdateVersion.base(manifestVersion))) throw new IOException("wrong mod version");
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static JsonObject modObject(byte[] jar) throws IOException {
        try {
            return JsonParser.parseString(modJson(jar)).getAsJsonObject();
        } catch (IllegalStateException | com.google.gson.JsonParseException e) {
            throw new IOException("fabric.mod.json unreadable");
        }
    }

    /** The version a mod jar file declares, or null when it is not a readable jar of this mod. */
    static String versionOf(java.nio.file.Path file) {
        try {
            if (java.nio.file.Files.size(file) > UpdateManifest.MAX_JAR) return null;
            JsonObject o = modObject(java.nio.file.Files.readAllBytes(file));
            return MOD_ID.equals(str(o, "id")) ? str(o, "version") : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String modJson(byte[] jar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (!e.getName().equals("fabric.mod.json")) continue;
                byte[] data = zip.readNBytes(MAX_MOD_JSON + 1);
                if (data.length > MAX_MOD_JSON) throw new IOException("fabric.mod.json too large");
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (RuntimeException e) {
            throw new IOException("not a jar");
        }
        throw new IOException("no fabric.mod.json");
    }
}
