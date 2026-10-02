package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** File check for data protection: the server's request, answered by {@link IntegrityResponsePayload}. */
public record IntegrityChallengePayload(byte version, byte[] nonce) implements CustomPacketPayload {
    public static final byte VERSION = 1;
    public static final int NONCE_LENGTH = 32;

    public static final Type<IntegrityChallengePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "integrity_challenge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrityChallengePayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeByte(v.version);
                buf.writeByteArray(v.nonce);
            },
            buf -> {
                byte version = buf.readByte();
                byte[] nonce = buf.readByteArray(NONCE_LENGTH);
                if (nonce.length != NONCE_LENGTH) throw new IllegalArgumentException("holo: nonce length " + nonce.length);
                return new IntegrityChallengePayload(version, nonce);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
