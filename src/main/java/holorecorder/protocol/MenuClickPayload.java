package holorecorder.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A click on slot {@code slot} of custom menu {@code menuId}. {@code kind}: 0 left, 1 right, 2 shift-click. */
public record MenuClickPayload(int version, int menuId, int slot, int kind) implements CustomPacketPayload {
    public static final Type<MenuClickPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "menu_click"));
    public static final StreamCodec<ByteBuf, MenuClickPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                ByteBufCodecs.VAR_INT.encode(buf, v.version);
                ByteBufCodecs.VAR_INT.encode(buf, v.menuId);
                ByteBufCodecs.VAR_INT.encode(buf, v.slot);
                ByteBufCodecs.VAR_INT.encode(buf, v.kind);
            },
            buf -> new MenuClickPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
