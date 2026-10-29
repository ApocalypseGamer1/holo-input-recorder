package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Opens (or replaces) a custom menu on a Holoplayer client that takes {@code holoserver:menu_open}. The slots are the
 * chest's slots (0 .. rows*9-1); {@code backSlot} is the slot that goes back one menu, or -1 for a root menu.
 */
public record MenuOpenPayload(int version, int menuId, Component title, int rows, int backSlot, List<MenuSection> sections,
                              List<MenuEntry> entries) implements CustomPacketPayload {
    public static final int VERSION = 1;
    public static final Type<MenuOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "menu_open"));
    public static final StreamCodec<ByteBuf, MenuOpenPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.VAR_INT.encode(buf, v.menuId);
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, v.title);
                ByteBufCodecs.VAR_INT.encode(buf, v.rows);
                ByteBufCodecs.VAR_INT.encode(buf, v.backSlot);
                MenuSection.LIST_CODEC.encode(buf, v.sections);
                MenuEntry.listCodec(54).encode(buf, v.entries);
            },
            buf -> new MenuOpenPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), MenuSection.LIST_CODEC.decode(buf), MenuEntry.listCodec(54).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
