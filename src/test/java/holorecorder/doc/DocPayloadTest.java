package holorecorder.doc;

import holorecorder.protocol.DocActionPayload;
import holorecorder.protocol.DocOpenPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The two document payloads: channel ids, wire layout and size limits (same bytes as the server's DocOpenPayload / DocActionPayload). */
class DocPayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void theChannelIdsMatchTheServer() {
        assertEquals("holoserver:doc_open", DocOpenPayload.TYPE.id().toString());
        assertEquals("holoserver:doc_action", DocActionPayload.TYPE.id().toString());
    }

    @Test
    void theOpenPayloadRoundTripsAsOneUtf8StringAndFeedsTheModel() {
        String json = DocLayoutTest.SAMPLE;
        RegistryFriendlyByteBuf buf = buffer();
        DocOpenPayload.CODEC.encode(buf, new DocOpenPayload(json));
        assertEquals(json, DocOpenPayload.CODEC.decode(buf).json());
        assertEquals(0, buf.readableBytes());
        buf = buffer();
        DocOpenPayload.CODEC.encode(buf, new DocOpenPayload(json));
        assertEquals(json, buf.readUtf(DocOpenPayload.MAX_JSON));   // a plain Minecraft string on the wire
        assertNotNull(DocModel.parse(json));
    }

    @Test
    void theOpenPayloadRefusesMoreThanTheLimit() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeUtf("x".repeat(DocOpenPayload.MAX_JSON + 1), DocOpenPayload.MAX_JSON + 10);
        assertThrows(RuntimeException.class, () -> DocOpenPayload.CODEC.decode(buf));
    }

    @Test
    void theActionPayloadCarriesBackOrClose() {
        for (String action : new String[]{DocActionPayload.BACK, DocActionPayload.CLOSE}) {
            RegistryFriendlyByteBuf buf = buffer();
            DocActionPayload.CODEC.encode(buf, new DocActionPayload(action));
            assertEquals(action, buf.readUtf(64));
            assertEquals(0, buf.readableBytes());
        }
        assertEquals("back", DocActionPayload.BACK);
        assertEquals("close", DocActionPayload.CLOSE);
        RegistryFriendlyByteBuf big = buffer();
        big.writeUtf("y".repeat(65), 100);
        assertThrows(RuntimeException.class, () -> DocActionPayload.CODEC.decode(big));
    }
}
