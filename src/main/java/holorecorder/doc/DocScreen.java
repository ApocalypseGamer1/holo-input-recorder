package holorecorder.doc;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.doc.DocLayout.Fill;
import holorecorder.doc.DocLayout.Prim;
import holorecorder.doc.DocLayout.Text;
import holorecorder.menu.MenuLayout.Rect;
import holorecorder.protocol.DocActionPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The paged document screen (guide, rules, privacy): a dark panel with a coloured header, a chapter list on the left, the
 * page with a scrollbar, and Back / Prev / Next / Close at the bottom. {@link DocLayout} works out every rectangle and text
 * line from the screen size; this class only draws them and handles the mouse and keys. All text comes from the server,
 * already translated.
 */
public final class DocScreen extends Screen {
    private static final int HOVER = 0xFF2A3342, BUTTON = 0xFF222833, BUTTON_HOVER = 0xFF303A4C, BUTTON_OFF = 0xFF191D23,
            THUMB = 0xFF5B677C, BUTTON_BORDER = 0xFF3A414C, DIM = 0xFF5C6570;

    private DocModel.Doc data;
    private int page;
    private DocLayout.Result layout;
    private double scroll, listScroll;
    private boolean draggingBar, actionSent;

    public DocScreen(DocModel.Doc data) {
        super(Component.literal(data.title()));
        this.data = data;
        this.page = data.page();
    }

