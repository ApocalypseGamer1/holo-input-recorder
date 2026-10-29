package holorecorder.improve;

import holorecorder.menu.MenuLayout.Rect;
import holorecorder.menu.TextFit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything of the Improve screen goes, from the screen size and a text meter alone (no game classes, so it is
 * unit-tested at every GUI scale). The result is a flat list of things to draw ({@link Prim}): filled rectangles, text
 * lines that are already wrapped to their box, and item icons. The screen only draws them, so nothing here depends on the
 * game, and a test can check that every one of them stays inside the panel.
 *
 * <p>Every tab is laid out at once and the panel is as tall as the tallest tab, so the panel does not jump when the player
 * switches tabs. Content coordinates: x from the viewport's left edge, y from the top of the scrolled content.
 */
public final class ImproveLayout {

    /** What the layout needs from the font. */
    public interface Meter {
        int lineHeight();

        int width(String text);

        /** True when {@code itemId} is a known item (an unknown id gets no icon and no space). */
        boolean iconKnown(String itemId);
    }

    public sealed interface Prim permits Fill, Text, Icon {
    }

    public record Fill(int x, int y, int w, int h, int argb) implements Prim {
    }

    /** One line of text; {@code x}, {@code y} is its top-left corner. */
    public record Text(int x, int y, String text, int argb, boolean shadow) implements Prim {
    }

    public record Icon(int x, int y, String itemId) implements Prim {
    }

    /** Where one block sits in the content. */
    public record Span(int y, int h) {
    }

    /** One tab's scrolled content plus its footer text (panel-absolute, drawn below the content). */
    public record Content(List<Prim> prims, List<Span> spans, int height, List<Text> footer) {
    }

    public record TabBox(Rect box, String label, int index) {
    }

    /** A time-range toggle button, placed. {@code on}: the range that is shown. */
    public record RangeBox(Rect box, String label, String action, boolean on) {
    }

    public record Result(Rect panel, Rect titleBar, List<Text> header, List<TabBox> tabs, List<RangeBox> ranges, Rect viewport,
                         int contentWidth, Rect footer, Rect back, Rect close, String backLabel, String closeLabel,
                         List<Content> contents) {
        public int maxScroll(int tab) {
            return Math.max(0, contents.get(tab).height() - viewport.h());
        }
    }

    public static final int PAD = 8, SCROLLBAR = 6, GAP = 4, MAX_PANEL = 560, ICON = 16, DOT = 5, DOT_GAP = 2;
    static final int BG = 0xFF14171C, BORDER = 0xFF2A2F38, CARD = 0xFF1D222A, ROW = 0xFF1A1E25, TEXT = 0xFFE8E8E8, WHITE = 0xFFFFFFFF,
            GREY = 0xFF9AA3AD, SUB = 0xFFAEB6BF, TIP = 0xFF7EE0A8, TRACK = 0xFF2A2F38, NOTE = 0xFFCFD6DD, ACCENT = 0xFF4FD8FF;
    private static final int MAX_LINES = 12;

    private ImproveLayout() {
    }

