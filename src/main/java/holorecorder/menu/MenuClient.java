package holorecorder.menu;

import holorecorder.protocol.MenuClickPayload;
import holorecorder.protocol.MenuClosePayload;
import holorecorder.protocol.MenuClosedPayload;
import holorecorder.protocol.MenuOpenPayload;
import holorecorder.protocol.MenuUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Receives the server's custom menus and sends back the player's clicks. Every method runs on the client thread. */
public final class MenuClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("holorec-client");

    private MenuClient() {
    }

    public static void open(Minecraft mc, MenuOpenPayload p) {
        MenuState state = MenuState.of(p);
        if (state == null) return;
        LOG.info("holorec: custom menu '{}' opened ({} tiles)", state.title.getString(), state.tiles().size());
        if (mc.gui.screen() instanceof CustomMenuScreen screen) screen.replace(state);
        else mc.gui.setScreen(new CustomMenuScreen(state));
    }

    public static void update(Minecraft mc, MenuUpdatePayload p) {
        if (mc.gui.screen() instanceof CustomMenuScreen screen) screen.update(p);
    }

    /** The server closed its menu: drop the screen if it still shows that menu. */
    public static void close(Minecraft mc, MenuClosePayload p) {
        if (p.version() != MenuOpenPayload.VERSION) return;
        if (mc.gui.screen() instanceof CustomMenuScreen screen && screen.menuId() == p.menuId()) {
            screen.closedByServer();
            mc.gui.setScreen(null);
        }
    }

    /** kind: 0 left click, 1 right click, 2 shift-click. */
    static void click(int menuId, int slot, int kind) {
        if (ClientPlayNetworking.canSend(MenuClickPayload.TYPE)) {
            ClientPlayNetworking.send(new MenuClickPayload(MenuOpenPayload.VERSION, menuId, slot, kind));
        }
    }

    static void dismissed(int menuId) {
        if (ClientPlayNetworking.canSend(MenuClosedPayload.TYPE)) {
            ClientPlayNetworking.send(new MenuClosedPayload(MenuOpenPayload.VERSION, menuId));
        }
    }
}
