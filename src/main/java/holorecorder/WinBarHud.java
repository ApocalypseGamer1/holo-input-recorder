package holorecorder;

import holorecorder.protocol.WinBarPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Vertical win bar on the left edge: the viewer's side at the bottom, light below, dark above. */
final class WinBarHud {
    private static final int LIGHT = 0xFFEDEDED, DARK = 0xFF3A3A3A, OUTLINE = 0xFF101010, TICK = 0xFF8A8A8A;
    private static final int ON_LIGHT = 0xFF1A1A1A, ON_DARK = 0xFFF2F2F2;
    private static final int BAR_WIDTH = 10, LEFT = 3, NAME_WIDTH = 56;
    private static final WinBarState STATE = new WinBarState();
    private static final boolean DEMO = Boolean.getBoolean("holorecorder.winbarDemo");
    private static long demoNextMs;

    static void register() {
        // Before the chat so chat text stays on top of the bar.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("holo_input_recorder", "winbar"), WinBarHud::extract);
    }

    static void receive(Minecraft client, WinBarPayload p) {
        STATE.update(p.active(), p.pA(), p.nameA(), p.nameB(), p.viewer(), client.level, System.currentTimeMillis());
    }

    static void reset() {
        STATE.reset();
    }

    /** Dev only: feeds a moving fake value when started with -Dholorecorder.winbarDemo=true. */
    static void demoTick(Minecraft client) {
        if (!DEMO || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now < demoNextMs) return;
        demoNextMs = now + 100;
        double t = now / 1000.0;
        receive(client, new WinBarPayload(true, (float) (0.5 + 0.48 * Math.sin(t * 0.7)), "Alpha", "Bravo",
                (byte) (((long) (t / 12)) % 3)));
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()
                || !STATE.visible(now, mc.level)) return;
        STATE.step(now);

        Font font = mc.font;
        int guiH = mc.getWindow().getGuiScaledHeight();
        int h = Math.max(40, Math.round(guiH * 0.45f));
        int y0 = (guiH - h) / 2, x1 = LEFT + BAR_WIDTH;
        double bottom = STATE.shownBottom();
        int split = y0 + Math.round((float) (h * (1.0 - bottom)));

        g.fill(LEFT - 1, y0 - 1, x1 + 1, y0 + h + 1, OUTLINE);
        g.fill(LEFT, y0, x1, split, DARK);
        g.fill(LEFT, split, x1, y0 + h, LIGHT);
        g.fill(LEFT, y0 + h / 2, x1, y0 + h / 2 + 1, TICK);
        g.fill(x1 + 1, y0 + h / 2, x1 + 3, y0 + h / 2 + 1, OUTLINE);

        boolean bottomLeads = STATE.bottomLeads();
        String text = WinBarState.leaderText(bottom);
        int chipW = font.width(text) + 5, chipH = font.lineHeight + 3;
        int chipX = x1 + 1, chipY = bottomLeads ? y0 + h - chipH : y0;
        g.fill(chipX, chipY - 1, chipX + chipW + 1, chipY + chipH + 1, OUTLINE);
        g.fill(chipX, chipY, chipX + chipW, chipY + chipH, bottomLeads ? LIGHT : DARK);
        g.text(font, text, chipX + 3, chipY + 2, bottomLeads ? ON_LIGHT : ON_DARK, false);

        String top = font.plainSubstrByWidth(STATE.topName(), NAME_WIDTH);
        String low = font.plainSubstrByWidth(STATE.bottomName(), NAME_WIDTH);
        if (y0 - 3 - font.lineHeight >= 0) g.text(font, top, LEFT, y0 - 3 - font.lineHeight, 0xFFC8C8C8, true);
        if (y0 + h + 3 + font.lineHeight <= guiH) g.text(font, low, LEFT, y0 + h + 3, 0xFFFFFFFF, true);
    }
}
