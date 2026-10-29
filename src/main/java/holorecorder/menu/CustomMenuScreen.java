package holorecorder.menu;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.Tr;
import holorecorder.protocol.MenuEntry;
import holorecorder.protocol.MenuUpdatePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A server menu drawn by the mod: a centred panel with a title, one card per tile (icon, name, a one-line subtitle, the
 * full text on hover), headings for groups of tiles, and a Back / Close footer. Works at every GUI scale and in every
 * language: {@link MenuLayout} places the cards from the screen size, {@link TextFit} wraps or shortens every text to its
 * box. Clicks go back to the server as {@code (menu id, slot, click type)}; the server decides what they do.
 */
public final class CustomMenuScreen extends Screen {
    private static final int PANEL = 0xF2111419, BORDER = 0xFF2B3240, TITLE_BG = 0xFF171B23, CARD = 0xFF1B2029, CARD_HOVER = 0xFF2A3342,
            ACCENT = 0xFF4FD8FF, TEXT = 0xFFFFFFFF, MUTED = 0xFFA9B0BC, HEADING = 0xFF8FA3B8, DIM = 0xFF6F7682, FOCUS = 0xFFFFFFFF,
            BUTTON = 0xFF222834, BUTTON_HOVER = 0xFF303A4C, TRACK = 0xFF0C0E12, THUMB = 0xFF5B677C, DARK_GRAY_RGB = 0x555555;
    private static final int FOCUS_BACK = -2, FOCUS_CLOSE = -3;

    /** Everything drawn for one tile, worked out once per layout. */
    private record Prepared(MenuEntry entry, ItemStack stack, List<String> nameLines, int nameColor, List<String> subLines,
                            int subColor, int accent, boolean dim, boolean cut) {
    }

    private MenuState state;
    private MenuLayout.Result layout;
    private final Map<Integer, Prepared> prepared = new HashMap<>();
    private Prepared backTile;
    private String backName = "", backSub = "", closeLabel = "";
    private double scroll;
    private int focus = Integer.MIN_VALUE;
    private int shownRevision = -1;
    private boolean draggingBar;
    private boolean serverClosed;

    public CustomMenuScreen(MenuState state) {
        super(state.title);
        this.state = state;
    }

    public int menuId() {
        return state.id;
    }

    /** The server opened another menu while this one was up: show it in the same screen. */
    void replace(MenuState next) {
        boolean sameTitle = next.title.getString().equals(state.title.getString());
        state = next;
        if (!sameTitle) {
            scroll = 0;
            focus = Integer.MIN_VALUE;
        }
        layoutAgain();
    }

    void update(MenuUpdatePayload p) {
        if (state.apply(p)) layoutAgain();
    }

    void closedByServer() {
        serverClosed = true;
    }

