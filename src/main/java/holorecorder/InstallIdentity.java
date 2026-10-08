package holorecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * A per-install random id for abuse prevention. Generated once and stored in the mod's config dir, then sent with the
 * mod report so a server can group accounts coming from the same install (e.g. many accounts farming from one copy of
 * the game). It is a random 128-bit value, NOT derived from the machine, the hardware or the account, and the player
 * can reset it by deleting the file. It is spoofable by design: a soft signal, never identity, and never a hard block
 * on its own. The caller passes the file path (ClientModsReport resolves the Fabric config dir), so this stays free of
 * Minecraft classes and unit-testable.
 */
final class InstallIdentity {
    /** 128 bits as lowercase hex. */
    static final int HEX_LENGTH = 32;
    private static final SecureRandom RNG = new SecureRandom();
    private static volatile String cached;

    private InstallIdentity() {}

    /** The id at {@code file}, creating it on the first call and caching it; "" if it cannot be read or written. */
    static String get(Path file) {
        String known = cached;
        if (known != null) return known;
        String id = loadOrCreate(file);
        cached = id;
        return id;
    }

    /** Reads a valid id from {@code file}, or writes a fresh one next to it atomically; "" on any IO error. */
    static String loadOrCreate(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                String existing = Files.readString(file).trim().toLowerCase(Locale.ROOT);
                if (isValid(existing)) return existing;
            }
            String fresh = fresh();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("install-id.txt.tmp");
            Files.writeString(tmp, fresh + System.lineSeparator());
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return fresh;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** A fresh random id. */
    static String fresh() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Exactly 32 lowercase hex characters. */
    static boolean isValid(String s) {
        if (s == null || s.length() != HEX_LENGTH) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    /** For tests: forget the cached id. */
    static void clearCache() {
        cached = null;
    }
}
