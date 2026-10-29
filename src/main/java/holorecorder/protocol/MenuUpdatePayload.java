package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Changed tiles of the open menu {@code menuId} (counts, training lines...). An empty entry clears its slot. */
public record MenuUpdatePayload(int version, int menuId, List<MenuEntry> changed) implements CustomPacketPayload {
    public static final Type<MenuUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "menu_update"));
    public static final StreamCodec<ByteBuf, MenuUpdatePayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.VAR_INT.encode(buf, v.menuId);
                MenuEntry.listCodec(54).encode(buf, v.changed);
            },
            buf -> new MenuUpdatePayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), MenuEntry.listCodec(54).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
