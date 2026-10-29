package holorecorder.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where everything of a custom menu goes, from the screen size alone (no game classes, so it is unit-tested at every GUI
 * scale). The panel is centred. Tiles keep the chest's rows as groups; a row that has more tiles than fit side by side
 * wraps onto further lines, so a narrow screen or a long language shows fewer, taller cards instead of overflowing. When
 * the groups are taller than the panel the middle part scrolls; the title bar and the Back/Close footer stay put.
 *
 * <p>Card boxes are in content coordinates: x from the viewport's left edge, y from the top of the scrolled content.
 */
public final class MenuLayout {

    /** What the layout needs to know about the text: how many lines a tile's name and subtitle take at a width. */
    public interface Meter {
        int lineHeight();

        /** Lines (1 or 2) the tile's name takes at {@code textWidth} pixels. */
        int nameLines(int slot, int textWidth);

        /** Lines (0, 1 or 2) the tile's subtitle takes at {@code textWidth} pixels. */
        int subLines(int slot, int textWidth);
    }

    /** A tile to place: its slot and the chest row it sits in. */
    public record Item(int slot, int row) {
    }

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    /** A placed tile. {@code box} is in content coordinates; the text starts at {@code textX} (box-relative) and is {@code textW} wide. */
    public record Card(int slot, Rect box, int textX, int textW, int nameLines, int subLines) {
    }

    public record Heading(int row, Rect box) {
    }

    /** {@code back} is null for a root menu. */
    public record Result(Rect panel, Rect titleBar, Rect viewport, int contentWidth, Rect footer, List<Card> cards,
                         List<Heading> headings, int contentHeight, int maxScroll, Rect back, Rect close) {
    }

    public static final int GAP = 4, PAD = 8, ICON = 20, CARD_PAD = 5, ICON_GAP = 6, MIN_CARD = 128, MAX_CARD = 210,
            SCROLLBAR = 6, MAX_PANEL = 560;

    private MenuLayout() {
    }

    /**
     * @param items      the tiles to place (not the Back tile), any order
     * @param headingRows chest rows that carry a section heading (a heading is drawn only above a row that has tiles)
     * @param backLabelW the widest of the Back button's two lines, or -1 when the menu has no Back
     * @param closeLabelW the width of the Close label
     */
    public static Result compute(int screenW, int screenH, Meter m, List<Item> items, Set<Integer> headingRows, int backLabelW,
                                 int closeLabelW) {
        int lh = m.lineHeight();
        int margin = screenW < 300 || screenH < 200 ? 4 : 12;
        int panelW = Math.max(100, Math.min(MAX_PANEL, screenW - 2 * margin));
        int viewW = panelW - 2 * PAD;
        int innerW = viewW - SCROLLBAR;
        int cols = Math.max(1, Math.min(9, (innerW + GAP) / (MIN_CARD + GAP)));

        TreeMap<Integer, List<Item>> rows = new TreeMap<>();
        for (Item it : items) rows.computeIfAbsent(it.row(), r -> new ArrayList<>()).add(it);
        for (List<Item> r : rows.values()) r.sort((a, b) -> Integer.compare(a.slot(), b.slot()));

        List<Card> cards = new ArrayList<>();
        List<Heading> headings = new ArrayList<>();
        int y = 0;
        boolean first = true;
        for (var e : rows.entrySet()) {
            List<Item> row = e.getValue();
            if (!first) y += GAP + 2;
            first = false;
            if (headingRows.contains(e.getKey())) {
                headings.add(new Heading(e.getKey(), new Rect(0, y, innerW, lh + 4)));
                y += lh + 4 + 2;
            }
            int n = row.size();
            int lines = (n + cols - 1) / cols;
            int per = (n + lines - 1) / lines;
            for (int l = 0; l < lines; l++) {
                int from = l * per, to = Math.min(n, from + per), cnt = to - from;
                if (cnt <= 0) break;
                int w = Math.min(MAX_CARD, (innerW - (cnt - 1) * GAP) / cnt);
                int total = cnt * w + (cnt - 1) * GAP;
                int x0 = (innerW - total) / 2;
                int textX = CARD_PAD + ICON + ICON_GAP;
                int textW = Math.max(8, w - textX - CARD_PAD);
                int h = 0;
                int[] nl = new int[cnt], sl = new int[cnt];
                for (int i = 0; i < cnt; i++) {
                    int slot = row.get(from + i).slot();
                    nl[i] = Math.max(1, Math.min(2, m.nameLines(slot, textW)));
                    sl[i] = Math.max(0, Math.min(2, m.subLines(slot, textW)));
                    int textH = nl[i] * lh + sl[i] * lh + (sl[i] > 0 ? 2 : 0);
                    h = Math.max(h, textH + 2 * CARD_PAD);
                }
                h = Math.max(h, ICON + 2 * CARD_PAD);
                for (int i = 0; i < cnt; i++) {
                    cards.add(new Card(row.get(from + i).slot(), new Rect(x0 + i * (w + GAP), y, w, h), textX, textW, nl[i], sl[i]));
                }
                y += h + (l < lines - 1 ? GAP : 0);
            }
        }
        int contentH = y;

        int buttonH = 2 * lh + 8;
        int footerH = buttonH + 10;
        int titleH = lh + 12;
        int wanted = titleH + PAD + contentH + PAD + footerH;
        int panelH = Math.min(wanted, Math.max(titleH + footerH + 2 * PAD + 20, screenH - 2 * margin));
        int panelX = (screenW - panelW) / 2, panelY = Math.max(0, (screenH - panelH) / 2);
        Rect panel = new Rect(panelX, panelY, panelW, panelH);
        Rect titleBar = new Rect(panelX, panelY, panelW, titleH);
        int viewH = Math.max(0, panelH - titleH - footerH - 2 * PAD);
        Rect viewport = new Rect(panelX + PAD, panelY + titleH + PAD, viewW, viewH);
        Rect footer = new Rect(panelX, panelY + panelH - footerH, panelW, footerH);

        int btnY = footer.y() + 5;
        int closeW = Math.min(closeLabelW + 24, backLabelW >= 0 ? Math.max(50, viewW / 3) : viewW);
        closeW = Math.max(closeW, Math.min(56, viewW));
        Rect close = new Rect(viewport.x() + viewW - closeW, btnY, closeW, buttonH);
        Rect back = null;
        if (backLabelW >= 0) {
            int backW = Math.min(viewW - closeW - GAP, Math.max(96, backLabelW + 26));
            back = new Rect(viewport.x(), btnY, Math.max(20, backW), buttonH);
        }
        return new Result(panel, titleBar, viewport, innerW, footer, List.copyOf(cards), List.copyOf(headings), contentH,
                Math.max(0, contentH - viewH), back, close);
    }
}
