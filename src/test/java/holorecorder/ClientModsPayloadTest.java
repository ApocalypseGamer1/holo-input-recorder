package holorecorder;

import holorecorder.protocol.ClientModsPayload;
import holorecorder.protocol.ClientModsPayload.Mod;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModsPayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void theCodecRoundTripsAndConsumesTheWholePayload() {
        ClientModsPayload sent = ClientModsPayload.of("1.3.0", List.of(
                new Mod("fabricloader", "0.19.5", "Fabric Loader"), new Mod("sodium", "0.8.0", "Sodium")));
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsPayload.CODEC.encode(buf, sent);
        assertEquals(sent, ClientModsPayload.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
        assertEquals(ClientModsPayload.VERSION, sent.version());
    }

    @Test void theWireLayoutIsVersionRecorderVersionCountThenIdVersionNamePerMod() {
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsPayload.CODEC.encode(buf, ClientModsPayload.of("1.3.0", List.of(new Mod("a", "1", "A"))));
        assertEquals(1, buf.readVarInt());
        assertEquals("1.3.0", buf.readUtf(100));
        assertEquals(1, buf.readVarInt());
        assertEquals("a", buf.readUtf(100));
        assertEquals("1", buf.readUtf(100));
        assertEquals("A", buf.readUtf(100));
        assertEquals(0, buf.readableBytes());
    }

    @Test void stringsAreCutToOneHundredCharactersAndNullBecomesEmpty() {
        String long150 = "x".repeat(150);
        ClientModsPayload p = ClientModsPayload.of(long150, List.of(new Mod(long150, long150, null)));
        assertEquals(100, p.recorderVersion().length());
        assertEquals(100, p.mods().get(0).id().length());
        assertEquals(100, p.mods().get(0).version().length());
        assertEquals("", p.mods().get(0).name());
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsPayload.CODEC.encode(buf, p);
        assertEquals(p, ClientModsPayload.CODEC.decode(buf));
    }

    @Test void theEncoderCutsAHandBuiltOversizedPayloadInsteadOfFailing() {
        String long150 = "y".repeat(150);
        List<Mod> many = new ArrayList<>();
        for (int i = 0; i < 450; i++) many.add(new Mod(long150, "v", "n"));
        RegistryFriendlyByteBuf buf = buffer();
        ClientModsPayload.CODEC.encode(buf, new ClientModsPayload(1, long150, many));
        ClientModsPayload back = ClientModsPayload.CODEC.decode(buf);
        assertEquals(ClientModsPayload.MAX_MODS, back.mods().size());
        assertEquals(100, back.mods().get(0).id().length());
    }

    @Test void atMostFourHundredEntriesAreKeptInOrder() {
        List<Mod> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) many.add(new Mod("m" + i, "1", "M" + i));
        ClientModsPayload p = ClientModsPayload.of("1.3.0", many);
        assertEquals(400, p.mods().size());
        assertEquals("m0", p.mods().get(0).id());
        assertEquals("m399", p.mods().get(399).id());
    }

    @Test void aDecoderRefusesACountAboveTheCap() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeVarInt(1);
        buf.writeUtf("1.3.0", 100);
        buf.writeVarInt(401);
        assertThrows(IllegalArgumentException.class, () -> ClientModsPayload.CODEC.decode(buf));
    }

    @Test void theReportIsSentOnlyWhenTheServerSpeaksTheRecorderProtocol() {
        AtomicInteger built = new AtomicInteger(), sent = new AtomicInteger();
        assertFalse(ClientModsReport.sendIfSupported(() -> false,
                () -> { built.incrementAndGet(); return ClientModsPayload.of("1", List.of()); },
                p -> sent.incrementAndGet()));
        assertEquals(0, built.get());
        assertEquals(0, sent.get());
        assertTrue(ClientModsReport.sendIfSupported(() -> true,
                () -> { built.incrementAndGet(); return ClientModsPayload.of("1", List.of()); },
                p -> sent.incrementAndGet()));
        assertEquals(1, built.get());
        assertEquals(1, sent.get());
    }
}