    public static Result compute(int screenW, int screenH, Meter m, ImproveModel.Screen s) {
        int lh = m.lineHeight();
        boolean compact = screenH < 200 || screenW < 300;
        int margin = compact ? 4 : 12;
        int vpad = compact ? 3 : 6;
        int panelW = Math.max(100, Math.min(MAX_PANEL, screenW - 2 * margin));
        int viewW = panelW - 2 * PAD;
        int innerW = viewW - SCROLLBAR;

        // header: title (and subtitle on the same line when both fit, else below it)
        int headW = panelW - 2 * PAD - 4;
        List<String> titleLines = TextFit.wrap(s.title(), headW, 2, m::width);
        List<String> subLines = new ArrayList<>();
        boolean sameLine = false;
        if (!s.subtitle().isBlank()) {
            int tw = titleLines.isEmpty() ? 0 : m.width(titleLines.get(0));
            int sw = m.width(s.subtitle().strip());
            if (titleLines.size() <= 1 && tw + 16 + sw <= headW) sameLine = true;
            else subLines = TextFit.wrap(s.subtitle(), headW, 2, m::width);
        }
        int titleRows = Math.max(1, titleLines.size());
        int headerH = 2 * vpad + titleRows * lh + (subLines.isEmpty() ? 0 : 2 + subLines.size() * lh);

        // tabs, then the time-range buttons: right-aligned on the last tab line when they fit there, else on a line of their own
        List<TabBox> tabs = new ArrayList<>();
        List<RangeBox> ranges = new ArrayList<>();
        int tabH = lh + (compact ? 4 : 8), tabsH = 0, tabRows = 0, tabsEnd = 0;
        if (s.tabs().size() > 1) {
            int x = 0, row = 0;
            for (int i = 0; i < s.tabs().size(); i++) {
                String label = TextFit.ellipsize(s.tabs().get(i).label(), viewW - 14, m::width);
                int w = m.width(label) + 14;
                if (x > 0 && x + w > viewW) {
                    x = 0;
                    row++;
                }
                tabs.add(new TabBox(new Rect(x, row * (tabH + GAP), w, tabH), label, i));
                x += w + GAP;
            }
            tabRows = row + 1;
            tabsEnd = x - GAP;
        }
        if (!s.ranges().isEmpty()) {
            List<String> rangeLabels = new ArrayList<>();
            int total = 0;
            for (ImproveModel.Range r : s.ranges()) {
                String label = TextFit.ellipsize(r.label(), viewW - 14, m::width);
                rangeLabels.add(label);
                total += m.width(label) + 14 + (total > 0 ? GAP : 0);
            }
            int row = tabRows;
            int x = viewW - total;
            if (tabRows > 0 && tabsEnd + 2 * GAP <= x) row = tabRows - 1;   // room on the last tab line
            else if (total > viewW) x = 0;                                   // not even alone: wrap like tabs
            for (int i = 0; i < s.ranges().size(); i++) {
                int w = m.width(rangeLabels.get(i)) + 14;
                if (total > viewW && x > 0 && x + w > viewW) {
                    x = 0;
                    row++;
                }
                ImproveModel.Range r = s.ranges().get(i);
                ranges.add(new RangeBox(new Rect(x, row * (tabH + GAP), w, tabH), rangeLabels.get(i), r.action(), r.on()));
                x += w + GAP;
            }
            tabRows = Math.max(tabRows, row + 1);
        }
        if (tabRows > 0) tabsH = tabRows * tabH + (tabRows - 1) * GAP;

        // footer: text left and buttons right when the text gets enough room, else the text above the buttons
        String backLabel = TextFit.ellipsize(s.back(), viewW / 2 - 16, m::width);
        String closeLabel = TextFit.ellipsize(s.close(), viewW / 2 - 16, m::width);
        int buttonH = lh + (compact ? 4 : 8);
        int backW = Math.max(48, m.width(backLabel) + 20), closeW = Math.max(48, m.width(closeLabel) + 20);
        if (backW + GAP + closeW > viewW) {   // a very narrow panel: two equal buttons
            closeW = Math.max(1, (viewW - GAP) / 2);
            backW = Math.max(1, viewW - GAP - closeW);
            backLabel = TextFit.ellipsize(s.back(), backW - 8, m::width);
            closeLabel = TextFit.ellipsize(s.close(), closeW - 8, m::width);
        }
        int buttonsW = backW + GAP + closeW;
        int textRoom = viewW - buttonsW - 10;
        boolean side = textRoom >= 90;
        int footerMaxLines = compact ? 1 : 3;
        int textW = side ? textRoom : viewW;
        List<List<String>> footerLines = new ArrayList<>();
        int maxFooterLines = 0;
        for (int i = 0; i < s.tabs().size(); i++) {
            List<String> lines = !side && compact ? List.of() : TextFit.wrap(s.footerOf(i), textW, footerMaxLines, m::width);
            footerLines.add(lines);
            maxFooterLines = Math.max(maxFooterLines, lines.size());
        }
        int fpad = compact ? 3 : 5;
        int textBlock = maxFooterLines * lh;
        int rowH = side ? Math.max(buttonH, textBlock) : textBlock + (textBlock > 0 ? 3 : 0) + buttonH;
        int footerH = rowH + 2 * fpad;

        // content of every tab
        List<Content> contents = new ArrayList<>();
        int maxContent = 0;
        for (var tab : s.tabs()) {
            Content c = content(innerW, m, tab.blocks());
            contents.add(c);
            maxContent = Math.max(maxContent, c.height());
        }

        int gap = compact ? 3 : 6;
        int viewTop = headerH + (tabsH > 0 ? gap + tabsH : 0) + gap;
        int bottomGap = compact ? 3 : PAD;
        int wanted = viewTop + maxContent + bottomGap + footerH;
        int minPanel = viewTop + 20 + bottomGap + footerH;
        int panelH = Math.min(wanted, Math.max(minPanel, screenH - 2 * margin));
        int panelX = (screenW - panelW) / 2, panelY = Math.max(0, (screenH - panelH) / 2);
        Rect panel = new Rect(panelX, panelY, panelW, panelH);
        Rect titleBar = new Rect(panelX, panelY, panelW, headerH);
        int viewH = Math.max(0, panelH - viewTop - bottomGap - footerH);
        Rect viewport = new Rect(panelX + PAD, panelY + viewTop, viewW, viewH);
        Rect footer = new Rect(panelX, panelY + panelH - footerH, panelW, footerH);

        List<Text> header = new ArrayList<>();
        int ty = panelY + vpad;
        for (String line : titleLines) {
            header.add(new Text(panelX + PAD + 4, ty, line, WHITE, true));
            ty += lh;
        }
        if (sameLine) {
            String sub = s.subtitle().strip();
            header.add(new Text(panelX + panelW - PAD - m.width(sub), panelY + vpad, sub, GREY, false));
        } else if (!subLines.isEmpty()) {
            ty += 2;
            for (String line : subLines) {
                header.add(new Text(panelX + PAD + 4, ty, line, GREY, false));
                ty += lh;
            }
        }

        List<TabBox> placedTabs = new ArrayList<>();
        for (TabBox t : tabs) {
            Rect b = t.box();
            placedTabs.add(new TabBox(new Rect(viewport.x() + b.x(), panelY + headerH + gap + b.y(), b.w(), b.h()), t.label(), t.index()));
        }

        List<RangeBox> placedRanges = new ArrayList<>();
        for (RangeBox r : ranges) {
            Rect b = r.box();
            placedRanges.add(new RangeBox(new Rect(viewport.x() + b.x(), panelY + headerH + gap + b.y(), b.w(), b.h()), r.label(), r.action(), r.on()));
        }

        int btnY, textY0;
        int rowTop = footer.y() + fpad;
        if (side) {
            btnY = rowTop + (rowH - buttonH) / 2;
            textY0 = rowTop + (rowH - textBlock) / 2;
        } else {
            textY0 = rowTop;
            btnY = rowTop + rowH - buttonH;
        }
        Rect close = new Rect(viewport.x() + viewW - closeW, btnY, closeW, buttonH);
        Rect back = new Rect(close.x() - GAP - backW, btnY, backW, buttonH);

        List<Content> withFooter = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            List<Text> ft = new ArrayList<>();
            int fy = textY0;
            if (side) fy = rowTop + (rowH - footerLines.get(i).size() * lh) / 2;
            for (String line : footerLines.get(i)) {
                ft.add(new Text(viewport.x(), fy, line, GREY, false));
                fy += lh;
            }
            Content c = contents.get(i);
            withFooter.add(new Content(c.prims(), c.spans(), c.height(), List.copyOf(ft)));
        }
        return new Result(panel, titleBar, List.copyOf(header), List.copyOf(placedTabs), List.copyOf(placedRanges), viewport, innerW,
                footer, back, close, backLabel, closeLabel, List.copyOf(withFooter));
    }

    // ---- blocks -----------------------------------------------------------------------------------------------------

    private static Content content(int w, Meter m, List<ImproveModel.Block> blocks) {
        List<Prim> out = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        int y = 0;
        boolean first = true;
        for (ImproveModel.Block b : blocks) {
            int top = y + (first ? 0 : GAP + (b instanceof ImproveModel.Section ? GAP : 0));
            int h;
            if (b instanceof ImproveModel.Section s) h = section(out, top, w, m, s);
            else if (b instanceof ImproveModel.Card c) h = card(out, top, w, m, c);
            else if (b instanceof ImproveModel.Row r) h = row(out, top, w, m, r);
            else if (b instanceof ImproveModel.Note n) h = note(out, top, w, m, n);
            else h = grid(out, top, w, m, (ImproveModel.Grid) b);
            if (h <= 0) continue;
            spans.add(new Span(top, h));
            y = top + h;
            first = false;
        }
        return new Content(List.copyOf(out), List.copyOf(spans), y, List.of());
    }

    private static List<String> wrap(Meter m, String text, int width, int max) {
        return TextFit.wrap(text, Math.max(8, width), max, m::width);
    }

    private static int lines(List<Prim> out, Meter m, List<String> lines, int x, int y, int argb, boolean shadow) {
        for (String line : lines) {
            out.add(new Text(x, y, line, argb, shadow));
            y += m.lineHeight();
        }
        return y;
    }

    private static int section(List<Prim> out, int y, int w, Meter m, ImproveModel.Section s) {
        if (s.text().isBlank()) return 0;
        int lh = m.lineHeight();
        String text = TextFit.ellipsize(s.text(), w - 4, m::width);
        out.add(new Text(2, y + 1, text, GREY, false));
        int lineX = 2 + m.width(text) + 5;
        if (lineX < w) out.add(new Fill(lineX, y + 1 + lh / 2, w - lineX, 1, TRACK));
        return lh + 3;
    }

    private static void bar(List<Prim> out, int x, int y, int w, int fillColor, double bar, double marker) {
        out.add(new Fill(x, y, w, 6, TRACK));
        int fw = (int) Math.round(bar * w);
        if (fw > 0) out.add(new Fill(x, y, Math.min(w, fw), 6, fillColor));
        if (marker >= 0 && w >= 4) {
            int mx = Math.max(0, Math.min(w - 2, (int) Math.round(marker * w) - 1));
            out.add(new Fill(x + mx, y - 2, 2, 10, WHITE));
        }
    }

    private static int card(List<Prim> out, int y, int w, Meter m, ImproveModel.Card c) {
        int lh = m.lineHeight();
        int edge = 3, pad = 6;
        boolean icon = !c.icon().isBlank() && m.iconKnown(c.icon());
        int textX = edge + pad + (icon ? ICON + 6 : 0);
        int perRow = 10, dots = c.dots().size();
        int dotsRowW = Math.min(dots, perRow) * (DOT + DOT_GAP) - DOT_GAP;
        int dotsW = Math.max(0, dotsRowW);
        int countW = c.count().isBlank() ? 0 : m.width(c.count().strip());
        boolean hasRight = countW > 0 || dots > 0;
        int rightW = Math.max(dotsW, Math.min(countW, Math.max(dotsW, w / 3)));
        boolean side = hasRight && w - textX - pad - rightW - 10 >= 100;
        int mainW = Math.max(8, side ? w - textX - pad - rightW - 10 : w - textX - pad);
        if (!side) perRow = Math.max(1, Math.min(10, (mainW + DOT_GAP) / (DOT + DOT_GAP)));   // under the text, the dots wrap to its width

        List<String> title = wrap(m, c.title(), mainW, MAX_LINES);
        List<String> sub = wrap(m, c.sub(), mainW, MAX_LINES);
        List<String> tip = wrap(m, c.tip(), mainW, MAX_LINES);
        List<Prim> body = new ArrayList<>();
        int ry = pad;
        ry = lines(body, m, title, textX, ry, WHITE, true);
        if (!sub.isEmpty()) ry = lines(body, m, sub, textX, ry + 2, SUB, false);
        if (c.bar() >= 0) {
            bar(body, textX, ry + 4 + 2, mainW, c.color(), c.bar(), c.marker());
            ry += 4 + 10;
        }
        if (!tip.isEmpty()) ry = lines(body, m, tip, textX, ry + 4, TIP, false);
        int bodyBottom = ry + pad;

        int rightBottom = 0;
        if (hasRight) {
            int rx = w - pad - rightW;
            int ryy = pad;
            if (side) {
                for (String line : wrap(m, c.count(), rightW, 3)) {
                    body.add(new Text(w - pad - m.width(line), ryy, line, SUB, false));
                    ryy += lh;
                }
                if (dots > 0 && countW > 0) ryy += 4;
                dotsAt(body, c, rx, ryy, perRow);
                if (dots > 0) ryy += dotRows(dots, perRow) * (DOT + DOT_GAP) - DOT_GAP;
                rightBottom = ryy + pad;
            } else {
                int sy = bodyBottom - pad + 4;
                for (String line : wrap(m, c.count(), mainW, 3)) {
                    body.add(new Text(textX, sy, line, SUB, false));
                    sy += lh;
                }
                if (dots > 0) {
                    dotsAt(body, c, textX, sy + (countW > 0 ? 2 : 0), perRow);
                    sy += (countW > 0 ? 2 : 0) + dotRows(dots, perRow) * (DOT + DOT_GAP) - DOT_GAP;
                }
                bodyBottom = sy + pad;
            }
        }
        int h = Math.max(Math.max(bodyBottom, rightBottom), ICON + 2 * pad);
        out.add(new Fill(0, y, w, h, CARD));
        out.add(new Fill(0, y, edge, h, c.color()));
        if (icon) out.add(new Icon(edge + pad, y + (h - ICON) / 2, c.icon()));
        for (Prim p : body) out.add(shift(p, y));
        return h;
    }

    private static int dotRows(int dots, int perRow) {
        return (dots + perRow - 1) / perRow;
    }

    private static void dotsAt(List<Prim> out, ImproveModel.Card c, int x, int y, int perRow) {
        for (int i = 0; i < c.dots().size(); i++) {
            int dx = x + (i % perRow) * (DOT + DOT_GAP), dy = y + (i / perRow) * (DOT + DOT_GAP);
            out.add(new Fill(dx, dy, DOT, DOT, c.dots().get(i) ? c.color() : TRACK));
        }
    }

    private static Prim shift(Prim p, int dy) {
        if (p instanceof Fill f) return new Fill(f.x(), f.y() + dy, f.w(), f.h(), f.argb());
        if (p instanceof Text t) return new Text(t.x(), t.y() + dy, t.text(), t.argb(), t.shadow());
        Icon i = (Icon) p;
        return new Icon(i.x(), i.y() + dy, i.itemId());
    }

    private static int row(List<Prim> out, int y, int w, Meter m, ImproveModel.Row r) {
        if (r.label().isBlank() && r.sub().isBlank() && r.value().isBlank() && r.bar() < 0) return 0;
        int lh = m.lineHeight();
        int pad = 6, vpad = 5;
        String value = r.value().isBlank() ? "" : TextFit.ellipsize(r.value(), Math.max(20, w / 3), m::width);
        int valueW = value.isEmpty() ? 0 : m.width(value);
        int avail = Math.max(16, w - 2 * pad - (valueW > 0 ? valueW + 8 : 0));
        boolean hasBar = r.bar() >= 0;
        boolean wide = hasBar && w >= 300;
        int leftW = wide ? avail * 45 / 100 : avail;
        int barW = wide ? avail - leftW - 10 : avail;
        List<String> label = wrap(m, r.label(), leftW, 3);
        List<String> sub = wrap(m, r.sub(), leftW, 3);
        int textH = (label.size() + sub.size()) * lh + (sub.isEmpty() || label.isEmpty() ? 0 : 1);
        int h = wide ? Math.max(textH, 10) + 2 * vpad : textH + 2 * vpad + (hasBar ? (textH > 0 ? 4 : 0) + 10 : 0);
        List<Prim> body = new ArrayList<>();
        body.add(new Fill(0, 0, w, h, ROW));
        int ty = wide ? (h - textH) / 2 : vpad;
        ty = lines(body, m, label, pad, ty, TEXT, false);
        lines(body, m, sub, pad, ty + (label.isEmpty() ? 0 : 1), GREY, false);
        if (hasBar) {
            if (wide) bar(body, pad + leftW + 10, h / 2 - 3, barW, r.color(), r.bar(), r.marker());
            else bar(body, pad, vpad + textH + (textH > 0 ? 4 : 0) + 2, barW, r.color(), r.bar(), r.marker());
        }
        if (valueW > 0) body.add(new Text(w - pad - valueW, wide || !hasBar ? (h - lh) / 2 : vpad, value, NOTE, false));
        for (Prim p : body) out.add(shift(p, y));
        return h;
    }

    private static int note(List<Prim> out, int y, int w, Meter m, ImproveModel.Note n) {
        if (n.text().isBlank()) return 0;
        int edge = 3, pad = 8, vpad = 7;
        List<String> lines = wrap(m, n.text(), w - edge - 2 * pad, 30);
        int h = lines.size() * m.lineHeight() + 2 * vpad;
        out.add(new Fill(0, y, w, h, CARD));
        if (n.color() != ImproveModel.NO_EDGE) out.add(new Fill(0, y, edge, h, n.color()));
        lines(out, m, lines, edge + pad, y + vpad, NOTE, false);
        return h;
    }

    private static int grid(List<Prim> out, int y, int w, Meter m, ImproveModel.Grid g) {
        if (g.rows().isEmpty()) return 0;
        int lh = m.lineHeight();
        int edge = 3, pad = 4, vpad = 5;
        boolean wide = w >= 240;
        int top = y;
        if (wide) {
            int labelW = Math.max(40, Math.min(120, w / 5));
            int cellW = (w - labelW - 4 * GAP) / 4;
            int textW = Math.max(8, cellW - edge - 2 * pad);
            int headH = 0;
            List<List<String>> heads = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                List<String> hl = wrap(m, g.head().get(i), cellW - 4, 2);
                heads.add(hl);
                headH = Math.max(headH, hl.size() * lh);
            }
            if (headH > 0) {
                for (int i = 0; i < 4; i++) lines(out, m, heads.get(i), labelW + GAP + i * (cellW + GAP) + 2, top, GREY, false);
                top += headH + GAP;
            }
            boolean firstRow = true;
            for (ImproveModel.GridRow r : g.rows()) {
                if (!firstRow) top += GAP;
                firstRow = false;
                List<String> label = wrap(m, r.label(), labelW - 2 * pad, 4);
                int rowH = label.size() * lh;
                List<List<String>> texts = new ArrayList<>(), subs = new ArrayList<>();
                for (ImproveModel.Cell c : r.cells()) {
                    List<String> t = wrap(m, c.text(), textW, 4), sb = wrap(m, c.sub(), textW, 3);
                    texts.add(t);
                    subs.add(sb);
                    rowH = Math.max(rowH, (t.size() + sb.size()) * lh);
                }
                rowH = Math.max(lh, rowH) + 2 * vpad;
                out.add(new Fill(0, top, labelW, rowH, ROW));
                lines(out, m, label, pad, top + vpad, GREY, false);
                for (int i = 0; i < 4; i++) {
                    int cx = labelW + GAP + i * (cellW + GAP);
                    ImproveModel.Cell c = r.cells().get(i);
                    out.add(new Fill(cx, top, cellW, rowH, ROW));
                    if (c.edge() != ImproveModel.NO_EDGE) out.add(new Fill(cx, top, edge, rowH, c.edge()));
                    int ty = lines(out, m, texts.get(i), cx + edge + pad, top + vpad, TEXT, false);
                    lines(out, m, subs.get(i), cx + edge + pad, ty, GREY, false);
                }
                top += rowH;
            }
            return top - y;
        }
        // narrow: the row label on its own line, then the four cells two by two, each with its column head on top
        int cellW = (w - GAP) / 2;
        int textW = Math.max(8, cellW - edge - 2 * pad);
        boolean firstRow = true;
        for (ImproveModel.GridRow r : g.rows()) {
            if (!firstRow) top += GAP + 2;
            firstRow = false;
            List<String> label = wrap(m, r.label(), w - 4, 3);
            top = lines(out, m, label, 2, top, GREY, false) + (label.isEmpty() ? 0 : 2);
            for (int pair = 0; pair < 2; pair++) {
                int rowH = 0;
                List<List<String>> heads = new ArrayList<>(), texts = new ArrayList<>(), subs = new ArrayList<>();
                for (int k = 0; k < 2; k++) {
                    int i = pair * 2 + k;
                    ImproveModel.Cell c = r.cells().get(i);
                    List<String> hl = wrap(m, g.head().get(i + 1), textW, 2);
                    List<String> t = wrap(m, c.text(), textW, 4), sb = wrap(m, c.sub(), textW, 3);
                    heads.add(hl);
                    texts.add(t);
                    subs.add(sb);
                    rowH = Math.max(rowH, (hl.size() + t.size() + sb.size()) * lh);
                }
                rowH = Math.max(lh, rowH) + 2 * vpad;
                for (int k = 0; k < 2; k++) {
                    int cx = k * (cellW + GAP);
                    ImproveModel.Cell c = r.cells().get(pair * 2 + k);
                    out.add(new Fill(cx, top, cellW, rowH, ROW));
                    if (c.edge() != ImproveModel.NO_EDGE) out.add(new Fill(cx, top, edge, rowH, c.edge()));
                    int ty = lines(out, m, heads.get(k), cx + edge + pad, top + vpad, GREY, false);
                    ty = lines(out, m, texts.get(k), cx + edge + pad, ty, TEXT, false);
                    lines(out, m, subs.get(k), cx + edge + pad, ty, GREY, false);
                }
                top += rowH + (pair == 0 ? GAP : 0);
            }
        }
        return top - y;
    }
}