    @Override
    protected void init() {
        layoutAgain();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!serverClosed) MenuClient.dismissed(state.id);
    }

    // ---- layout -----------------------------------------------------------------------------------------------------

    private int width(String s) {
        return font.width(s);
    }

    private static String nameOf(MenuEntry e) {
        return e.name().getString();
    }

    /** The first non-blank lore line: the subtitle. */
    private static Component subtitleOf(MenuEntry e) {
        for (Component c : e.lore()) if (!c.getString().isBlank()) return c;
        return null;
    }

    private static Optional<Integer> colourOf(Component c) {
        return c.visit((style, text) -> text.isBlank() || style.getColor() == null ? Optional.empty()
                : Optional.of(style.getColor().getValue()), Style.EMPTY);
    }

    private static ItemStack stackOf(String id) {
        try {
            Identifier key = Identifier.tryParse(id);
            ItemStack stack = key == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(key));
            return stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;
        } catch (RuntimeException e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private void layoutAgain() {
        if (font == null || width <= 0) return;
        shownRevision = state.revision();
        prepared.clear();
        MenuLayout.Meter meter = new MenuLayout.Meter() {
            @Override public int lineHeight() { return font.lineHeight; }
            @Override public int nameLines(int slot, int textWidth) {
                MenuEntry e = state.tile(slot);
                return e == null ? 1 : Math.max(1, TextFit.wrap(nameOf(e), textWidth, 2, CustomMenuScreen.this::width).size());
            }
            @Override public int subLines(int slot, int textWidth) {
                MenuEntry e = state.tile(slot);
                Component sub = e == null ? null : subtitleOf(e);
                return sub == null ? 0 : TextFit.wrap(sub.getString(), textWidth, 2, CustomMenuScreen.this::width).size();
            }
        };
        List<MenuLayout.Item> items = new ArrayList<>();
        Set<Integer> headingRows = new HashSet<>();
        for (MenuEntry e : state.tiles()) {
            if (state.hasBack() && e.slot() == state.backSlot) continue;
            items.add(new MenuLayout.Item(e.slot(), e.slot() / 9));
            if (state.sectionOf(e.slot() / 9) != null) headingRows.add(e.slot() / 9);
        }
        closeLabel = Tr.t("Close");
        int backLabelW = -1;
        if (state.hasBack()) {
            MenuEntry b = state.tile(state.backSlot);
            backName = nameOf(b);
            Component sub = subtitleOf(b);
            backSub = sub == null ? "" : sub.getString();
            backLabelW = Math.max(width(backName), width(backSub));
            backLabelW += 20;   // the arrow icon
        }
        layout = MenuLayout.compute(width, height, meter, items, headingRows, backLabelW, width(closeLabel));
        for (MenuLayout.Card c : layout.cards()) prepared.put(c.slot(), prepare(state.tile(c.slot()), c));
        backTile = state.hasBack() ? prepare(state.tile(state.backSlot), null) : null;
        scroll = Math.max(0, Math.min(layout.maxScroll(), scroll));
    }

    private Prepared prepare(MenuEntry e, MenuLayout.Card card) {
        int textW = card == null ? 1000 : card.textW();
        List<String> name = TextFit.wrap(nameOf(e), textW, 2, this::width);
        Optional<Integer> nameRgb = colourOf(e.name());
        boolean dim = nameRgb.isPresent() && nameRgb.get() == DARK_GRAY_RGB;
        int nameColor = dim ? DIM : 0xFF000000 | nameRgb.orElse(0xFFFFFF);
        Component sub = subtitleOf(e);
        List<String> subLines = sub == null ? List.of() : TextFit.wrap(sub.getString(), textW, 2, this::width);
        int subColor = sub == null ? MUTED : 0xFF000000 | colourOf(sub).orElse(0xA9B0BC);
        if (dim) subColor = DIM;
        int accent = dim ? DIM : nameRgb.map(rgb -> rgb == 0xFFFFFF || rgb == 0xAAAAAA ? ACCENT : 0xFF000000 | rgb).orElse(ACCENT);
        boolean cut = !name.isEmpty() && name.get(name.size() - 1).endsWith(TextFit.ELLIPSIS) && !nameOf(e).endsWith(TextFit.ELLIPSIS)
                || !subLines.isEmpty() && subLines.get(subLines.size() - 1).endsWith(TextFit.ELLIPSIS) && sub != null
                && !sub.getString().endsWith(TextFit.ELLIPSIS);
        return new Prepared(e, stackOf(e.item()), name, nameColor, subLines, subColor, accent, dim, cut);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static void box(GuiGraphicsExtractor g, MenuLayout.Rect r, int fill, int border) {
        g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), border);
        g.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (layout == null || shownRevision != state.revision()) layoutAgain();
        Font f = font;
        MenuLayout.Rect panel = layout.panel(), vp = layout.viewport();
        box(g, panel, PANEL, BORDER);
        g.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.w() - 1, panel.y() + layout.titleBar().h(), TITLE_BG);
        g.fill(panel.x() + 1, panel.y() + layout.titleBar().h(), panel.x() + panel.w() - 1, panel.y() + layout.titleBar().h() + 1, BORDER);
        g.fill(panel.x() + 1, panel.y() + 1, panel.x() + 3, panel.y() + layout.titleBar().h(), ACCENT);
        String title = TextFit.ellipsize(state.title.getString(), panel.w() - 2 * MenuLayout.PAD - 6, this::width);
        g.text(f, title, panel.x() + MenuLayout.PAD + 2, panel.y() + (layout.titleBar().h() - f.lineHeight) / 2 + 1, TEXT, true);

        int hover = cardAt(mouseX, mouseY);
        g.enableScissor(vp.x(), vp.y(), vp.x() + vp.w(), vp.y() + vp.h());
        int oy = vp.y() - (int) Math.round(scroll);
        for (MenuLayout.Heading h : layout.headings()) {
            Component label = state.sectionOf(h.row());
            if (label == null) continue;
            String text = TextFit.ellipsize(label.getString(), h.box().w() - 8, this::width);
            int y = oy + h.box().y() + 2;
            g.text(f, text, vp.x() + h.box().x() + 2, y, HEADING, false);
            int lineX = vp.x() + h.box().x() + 2 + width(text) + 5;
            g.fill(lineX, y + f.lineHeight / 2, vp.x() + h.box().x() + h.box().w(), y + f.lineHeight / 2 + 1, BORDER);
        }
        for (MenuLayout.Card c : layout.cards()) {
            int top = oy + c.box().y();
            if (top + c.box().h() < vp.y() || top > vp.y() + vp.h()) continue;
            drawCard(g, f, c, vp.x() + c.box().x(), top, c.slot() == hover, c.slot() == focus);
        }
        g.disableScissor();
        drawScrollbar(g, vp);
        drawFooter(g, f, mouseX, mouseY);
        drawTooltip(g, mouseX, mouseY, hover);
    }

    private void drawCard(GuiGraphicsExtractor g, Font f, MenuLayout.Card c, int x, int y, boolean hover, boolean focused) {
        Prepared p = prepared.get(c.slot());
        if (p == null) return;
        int w = c.box().w(), h = c.box().h();
        g.fill(x, y, x + w, y + h, hover || focused ? CARD_HOVER : CARD);
        g.fill(x, y, x + 2, y + h, p.accent());
        int ix = x + MenuLayout.CARD_PAD + 2, iy = y + (h - 16) / 2;
        g.item(p.stack(), ix, iy);
        if (p.dim()) g.fill(ix, iy, ix + 16, iy + 16, 0x99111419);
        int ty = y + MenuLayout.CARD_PAD;
        int tx = x + c.textX();
        for (String line : p.nameLines()) {
            g.text(f, line, tx, ty, p.nameColor(), true);
            ty += f.lineHeight;
        }
        if (!p.subLines().isEmpty()) {
            ty += 2;
            for (String line : p.subLines()) {
                g.text(f, line, tx, ty, p.subColor(), false);
                ty += f.lineHeight;
            }
        }
        if (focused) g.outline(x, y, w, h, FOCUS);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, MenuLayout.Rect vp) {
        if (layout.maxScroll() <= 0 || vp.h() <= 0) return;
        int x = vp.x() + layout.contentWidth() + 2;
        g.fill(x, vp.y(), x + 3, vp.y() + vp.h(), TRACK);
        int thumb = Math.max(12, vp.h() * vp.h() / layout.contentHeight());
        int ty = vp.y() + (int) Math.round((vp.h() - thumb) * (scroll / layout.maxScroll()));
        g.fill(x, ty, x + 3, ty + thumb, THUMB);
    }

    private void drawFooter(GuiGraphicsExtractor g, Font f, int mouseX, int mouseY) {
        MenuLayout.Rect foot = layout.footer();
        g.fill(foot.x() + 1, foot.y(), foot.x() + foot.w() - 1, foot.y() + 1, BORDER);
        MenuLayout.Rect close = layout.close();
        boolean closeHover = close.contains(mouseX, mouseY);
        box(g, close, closeHover || focus == FOCUS_CLOSE ? BUTTON_HOVER : BUTTON, focus == FOCUS_CLOSE ? FOCUS : BORDER);
        String closeText = TextFit.ellipsize(closeLabel, close.w() - 8, this::width);
        g.text(f, closeText, close.x() + (close.w() - width(closeText)) / 2, close.y() + (close.h() - f.lineHeight) / 2, TEXT, true);
        MenuLayout.Rect back = layout.back();
        if (back != null && backTile != null) {
            boolean hover = back.contains(mouseX, mouseY);
            box(g, back, hover || focus == FOCUS_BACK ? BUTTON_HOVER : BUTTON, focus == FOCUS_BACK ? FOCUS : BORDER);
            g.item(backTile.stack(), back.x() + 5, back.y() + (back.h() - 16) / 2);
            int tw = back.w() - 5 - 16 - 5 - 6;
            int ty = back.y() + 4;
            g.text(f, TextFit.ellipsize(backName, tw, this::width), back.x() + 26, ty, TEXT, true);
            if (!backSub.isEmpty()) g.text(f, TextFit.ellipsize(backSub, tw, this::width), back.x() + 26, ty + f.lineHeight + 1, MUTED, false);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, int hover) {
        MenuEntry e = null;
        boolean cut = false;
        if (hover >= 0) {
            e = state.tile(hover);
            Prepared p = prepared.get(hover);
            cut = p != null && p.cut();
        } else if (layout.back() != null && layout.back().contains(mouseX, mouseY) && state.hasBack()) {
            e = state.tile(state.backSlot);
        }
        if (e == null) return;
        boolean hasLore = false;
        for (Component c : e.lore()) if (!c.getString().isBlank()) hasLore = true;
        if (!hasLore && !cut) return;
        int maxW = Math.max(80, Math.min(260, width - 24));
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(e.name(), maxW));
        for (Component c : e.lore()) {
            if (c.getString().isEmpty()) lines.add(FormattedCharSequence.EMPTY);
            else lines.addAll(font.split(c, maxW));
        }
        g.setTooltipForNextFrame(lines, mouseX, mouseY);
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    /** The slot of the card under the pointer, or -1. */
    private int cardAt(double mx, double my) {
        if (layout == null || !layout.viewport().contains(mx, my)) return -1;
        int ox = layout.viewport().x(), oy = layout.viewport().y() - (int) Math.round(scroll);
        for (MenuLayout.Card c : layout.cards()) {
            if (new MenuLayout.Rect(ox + c.box().x(), oy + c.box().y(), c.box().w(), c.box().h()).contains(mx, my)) return c.slot();
        }
        return -1;
    }

    private void press(Runnable action) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        action.run();
    }

    private void clickSlot(int slot, int kind) {
        press(() -> MenuClient.click(state.id, slot, kind));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (layout == null) return false;
        int button = e.button();
        if (button == 0 || button == 1) {
            if (button == 0 && layout.close().contains(e.x(), e.y())) {
                press(this::onClose);
                return true;
            }
            if (layout.back() != null && layout.back().contains(e.x(), e.y())) {
                clickSlot(state.backSlot, button == 1 ? 1 : 0);
                return true;
            }
            int slot = cardAt(e.x(), e.y());
            if (slot >= 0) {
                focus = slot;
                clickSlot(slot, button == 1 ? 1 : minecraft.hasShiftDown() ? 2 : 0);
                return true;
            }
            if (button == 0 && layout.maxScroll() > 0) {
                MenuLayout.Rect vp = layout.viewport();
                int barX = vp.x() + layout.contentWidth();
                if (e.x() >= barX && e.x() < barX + MenuLayout.SCROLLBAR && e.y() >= vp.y() && e.y() < vp.y() + vp.h()) {
                    draggingBar = true;
                    dragTo(e.y());
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    private void dragTo(double my) {
        MenuLayout.Rect vp = layout.viewport();
        int thumb = Math.max(12, vp.h() * vp.h() / layout.contentHeight());
        double k = (my - vp.y() - thumb / 2.0) / Math.max(1, vp.h() - thumb);
        scroll = Math.max(0, Math.min(layout.maxScroll(), k * layout.maxScroll()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) {
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
        if (layout == null || layout.maxScroll() <= 0) return false;
        scroll = Math.max(0, Math.min(layout.maxScroll(), scroll - v * 18));
        return true;
    }

    /** Focus order: the cards as laid out, then Back, then Close. */
    private List<Integer> focusOrder() {
        List<Integer> order = new ArrayList<>();
        for (MenuLayout.Card c : layout.cards()) order.add(c.slot());
        if (layout.back() != null) order.add(FOCUS_BACK);
        order.add(FOCUS_CLOSE);
        return order;
    }

    private void moveFocus(int step) {
        List<Integer> order = focusOrder();
        int at = order.indexOf(focus);
        int next = at < 0 ? (step > 0 ? 0 : order.size() - 1) : Math.floorMod(at + step, order.size());
        focus = order.get(next);
        for (MenuLayout.Card c : layout.cards()) {
            if (c.slot() != focus) continue;
            int top = c.box().y(), bottom = top + c.box().h(), view = layout.viewport().h();
            if (top < scroll) scroll = top;
            else if (bottom > scroll + view) scroll = bottom - view;
            scroll = Math.max(0, Math.min(layout.maxScroll(), scroll));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (layout == null) return super.keyPressed(e);
        if (minecraft.options.keyInventory.matches(e) && !e.isEscape()) {
            onClose();
            return true;
        }
        if (e.key() == InputConstants.KEY_TAB) {
            moveFocus(e.hasShiftDown() ? -1 : 1);
            return true;
        }
        if (e.isDown() || e.isRight()) {
            moveFocus(1);
            return true;
        }
        if (e.isUp() || e.isLeft()) {
            moveFocus(-1);
            return true;
        }
        if (e.key() == InputConstants.KEY_PAGEDOWN || e.key() == InputConstants.KEY_PAGEUP) {
            scroll = Math.max(0, Math.min(layout.maxScroll(), scroll + (e.key() == InputConstants.KEY_PAGEDOWN ? 1 : -1) * layout.viewport().h() * 0.8));
            return true;
        }
        if (e.isConfirmation() || e.key() == InputConstants.KEY_SPACE) {
            if (focus == FOCUS_CLOSE) press(this::onClose);
            else if (focus == FOCUS_BACK && layout.back() != null) clickSlot(state.backSlot, 0);
            else if (focus >= 0 && state.tile(focus) != null) clickSlot(focus, 0);
            return true;
        }
        return super.keyPressed(e);
    }
}
