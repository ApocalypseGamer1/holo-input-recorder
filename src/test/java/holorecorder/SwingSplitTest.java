package holorecorder;

import holorecorder.protocol.ClientEventsPayload;
import holorecorder.protocol.ClientInputPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Attack swings (left click) and use swings (right click) are counted apart; both reach the server payload. */
class SwingSplitTest {
    @BeforeEach void open() {
        RawInputCapture.clear();
        RawInputCapture.arm(true, key -> true);
        RawInputCapture.screenProbe(() -> RawInputCapture.SCREEN_NONE);
    }
    @AfterEach void close() { RawInputCapture.arm(false, null); RawInputCapture.screenProbe(null); RawInputCapture.clear(); }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test void attackAndUseSwingsAreCountedSeparatelyAndTheDrainResetsBoth() {
        RawInputCapture.swing();
        RawInputCapture.swing();
        RawInputCapture.useSwing();
        RawInputCapture.Snapshot first = RawInputCapture.drain();
        assertEquals(2, first.swings());
        assertEquals(1, first.useSwings());
        RawInputCapture.Snapshot second = RawInputCapture.drain();
        assertEquals(0, second.swings());
        assertEquals(0, second.useSwings());
    }

    @Test void theSubTickLogTagsAUseSwingWithBOneOnTheExistingSwingType() {
        RawInputCapture.swing();
        RawInputCapture.useSwing();
        RawInputCapture.Events ev = RawInputCapture.drainEvents();
        assertArrayEquals(new byte[] {ClientEventsPayload.TYPE_SWING, ClientEventsPayload.TYPE_SWING}, ev.types());
        assertArrayEquals(new float[] {0f, 0f}, ev.a());
        assertArrayEquals(new float[] {0f, ClientEventsPayload.SWING_USE}, ev.b());
    }

    @Test void theTickPayloadCarriesTheSplitAndEverythingElseUnchanged() {
        RawInputCapture.swing();
        RawInputCapture.useSwing();
        RawInputCapture.useSwing();
        RawInputCapture.mousePress(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        RawInputCapture.mousePress(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        RawInputCapture.mouseTurn(3.5, -2.0);
        ClientInputPayload p = ClientInputPayload.of("r-1", 9L, 120, 1_700_000_000_000L, RawInputCapture.drain(),
                1.5f, -0.5f, 0b101, 4);
        assertEquals(ClientInputPayload.VERSION, p.version());
        assertEquals(1, p.swings());
        assertEquals(2, p.useSwings());
        assertEquals(1, p.leftPresses());
        assertEquals(1, p.rightPresses());
        assertEquals(3.5, p.rawMouseDx());
        assertEquals(-2.0, p.rawMouseDy());
        assertEquals(4, p.selectedSlot());
        assertEquals(0b101, p.flags());
    }

    @Test void theCodecRoundTripsTheUseSwingFieldLastAndConsumesTheWholePayload() {
        ClientInputPayload sent = new ClientInputPayload(ClientInputPayload.VERSION, "r-1", 7L, 42, 1L, 1.0, 2.0,
                3f, 4f, 0x7ff, 5, 6, 7, 8, 9);
        RegistryFriendlyByteBuf buf = buffer();
        ClientInputPayload.CODEC.encode(buf, sent);
        assertEquals(sent, ClientInputPayload.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test void theVersionIsThreeSoAServerOnAnOlderVersionRefusesInsteadOfMisreading() {
        assertEquals(3, ClientInputPayload.VERSION);
    }

    /** The server's golden sample (client-recorder-mv golden/payloads.hex): version 3 keeps the version 2 layout. */
    @Test void theVersionThreeInventorySampleEncodesToTheServerGoldenBytes() {
        RegistryFriendlyByteBuf buf = buffer();
        ClientInputPayload.CODEC.encode(buf, new ClientInputPayload(3, "r-1", 8L, 43, 2L,
                0.0, 0.0, 0f, 0f, 0x1800, 0, 1, 0, 2, 0));
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        assertEquals("0303722d31082b0200000000000000000000000000000000000000000000000080300001000200",
                java.util.HexFormat.of().formatHex(bytes));
    }

    @Test void insideTheInventoryScreenMouseInputIsRecordedButNoKey() {
        RawInputCapture.screenProbe(() -> RawInputCapture.SCREEN_INVENTORY);
        assertTrue(RawInputCapture.cursorCapture());
        RawInputCapture.move(3.0, -1.0);
        RawInputCapture.button(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, 0);
        RawInputCapture.scroll(0.0, 1.0);
        RawInputCapture.key(26, 1, 0);
        RawInputCapture.key(26, -1, 0);
        RawInputCapture.key(26, 0, 0);
        RawInputCapture.Events ev = RawInputCapture.drainEvents();
        assertArrayEquals(new byte[] {ClientEventsPayload.TYPE_MOUSE_MOVE, ClientEventsPayload.TYPE_BUTTON_DOWN,
                ClientEventsPayload.TYPE_SCROLL}, ev.types());
    }

    @Test void anyOtherScreenRecordsNothingAtTheMomentItOpens() {
        RawInputCapture.screenProbe(() -> RawInputCapture.SCREEN_OTHER);
        assertFalse(RawInputCapture.cursorCapture());
        RawInputCapture.move(3.0, -1.0);
        RawInputCapture.button(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, 0);
        RawInputCapture.scroll(0.0, 1.0);
        RawInputCapture.key(26, 1, 0);
        RawInputCapture.swing();
        assertEquals(0, RawInputCapture.drainEvents().size());
        RawInputCapture.screenProbe(() -> RawInputCapture.SCREEN_INVENTORY);
        RawInputCapture.arm(false, null);
        assertFalse(RawInputCapture.cursorCapture(), "no round, no cursor");
    }

    @Test void beforeTheProbeIsSetNothingIsRecorded() {
        RawInputCapture.screenProbe(null);
        RawInputCapture.move(1.0, 1.0);
        assertEquals(0, RawInputCapture.drainEvents().size());
    }

    @Test void aKeyRepeatIsActionMinusOneOnTheSdlClientAndMouseButtonsAreCountedBySdlNumber() {
        RawInputCapture.key(26, 1, 0);
        RawInputCapture.key(26, -1, 0);
        RawInputCapture.key(26, 0, 0);
        RawInputCapture.key(26, 2, 0);                      // not an SDL action: ignored
        RawInputCapture.Events ev = RawInputCapture.drainEvents();
        assertArrayEquals(new byte[] {ClientEventsPayload.TYPE_KEY_DOWN, ClientEventsPayload.TYPE_KEY_REPEAT,
                ClientEventsPayload.TYPE_KEY_UP}, ev.types());
        RawInputCapture.mousePress(2);                      // middle: neither counter
        RawInputCapture.Snapshot s = RawInputCapture.drain();
        assertEquals(0, s.leftPresses());
        assertEquals(0, s.rightPresses());
    }

    @Test void anUnboundKeyIsNeverRecordedAndAClosedGateRecordsNothing() {
        RawInputCapture.arm(true, key -> key == 26);
        RawInputCapture.key(27, 1, 0);
        RawInputCapture.arm(false, null);
        RawInputCapture.key(26, 1, 0);
        RawInputCapture.swing();
        assertEquals(0, RawInputCapture.drainEvents().size());
        assertEquals(1, RawInputCapture.drain().swings());  // the tick counter is not gated, only the event log
    }

    @Test void eventArraysOfDifferentLengthAreRefusedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new ClientEventsPayload(ClientEventsPayload.VERSION,
                "r", 1L, 1, 1L, 0, new int[2], new byte[1], new float[2], new float[2]));
    }
}