    /** The server sent a document while this one is open: show it from its start page. */
    void replace(DocModel.Doc next) {
        data = next;
        page = next.page();
        scroll = 0;
        listScroll = 0;
        actionSent = false;
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

    private void layoutAgain() {
        if (font == null || width <= 0) return;
        Font f = font;
        layout = DocLayout.compute(width, height, new DocLayout.Meter() {
            @Override public int lineHeight() { return f.lineHeight; }
            @Override public int width(String text) { return f.width(text); }
        }, data);
        scroll = Math.max(0, Math.min(layout.maxScroll(page), scroll));
        listScroll = Math.max(0, Math.min(layout.maxListScroll(), listScroll));
        showChapter();
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
        int accent = layout.accent();
        Rect panel = layout.panel(), vp = layout.viewport(), head = layout.header();
        box(g, panel, DocLayout.BG, DocLayout.BORDER);

        // header bar in the document's colour, with a progress line
        g.fill(head.x(), head.y(), head.x() + head.w(), head.y() + head.h(), DocLayout.mix(accent, 0xFF14171C, 0.30));
        g.fill(head.x(), head.y(), head.x() + 3, head.y() + head.h(), accent);
        Rect pr = layout.progress();
        g.fill(pr.x(), pr.y(), pr.x() + pr.w(), pr.y() + pr.h(), DocLayout.TRACK);
        int pages = data.pages().size();
        int filled = pages <= 1 ? pr.w() : (int) Math.round(pr.w() * (page + 1) / (double) pages);
        g.fill(pr.x(), pr.y(), pr.x() + filled, pr.y() + pr.h(), accent);
        for (Text t : layout.headerText()) g.text(f, t.text(), t.x(), t.y(), t.argb(), t.shadow());

        drawList(g, mouseX, mouseY);

        DocLayout.Content content = layout.contents().get(page);
        g.enableScissor(vp.x(), vp.y(), vp.x() + vp.w(), vp.y() + vp.h());
        int ox = vp.x(), oy = vp.y() - (int) Math.round(scroll);
        for (Prim p : content.prims()) {
            if (p instanceof Fill r) {
                if (oy + r.y() > vp.y() + vp.h() || oy + r.y() + r.h() < vp.y()) continue;
                g.fill(ox + r.x(), oy + r.y(), ox + r.x() + r.w(), oy + r.y() + r.h(), r.argb());
            } else if (p instanceof Text t) {
                if (oy + t.y() > vp.y() + vp.h() || oy + t.y() + f.lineHeight < vp.y()) continue;
                g.text(f, t.text(), ox + t.x(), oy + t.y(), t.argb(), t.shadow());
            }
        }
        g.disableScissor();
        drawScrollbar(g, vp);

        Rect foot = layout.footer();
        g.fill(foot.x(), foot.y(), foot.x() + foot.w(), foot.y() + 1, DocLayout.BORDER);
        if (layout.back() != null) button(g, layout.back().box(), layout.back().label(), mouseX, mouseY, true);
        button(g, layout.close().box(), layout.close().label(), mouseX, mouseY, true);
        if (layout.prev() != null) {
            button(g, layout.prev(), "<", mouseX, mouseY, page > 0);
            button(g, layout.next(), ">", mouseX, mouseY, page < pages - 1);
            centred(g, layout.counter(), (page + 1) + " / " + pages, DocLayout.GREY);
        }
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect list = layout.list();
        if (list == null) return;
        int accent = layout.accent();
        g.fill(list.x(), list.y(), list.x() + list.w(), list.y() + list.h(), DocLayout.LIST_BG);
        g.fill(list.x() + list.w() - 1, list.y(), list.x() + list.w(), list.y() + list.h(), DocLayout.BORDER);
        g.enableScissor(list.x(), list.y(), list.x() + list.w() - 1, list.y() + list.h());
        int oy = list.y() - (int) Math.round(listScroll);
        for (DocLayout.ChapterBox c : layout.chapters()) {
            Rect r = new Rect(list.x() + c.box().x(), oy + c.box().y(), c.box().w(), c.box().h());
            if (r.y() + r.h() < list.y() || r.y() > list.y() + list.h()) continue;
            boolean on = c.index() == page, hover = !on && list.contains(mouseX, mouseY) && r.contains(mouseX, mouseY);
            if (on) {
                g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), DocLayout.mix(accent, 0xFF14171C, 0.28));
                g.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), accent);
            } else if (hover) {
                g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), HOVER);
            }
            g.text(font, c.label(), r.x() + 6, r.y() + (r.h() - font.lineHeight) / 2 + 1, on || hover ? 0xFFFFFFFF : DocLayout.GREY, on);
        }
        g.disableScissor();
        int max = layout.maxListScroll();
        if (max > 0) {
            int thumb = Math.max(10, list.h() * list.h() / Math.max(1, layout.listHeight()));
            int ty = list.y() + (int) Math.round((list.h() - thumb) * (listScroll / max));
            g.fill(list.x() + list.w() - 3, ty, list.x() + list.w() - 1, ty + thumb, THUMB);
        }
    }

    private void button(GuiGraphicsExtractor g, Rect r, String label, int mouseX, int mouseY, boolean enabled) {
        box(g, r, !enabled ? BUTTON_OFF : r.contains(mouseX, mouseY) ? BUTTON_HOVER : BUTTON, BUTTON_BORDER);
        centred(g, r, label, enabled ? 0xFFFFFFFF : DIM);
    }

    private int thumbHeight(Rect vp) {
        return Math.max(12, vp.h() * vp.h() / Math.max(1, layout.contents().get(page).height()));
    }

    private void drawScrollbar(GuiGraphicsExtractor g, Rect vp) {
        int max = layout.maxScroll(page);
        if (max <= 0 || vp.h() <= 0) return;
        int x = vp.x() + layout.contentWidth() + 2;
        g.fill(x, vp.y(), x + 3, vp.y() + vp.h(), DocLayout.TRACK);
        int thumb = Math.min(vp.h(), thumbHeight(vp));
        int ty = vp.y() + (int) Math.round((vp.h() - thumb) * (scroll / max));
        g.fill(x, ty, x + 3, ty + thumb, THUMB);
    }

    // ---- pages ------------------------------------------------------------------------------------------------------

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void setPage(int index) {
        int last = data.pages().size() - 1;
        index = Math.max(0, Math.min(last, index));
        if (index == page) return;
        page = index;
        scroll = 0;
        showChapter();
    }

    /** Keeps the current chapter inside the list's view. */
    private void showChapter() {
        if (layout == null || layout.list() == null || page >= layout.chapters().size()) return;
        Rect b = layout.chapters().get(page).box();
        int h = layout.list().h();
        if (b.y() < listScroll) listScroll = Math.max(0, b.y() - 3);
        else if (b.y() + b.h() > listScroll + h) listScroll = b.y() + b.h() + 3 - h;
        listScroll = Math.max(0, Math.min(layout.maxListScroll(), listScroll));
    }

    // ---- closing ----------------------------------------------------------------------------------------------------

    private void sendOnce(String action) {
        if (actionSent) return;
        actionSent = true;
        DocClient.send(action);
    }

    /** Esc, the inventory key and the Close button all end here: tell the server once, then close. */
    @Override
    public void onClose() {
        sendOnce(DocActionPayload.CLOSE);
        super.onClose();
    }

    /** Any other way the screen goes away (death screen, another screen on top): the server still hears "close", once. */
    @Override
    public void removed() {
        sendOnce(DocActionPayload.CLOSE);
    }

    private void back() {
        sendOnce(DocActionPayload.BACK);
        super.onClose();
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (layout == null) return false;
        if (e.button() == 0) {
            double x = e.x(), y = e.y();
            if (layout.close().box().contains(x, y)) {
                click();
                onClose();
                return true;
            }
            if (layout.back() != null && layout.back().box().contains(x, y)) {
                click();
                back();
                return true;
            }
            if (layout.prev() != null) {
                if (layout.prev().contains(x, y)) {
                    if (page > 0) click();
                    setPage(page - 1);
                    return true;
                }
                if (layout.next().contains(x, y)) {
                    if (page < data.pages().size() - 1) click();
                    setPage(page + 1);
                    return true;
                }
            }
            Rect list = layout.list();
            if (list != null && list.contains(x, y)) {
                for (DocLayout.ChapterBox c : layout.chapters()) {
                    Rect r = new Rect(list.x() + c.box().x(), list.y() - (int) Math.round(listScroll) + c.box().y(), c.box().w(), c.box().h());
                    if (r.contains(x, y)) {
                        click();
                        setPage(c.index());
                        return true;
                    }
                }
                return true;
            }
            Rect vp = layout.viewport();
            if (layout.maxScroll(page) > 0) {
                int barX = vp.x() + layout.contentWidth();
                if (x >= barX && x < barX + DocLayout.SCROLLBAR && y >= vp.y() && y < vp.y() + vp.h()) {
                    draggingBar = true;
                    dragTo(y);
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
        scroll = Math.max(0, Math.min(layout.maxScroll(page), k * layout.maxScroll(page)));
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

    /** The wheel scrolls the chapter list over it, a long page over the page, and turns the page when the page fits. */
    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (layout == null) return false;
        Rect list = layout.list();
        if (list != null && list.contains(x, y)) {
            listScroll = Math.max(0, Math.min(layout.maxListScroll(), listScroll - v * 18));
            return true;
        }
        if (layout.maxScroll(page) > 0) {
            scroll = Math.max(0, Math.min(layout.maxScroll(page), scroll - v * 18));
        } else if (v != 0) {
            setPage(page + (v < 0 ? 1 : -1));
        }
        return true;
    }

    private void scrollBy(double d) {
        scroll = Math.max(0, Math.min(layout.maxScroll(page), scroll + d));
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
        if (e.key() == InputConstants.KEY_HOME) { setPage(0); return true; }
        if (e.key() == InputConstants.KEY_END) { setPage(data.pages().size() - 1); return true; }
        if (e.isRight()) { setPage(page + 1); return true; }
        if (e.isLeft()) { setPage(page - 1); return true; }
        if (e.key() == InputConstants.KEY_TAB) {
            setPage(Math.floorMod(page + (e.hasShiftDown() ? -1 : 1), data.pages().size()));
            return true;
        }
        return super.keyPressed(e);
    }

    /** Test and demo hook: the page now shown. */
    int page() {
        return page;
    }
}
