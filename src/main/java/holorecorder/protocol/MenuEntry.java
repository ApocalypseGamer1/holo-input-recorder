package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * One tile of a custom menu: the item to draw as the icon, its name and lore already in the player's language.
 * {@code item} is a registry id ("minecraft:diamond_sword"); an empty id means "no tile in this slot".
 */
public record MenuEntry(int slot, String item, Component name, List<Component> lore) {
    public static final int MAX_LORE = 40;
    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;

    public static final StreamCodec<ByteBuf, MenuEntry> CODEC = StreamCodec.of(
            (buf, e) -> {
                ByteBufCodecs.VAR_INT.encode(buf, e.slot);
                ByteBufCodecs.stringUtf8(128).encode(buf, e.item);
                TEXT.encode(buf, e.name);
                ByteBufCodecs.VAR_INT.encode(buf, e.lore.size());
                for (Component c : e.lore) TEXT.encode(buf, c);
            },
            buf -> {
                int slot = ByteBufCodecs.VAR_INT.decode(buf);
                String item = ByteBufCodecs.stringUtf8(128).decode(buf);
                Component name = TEXT.decode(buf);
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                if (n < 0 || n > MAX_LORE) throw new IllegalArgumentException("lore lines " + n);
                ArrayList<Component> lore = new ArrayList<>(n);
                for (int i = 0; i < n; i++) lore.add(TEXT.decode(buf));
                return new MenuEntry(slot, item, name, lore);
            });

    public MenuEntry {
        lore = lore == null ? List.of() : lore.size() > MAX_LORE ? List.copyOf(lore.subList(0, MAX_LORE)) : List.copyOf(lore);
    }

    /** A tile that removes whatever sits in {@code slot}. */
    public static MenuEntry empty(int slot) {
        return new MenuEntry(slot, "", Component.empty(), List.of());
    }

    public boolean isEmpty() {
        return item.isEmpty();
    }

    /** Wire codec for a list of entries (at most {@code max}). */
    public static StreamCodec<ByteBuf, List<MenuEntry>> listCodec(int max) {
        return StreamCodec.of(
                (buf, list) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, list.size());
                    for (MenuEntry e : list) CODEC.encode(buf, e);
                },
                buf -> {
                    int n = ByteBufCodecs.VAR_INT.decode(buf);
                    if (n < 0 || n > max) throw new IllegalArgumentException("entries " + n);
                    ArrayList<MenuEntry> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(CODEC.decode(buf));
                    return List.copyOf(out);
                });
    }
}
