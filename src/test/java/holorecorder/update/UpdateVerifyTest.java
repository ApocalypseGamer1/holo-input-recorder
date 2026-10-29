package holorecorder.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class UpdateVerifyTest {
    static byte[] jar(String modJson) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("holorecorder/Dummy.class"));
            z.write(new byte[] {1, 2, 3});
            z.closeEntry();
            if (modJson != null) {
                z.putNextEntry(new ZipEntry("fabric.mod.json"));
                z.write(modJson.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    static UpdateManifest.Build buildOf(byte[] bytes) {
        return new UpdateManifest.Build("v1.13.0", "holo-input-recorder-1.13.0.jar", UpdateVerify.sha256(bytes), bytes.length);
    }

    @Test
    void knownDigest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateVerify.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsTheRightJar() throws IOException {
        byte[] b = jar("{\"id\":\"holo_input_recorder\",\"version\":\"1.13.0\"}");
        UpdateVerify.check(b, buildOf(b), "1.13.0");
        byte[] mc262 = jar("{\"id\":\"holo_input_recorder\",\"version\":\"1.13.0+mc26.2\"}");
        UpdateVerify.check(mc262, buildOf(mc262), "1.13.0");
    }

    @Test
    void rejectsChangedBytesAndWrongSize() throws IOException {
        byte[] b = jar("{\"id\":\"holo_input_recorder\",\"version\":\"1.13.0\"}");
        UpdateManifest.Build build = buildOf(b);
        byte[] changed = b.clone();
        changed[changed.length - 1] ^= 1;
        assertThrows(IOException.class, () -> UpdateVerify.check(changed, build, "1.13.0"));
        UpdateManifest.Build wrongSize = new UpdateManifest.Build(build.tag(), build.file(), build.sha256(), b.length + 1);
        assertThrows(IOException.class, () -> UpdateVerify.check(b, wrongSize, "1.13.0"));
        UpdateManifest.Build wrongSha = new UpdateManifest.Build(build.tag(), build.file(), "0".repeat(64), b.length);
        assertThrows(IOException.class, () -> UpdateVerify.check(b, wrongSha, "1.13.0"));
    }

    @Test
    void rejectsAJarThatIsNotThisMod() throws IOException {
        for (String json : new String[] {"{\"id\":\"other_mod\",\"version\":\"1.13.0\"}", "{\"id\":\"holo_input_recorder\",\"version\":\"1.12.0\"}",
                "{\"id\":\"holo_input_recorder\"}", "{\"version\":\"1.13.0\"}", "not json", "[]", null}) {
            byte[] b = jar(json);
            assertThrows(IOException.class, () -> UpdateVerify.check(b, buildOf(b), "1.13.0"), String.valueOf(json));
        }
        byte[] notZip = "this is not a zip file at all".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> UpdateVerify.check(notZip, buildOf(notZip), "1.13.0"));
    }
}
