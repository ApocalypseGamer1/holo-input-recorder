package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The language the server uses for this player ("en", "es"): the one they picked there, else their game's. */
public record LangPayload(String code) implements CustomPacketPayload {
    public static final Type<LangPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("holoserver", "lang"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LangPayload> CODEC = StreamCodec.of(
            (buf, v) -> ByteBufCodecs.stringUtf8(16).encode(buf, v.code), buf -> new LangPayload(ByteBufCodecs.stringUtf8(16).decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
