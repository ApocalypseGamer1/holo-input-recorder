package holorecorder.update;

import holorecorder.Tr;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The self-updater's state and work. Everything that touches the network or the disk runs on a daemon thread; the screen only
 * reads the state. The only place a jar can come from is {@link UpdateManifest#BASE}.
 */
public final class UpdateService {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");

    public enum Phase { IDLE, DOWNLOADING, DONE, FAILED }

    /** A newer version exists. {@code build} is null when this Minecraft version has no automatic update. */
    public record Available(String current, String latest, UpdateManifest.Build build, PackInstance pack) {
    }

    private static volatile Available available;
    private static volatile Phase phase = Phase.IDLE;
    private static volatile int percent;
    private static volatile String pendingVersion = "";
    private static final AtomicBoolean busy = new AtomicBoolean();

    private UpdateService() {
    }

    /** Opt out with {@code -Dholo.updateCheck=false}. */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("holo.updateCheck"));
    }

    public static Available available() {
        return available;
    }

    public static Phase phase() {
        return phase;
    }

    public static int percent() {
        return percent;
    }

    public static String ownVersion() {
        return FabricLoader.getInstance().getModContainer(UpdateVerify.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** The running mod's jar, or null when it is not a plain jar file (a development run). */
    static Path ownJar() {
        return FabricLoader.getInstance().getModContainer(UpdateVerify.MOD_ID).flatMap(c -> {
            try {
                if (c.getOrigin().getKind() != ModOrigin.Kind.PATH) return Optional.<Path>empty();
                for (Path p : c.getOrigin().getPaths()) {
                    if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        return Optional.of(p.toAbsolutePath().normalize());
                    }
                }
            } catch (RuntimeException e) {
                // fall through: no jar
            }
            return Optional.<Path>empty();
        }).orElse(null);
    }

    /** Start-up work on a daemon thread: report the last swap, finish a pending one, then ask for the manifest. */
    public static void startup(Minecraft mc, Consumer<String> updatedTo, Consumer<Available> found) {
        if (!enabled()) return;
        Thread.ofPlatform().daemon().name("holo-update-check").start(() -> {
            try {
                Path jar = ownJar();
                if (jar != null) {
                    Path dir = UpdateSwap.updateDir(jar.getParent());
                    if (Files.isDirectory(dir)) {
                        String shown = UpdateSwap.resume(dir, ownVersion());
                        UpdateSwap.Pending p = UpdateSwap.readPending(dir);
                        pendingVersion = p == null ? "" : p.version();
                        if (shown != null) mc.execute(() -> updatedTo.accept(shown));
                    }
                }
                Available a = fetch();
                if (a != null) mc.execute(() -> found.accept(a));
            } catch (IOException | RuntimeException e) {
                LOG.info("update check: {}", e.toString());
            }
        });
    }

    /** Asks the official release page for the manifest on a daemon thread; {@code found} runs on the client thread with the result, if newer. */
    public static void checkAsync(Minecraft mc, Consumer<Available> found) {
        Thread.ofPlatform().daemon().name("holo-update-check").start(() -> {
            try {
                Available a = fetch();
                if (a != null) mc.execute(() -> found.accept(a));
            } catch (IOException | RuntimeException e) {
                LOG.info("update check: {}", e.toString());
            }
        });
    }

    /** Blocking. The newer version, or null when this one is current. */
    static Available fetch() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        UpdateHttp.get(UpdateManifest.MANIFEST_URL, UpdateManifest.MAX_MANIFEST, out, null);
        UpdateManifest m;
        try {
            m = UpdateManifest.parse(out.toString(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException("manifest rejected: " + e.getMessage());
        }
        String running = ownVersion();
        if (!UpdateVersion.isNewer(m.version(), running)) return null;
        String key = UpdateManifest.buildKey(minecraftVersion());
        Available a = new Available(running, m.version(), key == null ? null : m.build(key), PackInstance.detect(FabricLoader.getInstance().getGameDir()));
        if (m.version().equals(pendingVersion)) phase = Phase.DONE;
        available = a;
        return a;
    }

    /** Client thread. Starts the download once; a second call while one runs (or after it finished) does nothing. */
    public static void startDownload(Minecraft mc, Consumer<String> done) {
        Available a = available;
        if (a == null || a.build() == null || a.pack() != PackInstance.NONE || phase == Phase.DONE) return;
        if (!busy.compareAndSet(false, true)) return;
        phase = Phase.DOWNLOADING;
        percent = 0;
        Thread.ofPlatform().daemon().name("holo-update-download").start(() -> {
            try {
                download(a);
                phase = Phase.DONE;
                mc.execute(() -> done.accept(a.latest()));
            } catch (IOException | RuntimeException e) {
                LOG.warn("update: download failed: {}", e.toString());
                phase = Phase.FAILED;
            } finally {
                busy.set(false);
            }
        });
    }

    private static void download(Available a) throws IOException {
        Path jar = ownJar();
        if (jar == null) throw new IOException("not running from a jar file");
        UpdateManifest.Build b = a.build();
        Path dir = UpdateSwap.updateDir(jar.getParent());
        Files.createDirectories(dir);
        Path part = dir.resolve(b.file() + ".part");
        Path verified = dir.resolve(b.file() + ".verified");
        try {
            try (OutputStream out = Files.newOutputStream(part)) {
                UpdateHttp.get(UpdateManifest.jarUrl(b), b.size(), out, n -> percent = (int) Math.min(99, 100L * n / b.size()));
            }
            UpdateVerify.check(Files.readAllBytes(part), b, a.latest());
            Files.move(part, verified, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
        UpdateSwap.Pending pending = new UpdateSwap.Pending(a.latest(), b.file(), b.sha256(), jar.toString());
        UpdateSwap.writePending(dir, pending);
        pendingVersion = a.latest();
        if (!UpdateSwap.install(dir, pending)) throw new IOException("could not schedule the swap");
        percent = 100;
    }

    /** Text for a pack-managed instance, naming the launcher the player uses. */
    public static String packMessage(PackInstance pack) {
        return switch (pack) {
            case MODRINTH -> Tr.t("This instance is managed by a modpack. Update the Holoplayer pack in the Modrinth app: open the app, open your Holoplayer instance, click Update, then start the game again.");
            case PRISM -> Tr.t("This instance is managed by a modpack. Update the Holoplayer pack in Prism Launcher: open your Holoplayer instance, update the pack, then start the game again.");
            case CURSEFORGE -> Tr.t("This instance is managed by a modpack. Update the Holoplayer pack in the CurseForge app: open your Holoplayer profile, click Update, then start the game again.");
            default -> Tr.t("This instance is managed by a modpack. Update the Holoplayer pack in the launcher you installed it with, then start the game again.");
        };
    }
}
