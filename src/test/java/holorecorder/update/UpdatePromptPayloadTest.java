package holorecorder.update;

import holorecorder.protocol.UpdatePromptPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdatePromptPayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripsAndMatchesTheWireFormat() {
        ByteBuf buf = Unpooled.buffer();
        UpdatePromptPayload.CODEC.encode(buf, new UpdatePromptPayload(1, "1.13.0"));
        ByteBuf golden = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(golden, 1);
        ByteBufCodecs.stringUtf8(32).encode(golden, "1.13.0");
        assertEquals(golden, buf.duplicate());
        UpdatePromptPayload back = UpdatePromptPayload.CODEC.decode(buf);
        assertEquals(new UpdatePromptPayload(1, "1.13.0"), back);
        assertEquals(0, buf.readableBytes());
        assertEquals("holoserver:update_prompt", UpdatePromptPayload.TYPE.id().toString());
    }

    @Test
    void anUnknownVersionIsSkippedWithoutLeavingBytes() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 2);
        buf.writeBytes(new byte[] {9, 9, 9, 9});
        UpdatePromptPayload p = UpdatePromptPayload.CODEC.decode(buf);
        assertEquals(2, p.version());
        assertEquals("", p.latest());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aTooLongTextIsRefused() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 1);
        ByteBufCodecs.stringUtf8(100).encode(buf, "x".repeat(40));
        assertThrows(RuntimeException.class, () -> UpdatePromptPayload.CODEC.decode(buf));
    }
}
