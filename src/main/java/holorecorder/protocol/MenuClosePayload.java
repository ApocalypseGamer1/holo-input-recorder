package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server closed custom menu {@code menuId}; the client drops its screen if it still shows that menu. */
public record MenuClosePayload(int version, int menuId) implements CustomPacketPayload {
    public static final Type<MenuClosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "menu_close"));
    public static final StreamCodec<ByteBuf, MenuClosePayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.VAR_INT.encode(buf, v.menuId);
            },
            buf -> new MenuClosePayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
