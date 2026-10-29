package holorecorder.menu;

import holorecorder.protocol.MenuClickPayload;
import holorecorder.protocol.MenuClosePayload;
import holorecorder.protocol.MenuClosedPayload;
import holorecorder.protocol.MenuEntry;
import holorecorder.protocol.MenuOpenPayload;
import holorecorder.protocol.MenuSection;
import holorecorder.protocol.MenuUpdatePayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The menu payloads round-trip, match the server's bytes, and a client applies only updates for the menu on screen. */
class MenuProtocolTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec left bytes behind");
        return back;
    }

    private static MenuEntry entry(int slot, String item, String name, String... lore) {
        return new MenuEntry(slot, item, Component.literal(name).withStyle(ChatFormatting.AQUA),
                java.util.Arrays.stream(lore).map(l -> (Component) Component.literal(l).withStyle(ChatFormatting.GRAY)).toList());
    }

    static MenuOpenPayload goldenOpen() {
        return new MenuOpenPayload(MenuOpenPayload.VERSION, 7, Component.literal("Play"), 4, 27,
                List.of(new MenuSection(1, Component.literal("Modes"))),
                List.of(entry(10, "minecraft:diamond_sword", "Sword", "Line"), entry(27, "minecraft:arrow", "Back", "To the Play menu")));
    }

    private static String hex(StreamCodec<ByteBuf, ?> codec, Object value) {
        ByteBuf buf = Unpooled.buffer();
        @SuppressWarnings("unchecked") StreamCodec<ByteBuf, Object> c = (StreamCodec<ByteBuf, Object>) codec;
        c.encode(buf, value);
        return ByteBufUtil.hexDump(buf);
    }

    @Test
    void everyPayloadRoundTrips() {
        MenuOpenPayload open = goldenOpen();
        assertEquals(open, roundTrip(MenuOpenPayload.CODEC, open));
        MenuUpdatePayload up = new MenuUpdatePayload(1, 7, List.of(entry(10, "minecraft:diamond_sword", "Sword", "3 online"), MenuEntry.empty(11)));
        assertEquals(up, roundTrip(MenuUpdatePayload.CODEC, up));
        assertEquals(new MenuClickPayload(1, 7, 10, 2), roundTrip(MenuClickPayload.CODEC, new MenuClickPayload(1, 7, 10, 2)));
        assertEquals(new MenuClosedPayload(1, 7), roundTrip(MenuClosedPayload.CODEC, new MenuClosedPayload(1, 7)));
        assertEquals(new MenuClosePayload(1, 7), roundTrip(MenuClosePayload.CODEC, new MenuClosePayload(1, 7)));
    }

    @Test
    void theBytesAreTheServersBytes() {
        assertEquals(CompatGolden.OPEN, hex(MenuOpenPayload.CODEC, goldenOpen()));
        assertEquals(CompatGolden.CLICK, hex(MenuClickPayload.CODEC, new MenuClickPayload(1, 7, 10, 2)));
        assertEquals(CompatGolden.CLOSED, hex(MenuClosedPayload.CODEC, new MenuClosedPayload(1, 7)));
    }

    @Test
    void anUpdateAppliesOnlyToTheMenuOnScreen() {
        MenuState state = MenuState.of(goldenOpen());
        assertNotNull(state);
        assertEquals(2, state.tiles().size());
        assertTrue(state.hasBack());
        assertEquals("Modes", state.sectionOf(1).getString());
        assertNull(state.sectionOf(0));
        int before = state.revision();
        assertFalse(state.apply(new MenuUpdatePayload(1, 8, List.of(MenuEntry.empty(10)))), "another menu's update");
        assertFalse(state.apply(new MenuUpdatePayload(2, 7, List.of(MenuEntry.empty(10)))), "an unknown version");
        assertEquals(before, state.revision());
        assertEquals(2, state.tiles().size());
        assertTrue(state.apply(new MenuUpdatePayload(1, 7, List.of(entry(10, "minecraft:diamond_sword", "Sword", "5 online"), MenuEntry.empty(27)))));
        assertEquals("5 online", state.tile(10).lore().get(0).getString());
        assertFalse(state.hasBack(), "the Back tile was cleared");
        assertEquals(before + 1, state.revision());
        assertTrue(state.apply(new MenuUpdatePayload(1, 7, List.of(entry(99, "minecraft:stick", "outside the menu")))));
        assertNull(state.tile(99));
    }

    @Test
    void aNewerProtocolVersionOpensNothing() {
        MenuOpenPayload p = goldenOpen();
        assertNull(MenuState.of(new MenuOpenPayload(2, p.menuId(), p.title(), p.rows(), p.backSlot(), p.sections(), p.entries())));
    }

    @Test
    void theDecoderRefusesOversizedLists() {
        ByteBuf buf = Unpooled.buffer();
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, 500);
        assertThrows(IllegalArgumentException.class, () -> MenuEntry.listCodec(54).decode(buf));
    }

    /** The same constants are pinned in the server's CustomMenuProtocolTest.goldenBytes: the two codec copies cannot drift apart. */
    static final class CompatGolden {
        static final String OPEN = "0107080004506c6179041b01010800054d6f646573020a176d696e6563726166743a6469616d6f6e645f73776f72640a080005636f6c6f7200046171756108000474657874000553776f726400010a080005636f6c6f720004677261790800047465787400044c696e65001b0f6d696e6563726166743a6172726f770a080005636f6c6f720004617175610800047465787400044261636b00010a080005636f6c6f72000467726179080004746578740010546f2074686520506c6179206d656e7500";
        static final String CLICK = "01070a02";
        static final String CLOSED = "0107";
    }
}
