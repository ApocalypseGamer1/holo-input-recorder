package holorecorder;

import holorecorder.protocol.WinBarPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinBarStateTest {
    private static final Object WORLD = new Object();

    @Test void theViewersOwnSideIsAtTheBottomAndASpectatorSeesAThere() {
        assertTrue(WinBarState.bottomIsA(0));
        assertFalse(WinBarState.bottomIsA(1));
        assertTrue(WinBarState.bottomIsA(2));
        assertEquals(0.7, WinBarState.bottomChance(0.7f, 0), 1e-6);
        assertEquals(0.3, WinBarState.bottomChance(0.7f, 1), 1e-6);
        assertEquals(0.7, WinBarState.bottomChance(0.7f, 2), 1e-6);
        assertEquals(0.5, WinBarState.bottomChance(Float.NaN, 0), 1e-6);
        assertEquals(1.0, WinBarState.bottomChance(7f, 0), 1e-6);
    }

    @Test void theLeaderTextIsTheRoundedLargerChance() {
        assertEquals("72%", WinBarState.leaderText(0.72));
        assertEquals("72%", WinBarState.leaderText(0.28));
        assertEquals("50%", WinBarState.leaderText(0.5));
        assertEquals("100%", WinBarState.leaderText(1.0));
        assertEquals("99%", WinBarState.leaderText(0.006));
    }

    @Test void theLeaderOnlyFlipsOutsideTheMarginAroundAHalf() {
        assertTrue(WinBarState.leaderIsBottom(true, 0.495));
        assertFalse(WinBarState.leaderIsBottom(false, 0.505));
        assertTrue(WinBarState.leaderIsBottom(false, 0.6));
        assertFalse(WinBarState.leaderIsBottom(true, 0.4));
    }

    @Test void easingMovesTowardTheTargetAndNeverOvershoots() {
        assertEquals(0.2, WinBarState.ease(0.2, 0.9, 0), 0);
        double v = 0.2;
        for (int i = 0; i < 4; i++) {
            double next = WinBarState.ease(v, 0.9, 16);
            assertTrue(next > v && next < 0.9);
            v = next;
        }
        assertEquals(0.9, WinBarState.ease(0.2, 0.9, 300), 0.01);
        assertEquals(WinBarState.ease(0.2, 0.9, 32), WinBarState.ease(WinBarState.ease(0.2, 0.9, 16), 0.9, 16), 1e-9);
    }

    @Test void theBarShowsFromTheFirstPacketAndSnapsThere() {
        WinBarState s = new WinBarState();
        assertFalse(s.visible(0, WORLD));
        s.update(true, 0.8f, "A", "B", 1, WORLD, 1000);
        assertTrue(s.visible(1000, WORLD));
        assertEquals(0.2, s.shownBottom(), 1e-6);
        assertEquals("B", s.bottomName());
        assertEquals("A", s.topName());
        assertFalse(s.bottomLeads());
    }

    @Test void laterPacketsAreEasedNotJumped() {
        WinBarState s = new WinBarState();
        s.update(true, 0.5f, "A", "B", 0, WORLD, 0);
        s.update(true, 0.9f, "A", "B", 0, WORLD, 100);
        assertEquals(0.5, s.shownBottom(), 1e-9);
        s.step(116);
        assertTrue(s.shownBottom() > 0.5 && s.shownBottom() < 0.9);
        s.step(1000);
        assertEquals(0.9, s.shownBottom(), 0.001);
        assertTrue(s.bottomLeads());
    }

    @Test void theBarHidesOnInactiveStaleOtherWorldAndReset() {
        WinBarState s = new WinBarState();
        s.update(true, 0.6f, "A", "B", 2, WORLD, 0);
        assertTrue(s.visible(WinBarState.STALE_MS, WORLD));
        assertFalse(s.visible(WinBarState.STALE_MS + 1, WORLD));
        assertFalse(s.visible(10, new Object()));
        assertFalse(s.visible(10, null));
        s.update(true, 0.6f, "A", "B", 2, WORLD, 5000);
        assertTrue(s.visible(5000, WORLD));
        s.update(false, 0.6f, "A", "B", 2, WORLD, 5100);
        assertFalse(s.visible(5100, WORLD));
        s.update(true, 0.6f, "A", "B", 2, WORLD, 5200);
        s.reset();
        assertFalse(s.visible(5200, WORLD));
    }

    @Test void aPacketAfterAGapSnapsInsteadOfEasingFromTheOldValue() {
        WinBarState s = new WinBarState();
        s.update(true, 0.1f, "A", "B", 0, WORLD, 0);
        s.update(true, 0.9f, "A", "B", 0, WORLD, 10_000);
        assertEquals(0.9, s.shownBottom(), 1e-6);
    }

    @Test void theCodecRoundTripsInTheAgreedOrder() {
        WinBarPayload sent = new WinBarPayload(true, 0.625f, "Alpha", "Bravo", (byte) 1);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WinBarPayload.CODEC.encode(buf, sent);
        assertTrue(buf.readBoolean());
        assertEquals(0.625f, buf.readFloat());
        assertEquals("Alpha", buf.readUtf(16));
        assertEquals("Bravo", buf.readUtf(16));
        assertEquals(1, buf.readByte());
        assertEquals(0, buf.readableBytes());
        RegistryFriendlyByteBuf again = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WinBarPayload.CODEC.encode(again, sent);
        assertEquals(sent, WinBarPayload.CODEC.decode(again));
    }
}
