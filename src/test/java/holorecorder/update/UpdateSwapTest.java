package holorecorder.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** The rename swap and the start-up tidy-up, on a temporary mods folder. Nothing is started and nothing uses the network. */
class UpdateSwapTest {
    @TempDir Path mods;

    private static byte[] jar(String version) throws IOException {
        return UpdateVerifyTest.jar("{\"id\":\"holo_input_recorder\",\"version\":\"" + version + "\"}");
    }

    private Path put(String name, byte[] bytes) throws IOException {
        return Files.write(mods.resolve(name), bytes);
    }

    private List<String> names() throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** Jars Fabric would load: hidden files are skipped. */
    private long modJars() throws IOException {
        try (Stream<Path> s = Files.list(mods)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".jar")).filter(p -> !hidden(p)).filter(p -> UpdateVerify.versionOf(p) != null).count();
        }
    }

    private static boolean hidden(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    @Test
    void swapWithNothingHoldingTheJarLeavesOneModJarAndAnAsideCopy() throws IOException {
        Path old = put("holo-input-recorder-1.12.0.jar", jar("1.12.0"));
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        assertTrue(UpdateSwap.trySwap(old, staged, mods.resolve("holo-input-recorder-1.13.0.jar")));
        assertEquals(List.of("holo-input-recorder-1.12.0.jar.disabled", "holo-input-recorder-1.13.0.jar"), names());
        assertEquals(1, modJars());
        assertEquals("1.13.0", UpdateVerify.versionOf(mods.resolve("holo-input-recorder-1.13.0.jar")));
    }

    @Test
    void swapWhileTheJarIsHeldOpenEndsInEitherCleanState() throws IOException {
        byte[] oldBytes = jar("1.12.0");
        Path old = put("holo-input-recorder-1.12.0.jar", oldBytes);
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        Path target = mods.resolve("holo-input-recorder-1.13.0.jar");
        boolean swapped;
        try (ZipFile held = new ZipFile(old.toFile())) {
            assertNotNull(held.getEntry("fabric.mod.json"));
            swapped = UpdateSwap.trySwap(old, staged, target);
        }
        if (windows()) assertTrue(swapped, "Windows: the locked jar is hidden instead");
        if (swapped) {
            assertEquals(1, modJars());
            assertTrue(Files.exists(target));
            assertFalse(Files.exists(staged));
            assertTrue(Files.exists(mods.resolve("holo-input-recorder-1.12.0.jar.disabled")) || hidden(old));
        } else {
            assertTrue(Files.exists(staged), "the download must stay for the manual step");
            assertArrayEquals(oldBytes, Files.readAllBytes(old));
            assertFalse(Files.exists(target));
            assertFalse(Files.exists(mods.resolve("holo-input-recorder-1.12.0.jar.disabled")));
            assertEquals(1, modJars());
        }
    }

    @Test
    void aFailedSecondStepPutsTheOldJarBack() throws IOException {
        byte[] oldBytes = jar("1.12.0");
        Path old = put("holo-input-recorder-1.12.0.jar", oldBytes);
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        Path target = put("holo-input-recorder-1.13.0.jar", "in the way".getBytes());
        assertFalse(UpdateSwap.trySwap(old, staged, target));
        assertArrayEquals(oldBytes, Files.readAllBytes(old));
        assertTrue(Files.exists(staged));
        assertFalse(Files.exists(mods.resolve("holo-input-recorder-1.12.0.jar.disabled")));
    }

    @Test
    void theNextStartDeletesTheHiddenOldJarAndKeepsTheRunningOne() throws IOException {
        Path old = put("holo-input-recorder-1.12.0.jar", jar("1.12.0"));
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        Path target = mods.resolve("holo-input-recorder-1.13.0.jar");
        try (ZipFile held = new ZipFile(old.toFile())) {
            if (!UpdateSwap.hideAndSwap(old, staged, target)) {
                assertFalse(windows(), "Windows must be able to hide the locked jar");
                return;   // no hidden flag on this file system
            }
        }
        assertEquals(1, modJars());
        assertNull(UpdateSwap.cleanup(mods, "1.13.0", target));
        assertEquals(List.of("holo-input-recorder-1.13.0.jar"), names());
    }

    @Test
    void cleanupNeverDeletesTheRunningJarEvenWhenHidden() throws IOException {
        Path running = put("holo-input-recorder-1.13.0.jar", jar("1.13.0"));
        if (!UpdateSwap.setHidden(running, true)) return;
        assertNull(UpdateSwap.cleanup(mods, "1.13.0", running));
        assertNull(UpdateSwap.cleanup(mods, "1.13.0", null));
        assertTrue(Files.exists(running));
    }

    @Test
    void aFailedMoveAfterHidingUnhidesTheOldJar() throws IOException {
        Path old = put("holo-input-recorder-1.12.0.jar", jar("1.12.0"));
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        Path target = put("holo-input-recorder-1.13.0.jar", "in the way".getBytes());
        assertFalse(UpdateSwap.hideAndSwap(old, staged, target));
        assertFalse(hidden(old));
        assertTrue(Files.exists(staged));
    }

    @Test
    void aMissingRunningJarIsAFailureNotACrash() throws IOException {
        Path staged = put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        assertFalse(UpdateSwap.trySwap(mods.resolve("gone.jar"), staged, mods.resolve("holo-input-recorder-1.13.0.jar")));
        assertTrue(Files.exists(staged));
    }

    @Test
    void cleanupRemovesLeftoversAndKeepsOnlyANewerDownload() throws IOException {
        put("holo-input-recorder-1.11.0.jar.disabled", jar("1.11.0"));
        put("holo-input-recorder-1.12.0.jar.new", jar("1.12.0"));
        put("holo-input-recorder-1.9.0.jar.new", jar("1.9.0"));
        put("holo-input-recorder-1.0.0.jar.new", "not a jar".getBytes());
        put("holo-input-recorder-1.13.0.jar.new", jar("1.13.0"));
        put("holo-input-recorder-1.14.0.jar.new", jar("1.14.0"));
        put("holo-input-recorder-1.12.0.jar", jar("1.12.0"));
        put("other-mod-1.0.jar.disabled", jar("1.0"));
        put("something.new", jar("9.9.9"));
        Files.createDirectories(UpdateSwap.updateDir(mods));
        Path part = Files.write(UpdateSwap.updateDir(mods).resolve("x.jar.part"), new byte[] {1});

        UpdateSwap.Staged staged = UpdateSwap.cleanup(mods, "1.12.0", null);

        assertEquals(new UpdateSwap.Staged("1.14.0", "holo-input-recorder-1.14.0.jar"), staged);
        assertEquals(List.of(".holoplayer-update", "holo-input-recorder-1.12.0.jar", "holo-input-recorder-1.14.0.jar.new",
                "other-mod-1.0.jar.disabled", "something.new"), names());
        assertFalse(Files.exists(part));
    }

    @Test
    void cleanupAcceptsTheMc262VersionSuffix() throws IOException {
        put("holo-input-recorder-1.13.0+mc26.2.jar.new", jar("1.13.0+mc26.2"));
        assertEquals("1.13.0", UpdateSwap.cleanup(mods, "1.12.0+mc26.2", null).version());
        assertNull(UpdateSwap.cleanup(mods, "1.13.0+mc26.2", null));
    }

    @Test
    void cleanupOnAMissingFolderDoesNotThrow() {
        assertNull(UpdateSwap.cleanup(mods.resolve("nope"), "1.12.0", null));
    }
}
