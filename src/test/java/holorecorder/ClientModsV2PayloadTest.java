package holorecorder;

import holorecorder.protocol.ClientModsV2Payload;
import holorecorder.protocol.ClientModsV2Payload.Mod;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModsV2PayloadTest {
    private static final String HASH = "ab".repeat(64);

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void theCodecRoundTripsAndConsumesTheWholePayload() {
        ClientModsV2Payload sent = ClientModsV2Payload.of("1.4.0", 42L, 0, 1, List.of(
                new Mod("sodium", "0.8.0", "Sodium", "", 1_234_567L, HASH),
                new Mod("fabric-api-base", "1.0", "Fabric API Base", "fabric-api", -1L, "")));
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsV2Payload.CODEC.encode(buf, sent);
        assertEquals(sent, ClientModsV2Payload.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
        assertEquals(ClientModsV2Payload.VERSION, sent.version());
    }

    @Test void theWireLayoutIsAsDocumented() {
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsV2Payload.CODEC.encode(buf, ClientModsV2Payload.of("1.4.0", 7L, 1, 2,
                List.of(new Mod("a", "1", "A", "p", 10L, HASH))));
        assertEquals(2, buf.readVarInt());
        assertEquals("1.4.0", buf.readUtf(100));
        assertEquals(7L, buf.readLong());
        assertEquals(1, buf.readVarInt());
        assertEquals(2, buf.readVarInt());
        assertEquals(1, buf.readVarInt());
        assertEquals("a", buf.readUtf(100));
        assertEquals("1", buf.readUtf(100));
        assertEquals("A", buf.readUtf(100));
        assertEquals("p", buf.readUtf(100));
        assertEquals(11L, buf.readVarLong());   // size + 1, so an unknown size (-1) is 0
        assertEquals(HASH, buf.readUtf(128));
        assertEquals(0, buf.readableBytes());
    }

    @Test void malformedHashesAreDroppedUppercaseIsLowered() {
        ClientModsV2Payload p = ClientModsV2Payload.of("1", 1L, 0, 1, List.of(
                new Mod("a", "1", "A", "", 1L, "xyz"), new Mod("b", "1", "B", "", 1L, HASH.toUpperCase())));
        assertEquals("", p.mods().get(0).sha512());
        assertEquals(HASH, p.mods().get(1).sha512());
    }

    @Test void aDecoderRefusesBadPartsAndBadHashes() {
        RegistryFriendlyByteBuf parts = buffer();
        parts.writeVarInt(2);
        parts.writeUtf("1", 100);
        parts.writeLong(1L);
        parts.writeVarInt(3);
        parts.writeVarInt(3);
        assertThrows(IllegalArgumentException.class, () -> ClientModsV2Payload.CODEC.decode(parts));

        RegistryFriendlyByteBuf hash = buffer();
        hash.writeVarInt(2);
        hash.writeUtf("1", 100);
        hash.writeLong(1L);
        hash.writeVarInt(0);
        hash.writeVarInt(1);
        hash.writeVarInt(1);
        for (String s : new String[] {"a", "1", "A", ""}) hash.writeUtf(s, 100);
        hash.writeVarLong(5L);
        hash.writeUtf("g".repeat(128), 128);
        assertThrows(IllegalArgumentException.class, () -> ClientModsV2Payload.CODEC.decode(hash));
    }

    @Test void aLargeReportSplitsIntoPartsUnderThePayloadLimit() {
        List<Mod> mods = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            String s = ("mod" + i + "-").repeat(12);
            mods.add(ClientModsV2Payload.capped(new Mod(s, s, s, i % 3 == 0 ? "parent" : "", 1_000_000L + i, HASH)));
        }
        List<ClientModsV2Payload> parts = ClientModsReport.parts("1.4.0", 99L, mods);
        assertTrue(parts.size() > 1);
        List<Mod> back = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            ClientModsV2Payload p = parts.get(i);
            assertEquals(i, p.part());
            assertEquals(parts.size(), p.parts());
            assertEquals(99L, p.reportId());
            RegistryFriendlyByteBuf buf = buffer();
            ClientModsV2Payload.CODEC.encode(buf, p);
            assertTrue(buf.readableBytes() <= 32_000, "part " + i + " is " + buf.readableBytes() + " bytes");
            ClientModsV2Payload decoded = ClientModsV2Payload.CODEC.decode(buf);
            back.addAll(decoded.mods());
        }
        assertEquals(mods, back);
    }
}
