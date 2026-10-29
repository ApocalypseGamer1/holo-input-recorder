package holorecorder.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The file moves, on a temporary folder. The Windows helper itself is never started here. */
class UpdateSwapTest {
    @TempDir Path mods;

    private static final byte[] NEW = "new jar bytes".getBytes(StandardCharsets.UTF_8);

    private Path dir() throws IOException {
        return Files.createDirectories(UpdateSwap.updateDir(mods));
    }

    private UpdateSwap.Pending stage(Path dir, String oldName) throws IOException {
        Path old = mods.resolve(oldName);
        Files.write(old, "old jar bytes".getBytes(StandardCharsets.UTF_8));
        UpdateSwap.Pending p = new UpdateSwap.Pending("1.13.0", "holo-input-recorder-1.13.0.jar", UpdateVerify.sha256(NEW), old.toString());
        Files.write(UpdateSwap.verifiedFile(dir, p), NEW);
        UpdateSwap.writePending(dir, p);
        return p;
    }

    private List<String> jars() throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jar")).sorted().toList();
        }
    }

    @Test
    void pendingRoundTripsAndRejectsBadValues() throws IOException {
        Path dir = dir();
        UpdateSwap.Pending p = stage(dir, "holo-input-recorder-1.12.0.jar");
        assertEquals(p, UpdateSwap.readPending(dir));
        Files.writeString(dir.resolve("pending.json"), "{\"version\":\"1.13.0\",\"file\":\"../x.jar\",\"sha256\":\"" + "a".repeat(64) + "\",\"oldJar\":\"x\"}");
        assertNull(UpdateSwap.readPending(dir));
        Files.writeString(dir.resolve("pending.json"), "{\"version\":\"1.13.0\",\"file\":\"x.jar\",\"sha256\":\"zz\",\"oldJar\":\"x\"}");
        assertNull(UpdateSwap.readPending(dir));
        Files.writeString(dir.resolve("pending.json"), "garbage");
        assertNull(UpdateSwap.readPending(dir));
    }

    @Test
    void immediateSwapLeavesExactlyOneJarAndAnUndoCopy() throws IOException {
        Path dir = dir();
        UpdateSwap.Pending p = stage(dir, "holo-input-recorder-1.12.0.jar");
        assertTrue(UpdateSwap.swapNow(dir, p));
        assertEquals(List.of("holo-input-recorder-1.13.0.jar"), jars());
        assertArrayEquals(NEW, Files.readAllBytes(mods.resolve("holo-input-recorder-1.13.0.jar")));
        assertTrue(Files.isRegularFile(dir.resolve("previous").resolve("holo-input-recorder-1.12.0.jar.bak")));
        assertFalse(Files.exists(dir.resolve("pending.json")));
        assertEquals("ok 1.13.0", Files.readString(dir.resolve("last-result.txt")));
    }

    @Test
    void theNextLaunchReportsASuccessfulSwapOnce() throws IOException {
        Path dir = dir();
        Files.writeString(dir.resolve("last-result.txt"), "ok 1.13.0");
        assertEquals("1.13.0", UpdateSwap.resume(dir, "1.13.0"));
        assertNull(UpdateSwap.resume(dir, "1.13.0"));
    }

    @Test
    void aFailedResultOrAnOtherVersionShowsNothing() throws IOException {
        Path dir = dir();
        Files.writeString(dir.resolve("last-result.txt"), "fail locked");
        assertNull(UpdateSwap.resume(dir, "1.12.0"));
        Files.writeString(dir.resolve("last-result.txt"), "ok 1.13.0");
        assertNull(UpdateSwap.resume(dir, "1.12.0"));
    }

    @Test
    void aPendingJarThatChangedOrIsAlreadyInstalledIsDropped() throws IOException {
        Path dir = dir();
        UpdateSwap.Pending p = stage(dir, "holo-input-recorder-1.12.0.jar");
        Files.write(UpdateSwap.verifiedFile(dir, p), "tampered".getBytes(StandardCharsets.UTF_8));
        assertNull(UpdateSwap.resume(dir, "1.12.0"));
        assertFalse(Files.exists(dir.resolve("pending.json")));
        assertFalse(Files.exists(UpdateSwap.verifiedFile(dir, p)));
        assertEquals(List.of("holo-input-recorder-1.12.0.jar"), jars());

        UpdateSwap.Pending q = stage(dir, "holo-input-recorder-1.12.0.jar");
        assertNull(UpdateSwap.resume(dir, "1.13.0+mc26.2"));
        assertFalse(Files.exists(UpdateSwap.verifiedFile(dir, q)));
    }

    @Test
    void theHiddenFolderIsNeverScannedAsAMod() {
        assertTrue(UpdateSwap.DIR.startsWith("."));
    }
}
