package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The player closed the screen of custom menu {@code menuId} (Esc, Close button). */
public record MenuClosedPayload(int version, int menuId) implements CustomPacketPayload {
    public static final Type<MenuClosedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "menu_closed"));
    public static final StreamCodec<ByteBuf, MenuClosedPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.VAR_INT.encode(buf, v.menuId);
            },
            buf -> new MenuClosedPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
