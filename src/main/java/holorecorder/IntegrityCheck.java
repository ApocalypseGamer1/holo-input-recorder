package holorecorder;

import holorecorder.protocol.IntegrityChallengePayload;
import holorecorder.protocol.IntegrityResponsePayload;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** File check for data protection. */
final class IntegrityCheck {
    private static final byte[] NONE = new byte[0];

    private IntegrityCheck() {}

    /** HMAC-SHA256 of the mod's jar keyed by the nonce; empty unless the origin is exactly one regular .jar file. */
    static byte[] digest(List<Path> origin, byte[] nonce) {
        if (origin == null || origin.size() != 1) return NONE;
        Path jar = origin.get(0);
        if (!Files.isRegularFile(jar) || !jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return NONE;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nonce, "HmacSHA256"));
            return mac.doFinal(Files.readAllBytes(jar));
        } catch (Exception | OutOfMemoryError e) {
            return NONE;
        }
    }

    /** The response to a challenge, computed from this mod's own jar. Blocking: call off the render thread. */
    static IntegrityResponsePayload respond(IntegrityChallengePayload challenge) {
        var container = FabricLoader.getInstance().getModContainer("holo_input_recorder");
        String own = container.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        List<Path> origin;
        try {
            origin = container.map(m -> m.getOrigin().getPaths()).orElse(List.of());
        } catch (RuntimeException e) {
            origin = List.of();
        }
        return new IntegrityResponsePayload(IntegrityResponsePayload.VERSION, own, digest(origin, challenge.nonce()));
    }
}
