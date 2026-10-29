package holorecorder.improve;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.improve.ImproveLayout.Fill;
import holorecorder.improve.ImproveLayout.Icon;
import holorecorder.improve.ImproveLayout.Prim;
import holorecorder.improve.ImproveLayout.Text;
import holorecorder.menu.MenuLayout.Rect;
import holorecorder.protocol.ImproveActionPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The Improve screen: a centred panel with a header, tabs, a scrolling list of cards, rows, notes and tables, and a footer
 * with Back and Close. {@link ImproveLayout} works out every rectangle and text line from the screen size; this class only
 * draws them and handles the mouse and keys. All text comes from the server already translated.
 */
public final class ImproveScreen extends Screen {
    private static final int HOVER = 0xFF2A3342, BUTTON = 0xFF222833, BUTTON_HOVER = 0xFF303A4C, THUMB = 0xFF5B677C;

    private ImproveModel.Screen data;
    private int tab;
    private ImproveLayout.Result layout;
    private double scroll;
    private boolean draggingBar, actionSent;
    private final Map<String, ItemStack> stacks = new HashMap<>();

    public ImproveScreen(ImproveModel.Screen data) {
        super(Component.literal(data.title()));
        this.data = data;
        this.tab = data.tab();
    }

    /** The server sent the screen again (new numbers after a round): show it, and keep the tab and scroll when the tabs are the same. */
    void replace(ImproveModel.Screen next) {
        boolean same = next.tabs().size() == data.tabs().size();
        for (int i = 0; same && i < next.tabs().size(); i++) same = next.tabs().get(i).label().equals(data.tabs().get(i).label());
        data = next;
        if (!same) {
            tab = next.tab();
            scroll = 0;
        }
        layoutAgain();
    }

    @Override
    protected void init() {
        layoutAgain();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack stackOf(String id) {
        return stacks.computeIfAbsent(id, key -> {
            try {
                Identifier ident = Identifier.tryParse(key);
                return ident == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(ident));
            } catch (RuntimeException e) {
                return ItemStack.EMPTY;
            }
        });
    }

