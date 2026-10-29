package holorecorder.update;

import holorecorder.Tr;
import holorecorder.menu.TextFit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * "Holoplayer mod update": the versions, why, and one button. A pack-managed instance gets the pack's instructions instead of
 * the button. The download runs on its own thread ({@link UpdateService}); this screen only reads its state.
 */
public final class UpdateScreen extends Screen {
    private static final int BG = 0xFF14171C, BORDER = 0xFF2A2F38, ACCENT = 0xFF4FD8FF, GREY = 0xFF9AA3AD, TEXT = 0xFFE8E8E8,
            BAD = 0xFFFF8A80, GOOD = 0xFF7EE0A8;
    private static final int PAD = 12, GAP = 4;

    private final Screen parent;
    private final boolean fromServer;
    private Button update, later, folder, page;

    public UpdateScreen(Screen parent, boolean fromServer) {
        super(Component.literal(Tr.t("Holoplayer mod update")));
        this.parent = parent;
        this.fromServer = fromServer;
    }

    @Override
    protected void init() {
        update = addRenderableWidget(Button.builder(Component.literal(Tr.t("Update mod")), b -> {
            // a second click while it runs is ignored in startDownload
            UpdateService.startDownload(minecraft, UpdateClient::downloaded);
        }).bounds(0, 0, 100, 20).build());
        folder = addRenderableWidget(Button.builder(Component.literal(Tr.t("Open mods folder")), b -> {
            Path mods = UpdateService.modsDir();
            if (mods != null) PlatformOpen.path(mods);
        }).bounds(0, 0, 100, 20).build());
        page = addRenderableWidget(Button.builder(Component.literal(Tr.t("Open download page")),
                b -> PlatformOpen.uri(URI.create(UpdateService.DOWNLOAD_PAGE))).bounds(0, 0, 100, 20).build());
        later = addRenderableWidget(Button.builder(Component.literal(Tr.t("Later")), b -> onClose()).bounds(0, 0, 100, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Status(String text, int argb) {
    }

    private Status status(UpdateService.Available a) {
        if (a.pack() != PackInstance.NONE) return new Status(UpdateService.packMessage(a.pack()), TEXT);
        if (UpdateService.phase() == UpdateService.Phase.MANUAL && a.file() != null) {
            return new Status(Tr.t("Close Minecraft, delete the old holo-input-recorder jar in your mods folder, then rename {0} to {1}.",
                    a.file() + ".new", a.file()), TEXT);
        }
        if (a.build() == null) return new Status(Tr.t("This Minecraft version has no automatic update. Get the new mod from the Holoplayer website."), TEXT);
        return switch (UpdateService.phase()) {
            case DOWNLOADING -> new Status(Tr.t("Downloading... {0}%", UpdateService.percent()), GREY);
            case DONE -> new Status(Tr.t("Done. Close Minecraft and start it again to use {0}.", a.latest()), GOOD);
            case FAILED -> new Status(Tr.t("Update failed. Try again, or get the mod from the Holoplayer website."), BAD);
            default -> new Status("", TEXT);
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        UpdateService.Available a = UpdateService.available();
        if (a == null) return;
        int panelW = Math.max(120, Math.min(width - 16, 300)), textW = panelW - 2 * PAD, lh = font.lineHeight + 1;
        List<String> reason = TextFit.wrap(fromServer ? Tr.t("This server works best with the newest Holoplayer mod.")
                : Tr.t("A new version of the Holoplayer mod is available."), textW, 2, font::width);
        Status st = status(a);
        List<String> status = TextFit.wrap(st.text(), textW, 7, font::width);
        boolean manual = a.pack() == PackInstance.NONE && UpdateService.phase() == UpdateService.Phase.MANUAL && a.file() != null;
        boolean canUpdate = a.pack() == PackInstance.NONE && a.build() != null && !manual;
        boolean done = UpdateService.phase() == UpdateService.Phase.DONE;
        int bodyLines = 2 + reason.size() + status.size();
        int panelH = PAD + bodyLines * lh + 2 * GAP + (manual ? 2 * 20 + GAP : 20) + PAD;
        int px = (width - panelW) / 2, py = Math.max(2, (height - panelH) / 2);
        g.fill(px, py, px + panelW, py + panelH, BORDER);
        g.fill(px + 1, py + 1, px + panelW - 1, py + panelH - 1, BG);
        g.fill(px + 1, py + 1, px + 4, py + panelH - 1, ACCENT);

        int y = py + PAD;
        g.text(font, Tr.t("Holoplayer mod update"), px + PAD, y, 0xFFFFFFFF, true);
        y += lh;
        g.text(font, Tr.t("Version {0} -> {1}", a.current(), a.latest()), px + PAD, y, GREY, false);
        y += lh + GAP;
        for (String line : reason) {
            g.text(font, line, px + PAD, y, TEXT, false);
            y += lh;
        }
        for (String line : status) {
            g.text(font, line, px + PAD, y, st.argb(), false);
            y += lh;
        }
        y += GAP;

        folder.visible = page.visible = manual;
        if (manual) {
            int half = (textW - GAP) / 2;
            folder.setWidth(half);
            folder.setPosition(px + PAD, y);
            page.setWidth(half);
            page.setPosition(px + PAD + half + GAP, y);
            y += 20 + GAP;
        }
        int bw = canUpdate ? (textW - GAP) / 2 : Math.min(textW, 120);
        update.visible = canUpdate && !done;
        update.active = UpdateService.phase() != UpdateService.Phase.DOWNLOADING;
        later.setMessage(Component.literal(done ? Tr.t("Close") : Tr.t("Later")));
        later.setWidth(update.visible ? bw : Math.min(textW, 120));
        later.setPosition(update.visible ? px + PAD + bw + GAP : px + (panelW - later.getWidth()) / 2, y);
        update.setWidth(bw);
        update.setPosition(px + PAD, y);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
