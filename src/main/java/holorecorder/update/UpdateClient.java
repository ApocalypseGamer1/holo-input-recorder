package holorecorder.update;

import holorecorder.Tr;
import holorecorder.protocol.UpdatePromptPayload;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Wires the updater into the game: the launch check, the title-screen button, toasts, and the server's hint. Client thread only. */
public final class UpdateClient {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    private static final AtomicBoolean PROMPTED = new AtomicBoolean();
    private static Button titleButton;

    private UpdateClient() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            try {
                if (!(screen instanceof TitleScreen) || !UpdateService.enabled()) return;
                addTitleButton(mc, screen);
                // the check usually ends after the title screen is up
                ScreenEvents.afterTick(screen).register(s -> addTitleButton(mc, s));
            } catch (RuntimeException e) {
                LOG.warn("update button not added: {}", e.toString());
            }
        });
        Minecraft mc = Minecraft.getInstance();
        UpdateService.startup(mc, a -> {
            if (UpdateService.phase() != UpdateService.Phase.DONE) {
                toast(mc, Tr.t("Holoplayer mod update"), Tr.t("Update available: version {0}", a.latest()));
            }
        });
    }

    private static void addTitleButton(Minecraft mc, Screen screen) {
        UpdateService.Available a = UpdateService.available();
        if (a == null || UpdateService.phase() == UpdateService.Phase.DONE) return;
        var widgets = Screens.getWidgets(screen);
        if (titleButton != null && widgets.contains(titleButton)) return;
        String label = Tr.t("Update Holoplayer mod");
        int w = Math.min(screen.getFont().width(label) + 20, Math.max(80, screen.width / 2 - 8));
        titleButton = Button.builder(Component.literal(label), b -> mc.gui.setScreen(new UpdateScreen(screen, false)))
                .bounds(4, screen.height - 56, w, 20).build();
        widgets.add(titleButton);
    }

    /** The server's hint arrived. Only the official manifest decides; at most one check per connection. */
    public static void prompt(Minecraft mc, UpdatePromptPayload p) {
        if (p.version() != UpdatePromptPayload.VERSION || !UpdateService.enabled() || !PROMPTED.compareAndSet(false, true)) return;
        if (UpdateService.phase() == UpdateService.Phase.MANUAL && UpdateService.available() != null) {
            if (mc.gui.screen() == null) mc.gui.setScreen(new UpdateScreen(null, true));
            return;
        }
        if (UpdateService.available() == null && !UpdateVersion.isNewer(p.latest(), UpdateService.ownVersion())) return;
        UpdateService.checkAsync(mc, a -> {
            if (UpdateService.phase() == UpdateService.Phase.DONE) return;
            if (mc.gui.screen() == null) mc.gui.setScreen(new UpdateScreen(null, true));
            else toast(mc, Tr.t("Holoplayer mod update"), Tr.t("Update available: version {0}", a.latest()));
        });
    }

    public static void reset() {
        PROMPTED.set(false);
    }

    /** The jar is downloaded, verified and scheduled; the game keeps running. */
    static void downloaded(String version) {
        Minecraft mc = Minecraft.getInstance();
        toast(mc, Tr.t("Holoplayer mod update"), Tr.t("Update downloaded. Close Minecraft and start it again to use version {0}.", version));
    }

    private static void toast(Minecraft mc, String title, String body) {
        try {
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(body));
        } catch (RuntimeException e) {
            LOG.info("update toast not shown: {}", e.toString());
        }
    }
}
