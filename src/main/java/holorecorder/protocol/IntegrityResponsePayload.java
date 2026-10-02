package holorecorder.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** File check for data protection. {@code digest} is 32 bytes, or empty when the mod file could not be checked. */
public record IntegrityResponsePayload(byte version, String recorderVersion, byte[] digest)
        implements CustomPacketPayload {
    public static final byte VERSION = 1;
    public static final int DIGEST_LENGTH = 32;
    public static final int MAX_STRING = 100;

    public static final Type<IntegrityResponsePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("holoserver", "integrity_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrityResponsePayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeByte(v.version);
                String s = v.recorderVersion == null ? "" : v.recorderVersion;
                buf.writeUtf(s.length() <= MAX_STRING ? s : s.substring(0, MAX_STRING), MAX_STRING);
                buf.writeByteArray(v.digest);
            },
            buf -> {
                byte version = buf.readByte();
                String recorderVersion = buf.readUtf(MAX_STRING);
                byte[] digest = buf.readByteArray(DIGEST_LENGTH);
                if (digest.length != 0 && digest.length != DIGEST_LENGTH) {
                    throw new IllegalArgumentException("holo: digest length " + digest.length);
                }
                return new IntegrityResponsePayload(version, recorderVersion, digest);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
