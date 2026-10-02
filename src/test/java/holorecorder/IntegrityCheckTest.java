package holorecorder;

import holorecorder.protocol.IntegrityChallengePayload;
import holorecorder.protocol.IntegrityResponsePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrityCheckTest {
    private static final byte[] DATA = "holo integrity reference bytes".getBytes(StandardCharsets.UTF_8);
    /** HMAC-SHA256 of DATA with key bytes 0..31, computed with Python's hmac module. */
    private static final String REFERENCE = "4e92338a725abb3681a02589ed56d82a8b7a2c4694f3087532b6f8a37c74a4e3";

    private static byte[] nonce() {
        byte[] n = new byte[32];
        for (int i = 0; i < n.length; i++) n[i] = (byte) i;
        return n;
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void theDigestIsHmacSha256OfTheJarBytesKeyedByTheNonce(@TempDir Path dir) throws Exception {
        Path jar = Files.write(dir.resolve("mod.jar"), DATA);
        assertEquals(REFERENCE, HexFormat.of().formatHex(IntegrityCheck.digest(List.of(jar), nonce())));
    }

    @Test void aDirectoryOriginGivesAnEmptyDigest(@TempDir Path dir) {
        assertArrayEquals(new byte[0], IntegrityCheck.digest(List.of(dir), nonce()));
    }

    @Test void aMissingFileANonJarFileOrSeveralPathsGiveAnEmptyDigest(@TempDir Path dir) throws Exception {
        Path jar = Files.write(dir.resolve("mod.jar"), DATA);
        Path txt = Files.write(dir.resolve("mod.txt"), DATA);
        assertArrayEquals(new byte[0], IntegrityCheck.digest(List.of(dir.resolve("none.jar")), nonce()));
        assertArrayEquals(new byte[0], IntegrityCheck.digest(List.of(txt), nonce()));
        assertArrayEquals(new byte[0], IntegrityCheck.digest(List.of(jar, jar), nonce()));
        assertArrayEquals(new byte[0], IntegrityCheck.digest(List.of(), nonce()));
    }

    @Test void aDifferentNonceOrFileGivesADifferentDigest(@TempDir Path dir) throws Exception {
        Path jar = Files.write(dir.resolve("a.jar"), DATA);
        Path other = Files.write(dir.resolve("b.jar"), "edited".getBytes(StandardCharsets.UTF_8));
        byte[] other1 = nonce();
        other1[0] = 99;
        byte[] base = IntegrityCheck.digest(List.of(jar), nonce());
        assertEquals(false, java.util.Arrays.equals(base, IntegrityCheck.digest(List.of(jar), other1)));
        assertEquals(false, java.util.Arrays.equals(base, IntegrityCheck.digest(List.of(other), nonce())));
    }

    @Test void theChallengeCodecRoundTripsExactlyThirtyTwoNonceBytes() {
        RegistryFriendlyByteBuf buf = buffer();
        IntegrityChallengePayload.CODEC.encode(buf, new IntegrityChallengePayload(IntegrityChallengePayload.VERSION, nonce()));
        IntegrityChallengePayload back = IntegrityChallengePayload.CODEC.decode(buf);
        assertEquals(1, back.version());
        assertArrayEquals(nonce(), back.nonce());
        assertEquals(0, buf.readableBytes());

        RegistryFriendlyByteBuf bad = buffer();
        bad.writeByte(1);
        bad.writeByteArray(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> IntegrityChallengePayload.CODEC.decode(bad));
    }

    @Test void theResponseCodecRoundTripsAFullAndAnEmptyDigestAndRefusesOtherLengths() {
        for (byte[] digest : new byte[][] {new byte[32], new byte[0]}) {
            RegistryFriendlyByteBuf buf = buffer();
            IntegrityResponsePayload.CODEC.encode(buf,
                    new IntegrityResponsePayload(IntegrityResponsePayload.VERSION, "1.3.0", digest));
            IntegrityResponsePayload back = IntegrityResponsePayload.CODEC.decode(buf);
            assertEquals(1, back.version());
            assertEquals("1.3.0", back.recorderVersion());
            assertArrayEquals(digest, back.digest());
            assertEquals(0, buf.readableBytes());
        }
        RegistryFriendlyByteBuf bad = buffer();
        bad.writeByte(1);
        bad.writeUtf("1.3.0", 100);
        bad.writeByteArray(new byte[7]);
        assertThrows(IllegalArgumentException.class, () -> IntegrityResponsePayload.CODEC.decode(bad));
    }
}
