package holorecorder.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Puts a verified jar in place without starting any program. A verified download waits as {@code <file>.new} in the mods folder
 * (a name Fabric ignores). The running jar is renamed to {@code .disabled} and the new one takes its name; if the game holds
 * the running jar open (Windows usually does) the rename fails, nothing changes, and the player finishes by hand.
 */
final class UpdateSwap {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    /** Hidden folder inside mods/ for partial downloads; Fabric does not scan it. */
    static final String DIR = ".holoplayer-update";
    private static final Pattern NEW = Pattern.compile("holo-input-recorder[A-Za-z0-9._+-]*\\.jar\\.new");
    private static final Pattern DISABLED = Pattern.compile("holo-input-recorder[A-Za-z0-9._+-]*\\.jar\\.disabled");

    /** A finished download that was not swapped in: {@code file} is the jar name it should get (without ".new"). */
    record Staged(String version, String file) {
    }

    private UpdateSwap() {
    }

    static Path updateDir(Path modsDir) {
        return modsDir.resolve(DIR);
    }

    /**
     * Renames the running jar aside, then the new file to {@code target}. Any failure leaves things as they were (a second
     * step that fails puts the first back) and is not retried. Returns whether the new jar is now in place.
     */
    static boolean trySwap(Path running, Path staged, Path target) {
        Path aside = running.resolveSibling(running.getFileName() + ".disabled");
        try {
            Files.move(running, aside, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOG.info("update: the running jar cannot be renamed ({}); manual step needed", e.toString());
            return false;
        }
        try {
            Files.move(staged, target);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("update: could not move the new jar in: {}", e.toString());
            try {
                Files.move(aside, running);
            } catch (IOException | RuntimeException undo) {
                LOG.warn("update: could not put the old jar back: {}", undo.toString());
            }
            return false;
        }
    }

    /**
     * Start-up tidy-up, silent and never throwing: removes our leftover {@code .disabled} jars, partial downloads, and any
     * {@code .new} that is not newer than the running version. Returns the newest {@code .new} that is newer, else null.
     */
    static Staged cleanup(Path modsDir, String running) {
        Staged best = null;
        try {
            Path part = updateDir(modsDir);
            if (Files.isDirectory(part)) {
                try (DirectoryStream<Path> s = Files.newDirectoryStream(part, "*.part")) {
                    for (Path p : s) delete(p);
                }
            }
            try (DirectoryStream<Path> s = Files.newDirectoryStream(modsDir)) {
                for (Path p : s) {
                    String name = p.getFileName().toString();
                    if (!Files.isRegularFile(p)) continue;
                    if (DISABLED.matcher(name).matches()) {
                        delete(p);
                    } else if (NEW.matcher(name).matches()) {
                        String version = UpdateVerify.versionOf(p);
                        if (version == null || !UpdateVersion.isNewer(version, running)) {
                            delete(p);
                        } else if (best == null || UpdateVersion.isNewer(version, best.version())) {
                            if (best != null) delete(modsDir.resolve(best.file() + ".new"));
                            best = new Staged(UpdateVersion.base(version), name.substring(0, name.length() - ".new".length()));
                        } else {
                            delete(p);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.info("update: tidy-up skipped: {}", e.toString());
        }
        return best;
    }

    private static void delete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException | RuntimeException e) {
            LOG.info("update: could not delete {}: {}", p.getFileName(), e.toString());
        }
    }
}