    private void layoutAgain() {
        if (font == null || width <= 0) return;
        Font f = font;
        layout = ImproveLayout.compute(width, height, new ImproveLayout.Meter() {
            @Override public int lineHeight() { return f.lineHeight; }
            @Override public int width(String text) { return f.width(text); }
            @Override public boolean iconKnown(String itemId) { return !stackOf(itemId).isEmpty(); }
        }, data);
        scroll = Math.max(0, Math.min(layout.maxScroll(tab), scroll));
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static void box(GuiGraphicsExtractor g, Rect r, int fill, int border) {
        g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), border);
        g.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);
    }

    private void centred(GuiGraphicsExtractor g, Rect r, String text, int argb) {
        g.text(font, text, r.x() + (r.w() - font.width(text)) / 2, r.y() + (r.h() - font.lineHeight) / 2 + 1, argb, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (layout == null) layoutAgain();
        if (layout == null) return;
        Font f = font;
        Rect panel = layout.panel(), vp = layout.viewport();
        box(g, panel, ImproveLayout.BG, ImproveLayout.BORDER);
        int headBottom = layout.titleBar().y() + layout.titleBar().h();
        g.fill(panel.x() + 1, headBottom - 1, panel.x() + panel.w() - 1, headBottom, ImproveLayout.BORDER);
        g.fill(panel.x() + 1, panel.y() + 1, panel.x() + 4, headBottom - 1, ImproveLayout.ACCENT);
        for (Text t : layout.header()) g.text(f, t.text(), t.x(), t.y(), t.argb(), t.shadow());

        for (ImproveLayout.TabBox t : layout.tabs()) {
            boolean on = t.index() == tab, hover = t.box().contains(mouseX, mouseY);
            box(g, t.box(), on ? 0xFF222833 : hover ? HOVER : ImproveLayout.BG, on ? ImproveLayout.ACCENT : ImproveLayout.BORDER);
            centred(g, t.box(), t.label(), on || hover ? 0xFFFFFFFF : ImproveLayout.GREY);
        }

        ImproveLayout.Content content = layout.contents().get(tab);
        g.enableScissor(vp.x(), vp.y(), vp.x() + vp.w(), vp.y() + vp.h());
        int ox = vp.x(), oy = vp.y() - (int) Math.round(scroll);
        for (Prim p : content.prims()) {
            if (p instanceof Fill r) {
                if (oy + r.y() > vp.y() + vp.h() || oy + r.y() + r.h() < vp.y()) continue;
                g.fill(ox + r.x(), oy + r.y(), ox + r.x() + r.w(), oy + r.y() + r.h(), r.argb());
            } else if (p instanceof Text t) {
                if (oy + t.y() > vp.y() + vp.h() || oy + t.y() + f.lineHeight < vp.y()) continue;
                g.text(f, t.text(), ox + t.x(), oy + t.y(), t.argb(), t.shadow());
            } else if (p instanceof Icon i) {
                if (oy + i.y() > vp.y() + vp.h() || oy + i.y() + ImproveLayout.ICON < vp.y()) continue;
                g.item(stackOf(i.itemId()), ox + i.x(), oy + i.y());
            }
        }
        g.disableScissor();
        drawScrollbar(g, vp);

        Rect foot = layout.footer();
        g.fill(foot.x() + 1, foot.y(), foot.x() + foot.w() - 1, foot.y() + 1, ImproveLayout.BORDER);
        for (Text t : content.footer()) g.text(f, t.text(), t.x(), t.y(), t.argb(), t.shadow());
        button(g, layout.back(), layout.backLabel(), mouseX, mouseY);
        button(g, layout.close(), layout.closeLabel(), mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor g, Rect r, String label, int mouseX, int mouseY) {
        box(g, r, r.contains(mouseX, mouseY) ? BUTTON_HOVER : BUTTON, 0xFF3A414C);
        centred(g, r, label, 0xFFFFFFFF);
    }

    private int thumbHeight(Rect vp) {
        return Math.max(12, vp.h() * vp.h() / Math.max(1, layout.contents().get(tab).height()));
    }

    private void drawScrollbar(GuiGraphicsExtractor g, Rect vp) {
        int max = layout.maxScroll(tab);
        if (max <= 0 || vp.h() <= 0) return;
        int x = vp.x() + layout.contentWidth() + 2;
        g.fill(x, vp.y(), x + 3, vp.y() + vp.h(), ImproveLayout.TRACK);
        int thumb = Math.min(vp.h(), thumbHeight(vp));
        int ty = vp.y() + (int) Math.round((vp.h() - thumb) * (scroll / max));
        g.fill(x, ty, x + 3, ty + thumb, THUMB);
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void setTab(int index) {
        if (index < 0 || index >= data.tabs().size() || index == tab) return;
        tab = index;
        scroll = 0;
    }

    private void sendOnce(String action) {
        if (actionSent) return;
        actionSent = true;
        ImproveClient.send(action);
    }

    /** Esc, the inventory key and the Close button all end here: tell the server once, then close. */
    @Override
    public void onClose() {
        sendOnce(ImproveActionPayload.CLOSE);
        super.onClose();
    }

    /** Any other way the screen goes away (death screen, another screen on top): the server still hears "close", once. */
    @Override
    public void removed() {
        sendOnce(ImproveActionPayload.CLOSE);
    }

    private void back() {
        sendOnce(ImproveActionPayload.BACK);
        super.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (layout == null) return false;
        if (e.button() == 0) {
            if (layout.close().contains(e.x(), e.y())) {
                click();
                onClose();
                return true;
            }
            if (layout.back().contains(e.x(), e.y())) {
                click();
                back();
                return true;
            }
            for (ImproveLayout.TabBox t : layout.tabs()) {
                if (t.box().contains(e.x(), e.y())) {
                    click();
                    setTab(t.index());
                    return true;
                }
            }
            Rect vp = layout.viewport();
            if (layout.maxScroll(tab) > 0) {
                int barX = vp.x() + layout.contentWidth();
                if (e.x() >= barX && e.x() < barX + ImproveLayout.SCROLLBAR && e.y() >= vp.y() && e.y() < vp.y() + vp.h()) {
                    draggingBar = true;
                    dragTo(e.y());
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    private void dragTo(double my) {
        Rect vp = layout.viewport();
        int thumb = Math.min(vp.h(), thumbHeight(vp));
        double k = (my - vp.y() - thumb / 2.0) / Math.max(1, vp.h() - thumb);
        scroll = Math.max(0, Math.min(layout.maxScroll(tab), k * layout.maxScroll(tab)));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar && layout != null) {
            dragTo(e.y());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        draggingBar = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (layout == null || layout.maxScroll(tab) <= 0) return false;
        scroll = Math.max(0, Math.min(layout.maxScroll(tab), scroll - v * 18));
        return true;
    }

    private void scrollBy(double d) {
        scroll = Math.max(0, Math.min(layout.maxScroll(tab), scroll + d));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (layout == null) return super.keyPressed(e);
        if (minecraft.options.keyInventory.matches(e) && !e.isEscape()) {
            onClose();
            return true;
        }
        if (e.isDown()) { scrollBy(18); return true; }
        if (e.isUp()) { scrollBy(-18); return true; }
        if (e.key() == InputConstants.KEY_PAGEDOWN) { scrollBy(layout.viewport().h() * 0.8); return true; }
        if (e.key() == InputConstants.KEY_PAGEUP) { scrollBy(-layout.viewport().h() * 0.8); return true; }
        if (e.key() == InputConstants.KEY_HOME) { scroll = 0; return true; }
        if (e.key() == InputConstants.KEY_END) { scroll = layout.maxScroll(tab); return true; }
        if (e.isRight() || e.isLeft() || e.key() == InputConstants.KEY_TAB) {
            int step = e.isLeft() || (e.key() == InputConstants.KEY_TAB && e.hasShiftDown()) ? -1 : 1;
            setTab(Math.floorMod(tab + step, data.tabs().size()));
            return true;
        }
        return super.keyPressed(e);
    }
}
