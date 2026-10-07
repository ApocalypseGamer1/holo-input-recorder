package holorecorder;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModFileHashesTest {
    private static String expected(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
    }

    @Test void aJarHashesToItsSha512InLowercaseHex() throws Exception {
        Path dir = Files.createTempDirectory("holohash");
        byte[] bytes = new byte[200_000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
        Path jar = Files.write(dir.resolve("some-mod-1.0.jar"), bytes);
        ModFileHashes.clearCache();
        String got = ModFileHashes.sha512(jar);
        assertEquals(expected(bytes), got);
        assertEquals(ModFileHashes.HEX_LENGTH, got.length());
        assertEquals(got.toLowerCase(), got);
    }

    @Test void anythingButARegularJarGivesNoFingerprint() throws Exception {
        Path dir = Files.createTempDirectory("holohash");
        assertEquals("", ModFileHashes.sha512(Files.writeString(dir.resolve("notes.txt"), "x")));
        assertEquals("", ModFileHashes.sha512(Files.createDirectory(dir.resolve("folder.jar"))));
        assertEquals("", ModFileHashes.sha512(dir.resolve("missing.jar")));
        assertEquals("", ModFileHashes.sha512(null));
    }

    @Test void aChangedFileIsHashedAgain() throws Exception {
        Path dir = Files.createTempDirectory("holohash");
        Path jar = Files.writeString(dir.resolve("m.jar"), "first");
        ModFileHashes.clearCache();
        String before = ModFileHashes.sha512(jar);
        Files.writeString(jar, "second, longer");
        Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis() + 5_000));
        String after = ModFileHashes.sha512(jar);
        assertNotEquals(before, after);
        assertEquals(expected("second, longer".getBytes()), after);
    }
}
