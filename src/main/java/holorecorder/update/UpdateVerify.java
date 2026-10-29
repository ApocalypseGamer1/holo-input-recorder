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
        JsonObject o;
        try {
            o = JsonParser.parseString(modJson(jar)).getAsJsonObject();
        } catch (IllegalStateException | com.google.gson.JsonParseException e) {
            throw new IOException("fabric.mod.json unreadable");
        }
        if (!o.has("id") || !MOD_ID.equals(o.get("id").getAsString())) throw new IOException("wrong mod id");
        String version = o.has("version") ? o.get("version").getAsString() : "";
        if (!UpdateVersion.base(version).equals(UpdateVersion.base(manifestVersion))) throw new IOException("wrong mod version");
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
