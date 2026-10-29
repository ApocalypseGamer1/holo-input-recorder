package holorecorder.update;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Puts a verified jar in place. The running jar is locked on Windows and two jars with the same mod id crash Fabric, so there
 * the swap waits for the game to exit (see {@link SwapScript}); on macOS and Linux a rename is safe at once. The state lives in
 * {@code <mods>/.holoplayer-update/}, a hidden folder Fabric does not scan.
 */
final class UpdateSwap {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    static final String DIR = ".holoplayer-update";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}\\.jar");
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9}){1,3}");

    /** A verified jar waiting for the swap. {@code oldJar} is the running mod's own file. */
    record Pending(String version, String file, String sha256, String oldJar) {
        boolean valid() {
            return file != null && NAME.matcher(file).matches() && sha256 != null && SHA.matcher(sha256).matches()
                    && version != null && VERSION.matcher(version).matches() && oldJar != null && !oldJar.isBlank();
        }
    }

    private UpdateSwap() {
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static Path updateDir(Path jarDir) {
        return jarDir.resolve(DIR);
    }

    static void writePending(Path dir, Pending p) throws IOException {
        Files.writeString(dir.resolve("pending.json"), new Gson().toJson(p), StandardCharsets.UTF_8);
    }

    static Pending readPending(Path dir) {
        try {
            Path f = dir.resolve("pending.json");
            if (!Files.isRegularFile(f) || Files.size(f) > 4096) return null;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Pending p = new Pending(str(o, "version"), str(o, "file"), str(o, "sha256"), str(o, "oldJar"));
            return p.valid() ? p : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    static Path verifiedFile(Path dir, Pending p) {
        return dir.resolve(p.file() + ".verified");
    }

    /** Hands a verified jar over: a waiting helper on Windows, an immediate swap elsewhere. Returns false when nothing was scheduled. */
    static boolean install(Path dir, Pending p) {
        if (windows()) return spawnHelper(dir, p);
        return swapNow(dir, p);
    }

    /** Drops what is left of an update that cannot finish (hash differs, jar gone). */
    static void discard(Path dir, Pending p) {
        try {
            Files.deleteIfExists(dir.resolve("pending.json"));
            if (p != null) Files.deleteIfExists(verifiedFile(dir, p));
        } catch (IOException e) {
            LOG.warn("update: could not clean up: {}", e.toString());
        }
    }

    /** Runs on the next start: finishes a swap that was downloaded but never completed. Returns a result line to show, or null. */
    static String resume(Path dir, String runningVersion) {
        String shown = null;
        try {
            Path result = dir.resolve("last-result.txt");
            if (Files.isRegularFile(result) && Files.size(result) < 256) {
                String line = Files.readString(result, StandardCharsets.UTF_8).strip();
                Files.deleteIfExists(result);
                if (line.startsWith("ok ") && UpdateVersion.base(line.substring(3)).equals(UpdateVersion.base(runningVersion))) shown = line.substring(3);
                else if (!line.startsWith("ok ")) LOG.warn("update: the last swap did not finish ({})", line);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("update: could not read the last result: {}", e.toString());
        }
        Pending p = readPending(dir);
        if (p == null) return shown;
        Path jar = verifiedFile(dir, p);
        if (!UpdateVersion.isNewer(p.version(), runningVersion) || !Files.isRegularFile(jar) || !sha256Of(jar).equals(p.sha256())) {
            discard(dir, p);
            return shown;
        }
        if (!helperAlive(dir)) install(dir, p);
        return shown;
    }

    private static String sha256Of(Path file) {
        try {
            if (Files.size(file) > UpdateManifest.MAX_JAR) return "";
            return UpdateVerify.sha256(Files.readAllBytes(file));
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static boolean helperAlive(Path dir) {
        try {
            Path f = dir.resolve("helper.pid");
            if (!Files.isRegularFile(f)) return false;
            long pid = Long.parseLong(Files.readString(f, StandardCharsets.UTF_8).strip());
            return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                    .filter(h -> h.info().command().map(c -> {
                        String s = c.toLowerCase(Locale.ROOT);
                        return s.contains("powershell") || s.contains("pwsh");
                    }).orElse(true)).isPresent();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean spawnHelper(Path dir, Pending p) {
        try {
            if (helperAlive(dir)) return true;
            Path script = dir.resolve("swap.ps1");
            Files.writeString(script, SwapScript.TEXT, StandardCharsets.US_ASCII);
            Path old = Path.of(p.oldJar()), target = dir.getParent().resolve(p.file());
            String root = System.getenv("SystemRoot");
            String exe = root == null ? "powershell.exe" : Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
            ProcessBuilder pb = new ProcessBuilder(SwapScript.command(exe, script, ProcessHandle.current().pid(), old,
                    verifiedFile(dir, p), target, p.sha256(), dir, p.version()));
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("NUL")));
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            Files.writeString(dir.resolve("helper.pid"), Long.toString(process.pid()), StandardCharsets.US_ASCII);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("update: could not start the swap helper: {}", e.toString());
            return false;
        }
    }

    static boolean swapNow(Path dir, Pending p) {
        Path old = Path.of(p.oldJar()), verified = verifiedFile(dir, p), target = dir.getParent().resolve(p.file());
        Path bak = dir.resolve("previous").resolve(old.getFileName() + ".bak");
        boolean moved = false;
        try {
            Files.createDirectories(bak.getParent());
            if (Files.exists(old) && !old.equals(target)) {
                Files.move(old, bak, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            }
            Files.move(verified, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dir.resolve("last-result.txt"), "ok " + p.version(), StandardCharsets.US_ASCII);
            Files.deleteIfExists(dir.resolve("pending.json"));
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.warn("update: swap failed: {}", e.toString());
            if (moved) {
                try {
                    Files.move(bak, old, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // the old jar stays in previous/
                }
            }
            return false;
        }
    }
}
