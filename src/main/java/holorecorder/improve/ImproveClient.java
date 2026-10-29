package holorecorder.improve;

import holorecorder.protocol.ImproveActionPayload;
import holorecorder.protocol.ImproveOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Receives the server's Improve screen and sends back Back / Close. Every method runs on the client thread. */
public final class ImproveClient {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private ImproveClient() {
    }

    /** Opens the screen, or refreshes it in place when it is already up. A payload that cannot be read is logged once and ignored. */
    public static void open(Minecraft mc, ImproveOpenPayload p) {
        ImproveModel.Screen data;
        try {
            data = ImproveModel.parse(p.json());
        } catch (RuntimeException e) {
            data = null;
        }
        if (data == null) {
            if (LOGGED.compareAndSet(false, true)) LOG.warn("Improve screen: the server sent data that cannot be read; not opened");
            return;
        }
        if (mc.gui.screen() instanceof ImproveScreen screen) screen.replace(data);
        else mc.gui.setScreen(new ImproveScreen(data));
    }

    static void send(String action) {
        if (ClientPlayNetworking.canSend(ImproveActionPayload.TYPE)) ClientPlayNetworking.send(new ImproveActionPayload(action));
    }
}
