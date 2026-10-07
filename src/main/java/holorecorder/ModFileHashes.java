package holorecorder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SHA-512 fingerprints of mod files for the data-protection report: the same hash Modrinth indexes, so the server can
 * name a mod from its file without the file ever leaving this computer. Cached by path, size and modification time,
 * so a rejoin hashes nothing new. Blocking: call off the render thread.
 */
final class ModFileHashes {
    /** Lowercase hex of a SHA-512 digest. */
    static final int HEX_LENGTH = 128;
    private static final int BUFFER = 1 << 16;

    private record Key(Path path, long size, long modified) {}

    private static final Map<Key, String> CACHE = new ConcurrentHashMap<>();

    private ModFileHashes() {}

    /** The fingerprint of a regular {@code .jar} file, or "" when it is not one or cannot be read. */
    static String sha512(Path file) {
        if (file == null || !Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return "";
        }
        try {
            Path real = file.toAbsolutePath().normalize();
            Key key = new Key(real, Files.size(real), Files.getLastModifiedTime(real).toMillis());
            String known = CACHE.get(key);
            if (known != null) return known;
            String hex = digest(real);
            if (!hex.isEmpty()) CACHE.put(key, hex);
            return hex;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Streams the file through SHA-512; "" on any read error. */
    static String digest(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            byte[] buffer = new byte[BUFFER];
            for (int n; (n = in.read(buffer)) > 0; ) sha.update(buffer, 0, n);
            return HexFormat.of().formatHex(sha.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** For tests: forget every cached fingerprint. */
    static void clearCache() {
        CACHE.clear();
    }
}
