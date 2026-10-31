package holorecorder.doc;

import holorecorder.protocol.DocActionPayload;
import holorecorder.protocol.DocOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Receives the server's document (guide, rules, privacy) and sends back Back / Close. Every method runs on the client thread. */
public final class DocClient {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private DocClient() {
    }

    /** Opens the screen, or swaps the document in place when one is already up. A payload that cannot be read is logged once and ignored. */
    public static void open(Minecraft mc, DocOpenPayload p) {
        DocModel.Doc data = DocModel.parse(p.json());
        if (data == null) {
            if (LOGGED.compareAndSet(false, true)) LOG.warn("Document screen: the server sent data that cannot be read; not opened");
            return;
        }
        if (mc.gui.screen() instanceof DocScreen screen) screen.replace(data);
        else mc.gui.setScreen(new DocScreen(data));
    }

    static void send(String action) {
        if (ClientPlayNetworking.canSend(DocActionPayload.TYPE)) ClientPlayNetworking.send(new DocActionPayload(action));
    }
}
