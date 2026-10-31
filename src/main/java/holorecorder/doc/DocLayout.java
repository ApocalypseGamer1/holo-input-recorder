package holorecorder.doc;

import holorecorder.menu.MenuLayout.Rect;
import holorecorder.menu.TextFit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything of the document screen goes, from the screen size and a text meter alone (no game classes, so it is
 * unit-tested at every GUI scale and in long languages). The result is rectangles plus, for each page, a flat list of things
 * to draw ({@link Prim}) whose text is already wrapped to its box. Content coordinates: x from the viewport's left edge,
 * y from the top of the scrolled page. Everything else is in screen coordinates, except the chapter boxes (from the list's
 * top-left corner, before its scroll).
 */
public final class DocLayout {

    /** What the layout needs from the font. Widths must add up: width(a + b) == width(a) + width(b). */
    public interface Meter {
        int lineHeight();

        int width(String text);
    }

    public sealed interface Prim permits Fill, Text {
    }

    public record Fill(int x, int y, int w, int h, int argb) implements Prim {
    }

    /** One line (or piece of a line) of text; {@code x}, {@code y} is its top-left corner. */
    public record Text(int x, int y, String text, int argb, boolean shadow) implements Prim {
    }

    public record Content(List<Prim> prims, int height) {
    }

    public record ChapterBox(Rect box, String label, int index) {
    }

    public record Button(Rect box, String label) {
    }

    /** {@code list}, {@code prev}, {@code next}, {@code counter} and {@code back} are null when not shown. */
    public record Result(Rect panel, Rect header, List<Text> headerText, Rect progress, Rect list, List<ChapterBox> chapters,
                         int listHeight, Rect viewport, int contentWidth, Rect footer, Rect prev, Rect next, Rect counter,
                         Button back, Button close, int accent, List<Content> contents) {
        public int maxScroll(int page) {
            return Math.max(0, contents.get(page).height() - viewport.h());
        }

        public int maxListScroll() {
            return list == null ? 0 : Math.max(0, listHeight - list.h());
        }
    }

    /** A piece of a wrapped line: {@code x} from the line's left edge. */
    record Seg(int x, String text, boolean key) {
    }

    public static final int PAD = 8, SCROLLBAR = 6, MAX_PANEL_W = 520, MAX_PANEL_H = 330, GAP = 4;
    public static final int BG = 0xE614171C, BORDER = 0xFF2A2F38, LIST_BG = 0xCC0E1115, ROW = 0xFF1A1E25, TEXT = 0xFFE8E8E8,
            GREY = 0xFF9AA3AD, KEY = 0xFFFFD36B, TRACK = 0xFF2A2F38;
    private static final int BASE = 0xFF14171C, MAX_HEAD_LINES = 3;

    private DocLayout() {
    }

    public static int mix(int a, int b, double t) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
            out |= (int) Math.round(ca * t + cb * (1 - t)) << shift;
        }
        return out;
    }

    public static Result compute(int screenW, int screenH, Meter m, DocModel.Doc doc) {
        int lh = m.lineHeight();
        boolean compact = screenH < 200 || screenW < 300;
        int margin = compact ? 4 : 12;
        int vpad = compact ? 3 : 6;
        int panelW = Math.max(100, Math.min(MAX_PANEL_W, screenW - 2 * margin));
        int pages = doc.pages().size();

        // header: title, and under it the subtitle (not on a small screen); a 2 px progress line closes it
        int headW = panelW - 2 * PAD - 4;
        String title = TextFit.ellipsize(doc.title(), headW, m::width);
        String sub = compact || doc.subtitle().isBlank() ? "" : TextFit.ellipsize(doc.subtitle(), headW, m::width);
        int headerH = 2 * vpad + lh + (sub.isEmpty() ? 0 : lh + 1) + 2;

        int btnH = lh + (compact ? 4 : 8);
        int footerH = btnH + 2 * vpad;
        int minH = headerH + footerH + 3 * lh + 2 * PAD;
        int panelH = Math.max(minH, Math.min(MAX_PANEL_H, screenH - 2 * margin));
        int panelX = (screenW - panelW) / 2, panelY = (screenH - panelH) / 2;
        Rect panel = new Rect(panelX, panelY, panelW, panelH);
        Rect header = new Rect(panelX + 1, panelY + 1, panelW - 2, headerH - 1);
        Rect progress = new Rect(panelX + 1, panelY + headerH - 2, panelW - 2, 2);
        List<Text> headerText = new ArrayList<>();
        headerText.add(new Text(panelX + PAD + 4, panelY + vpad, title, 0xFFFFFFFF, true));
        if (!sub.isEmpty()) headerText.add(new Text(panelX + PAD + 4, panelY + vpad + lh + 1, sub, 0xFFC9D1D9, false));

        int bodyTop = panelY + headerH, bodyBottom = panelY + panelH - footerH;
        int bodyH = bodyBottom - bodyTop;

        // chapter list on the left (a document with several pages, on a window wide enough)
        Rect list = null;
        List<ChapterBox> chapters = new ArrayList<>();
        int listHeight = 0, listW = 0;
        if (pages > 1 && panelW >= 300) {
            listW = Math.max(90, Math.min(140, panelW * 30 / 100));
            list = new Rect(panelX + 1, bodyTop, listW, bodyH);
            int rowH = lh + (compact ? 3 : 5);
            for (int i = 0; i < pages; i++) {
                String label = TextFit.ellipsize(doc.pages().get(i).head(), listW - 6 - 10, m::width);
                if (label.isEmpty()) label = String.valueOf(i + 1);
                chapters.add(new ChapterBox(new Rect(3, 3 + i * rowH, listW - 6, rowH - 1), label, i));
            }
            listHeight = 6 + pages * rowH;
        }

        int vx = list == null ? panelX + PAD : list.x() + list.w() + PAD;
        int vy = bodyTop + PAD;
        int vw = panelX + panelW - PAD - vx;
        Rect viewport = new Rect(vx, vy, Math.max(20, vw), Math.max(lh, bodyBottom - vy - 4));
        int contentW = Math.max(12, viewport.w() - SCROLLBAR);

        List<Content> contents = new ArrayList<>();
        for (DocModel.Page p : doc.pages()) contents.add(page(p, contentW, m, doc.accent()));

        // footer: Back on the left, the pager in the middle, Close on the right
        Rect footer = new Rect(panelX + 1, panelY + panelH - footerH, panelW - 2, footerH);
        int by = footer.y() + vpad;
        int avail = panelW - 2 * PAD;
        int maxLabel = Math.max(20, avail * 2 / 5);
        String closeLabel = TextFit.ellipsize(doc.close(), maxLabel - 14, m::width);
        String backLabel = doc.back() == null ? null : TextFit.ellipsize(doc.back(), maxLabel - 14, m::width);
        int closeW = m.width(closeLabel) + 14;
        int backW = backLabel == null ? 0 : m.width(backLabel) + 14;
        int arrowW = Math.max(btnH, Math.max(m.width("<"), m.width(">")) + 10);
        String widest = pages + " / " + pages;
        int counterW = m.width(widest) + 8;
        int pagerW = pages > 1 ? 2 * arrowW + counterW + 2 * 2 : 0;
        if (backLabel != null && backW + closeW + pagerW + 2 * GAP > avail) {
            backLabel = null;
            backW = 0;
        }
        if (closeW + pagerW + GAP > avail) {
            closeW = Math.max(btnH, avail - pagerW - GAP);
            closeLabel = TextFit.ellipsize(closeLabel, closeW - 14, m::width);
        }
        int left = panelX + PAD, right = panelX + panelW - PAD;
        Button back = backLabel == null ? null : new Button(new Rect(left, by, backW, btnH), backLabel);
        Button close = new Button(new Rect(right - closeW, by, closeW, btnH), closeLabel);
        Rect prev = null, next = null, counter = null;
        if (pages > 1) {
            int lo = (back == null ? left : left + backW + GAP), hi = right - closeW - GAP - pagerW;
            int px = Math.max(lo, Math.min(hi, panelX + (panelW - pagerW) / 2));
            prev = new Rect(px, by, arrowW, btnH);
            counter = new Rect(px + arrowW + 2, by, counterW, btnH);
            next = new Rect(px + arrowW + 2 + counterW + 2, by, arrowW, btnH);
        }
        return new Result(panel, header, headerText, progress, list, chapters, listHeight, viewport, contentW, footer, prev, next,
                counter, back, close, doc.accent(), contents);
    }

    // ------------------------------------------------------------------------------------------ one page

    private static Content page(DocModel.Page p, int w, Meter m, int accent) {
        int lh = m.lineHeight(), step = lh + 1;
        List<Prim> out = new ArrayList<>();
        int y = 0;
        if (!p.head().isBlank()) {
            for (String line : TextFit.wrap(p.head(), w, MAX_HEAD_LINES, m::width)) {
                out.add(new Text(0, y, line, accent, true));
                y += step;
            }
            out.add(new Fill(0, y, w, 1, mix(accent, BASE, 0.5)));
            y += 6;
        }
        DocModel.Kind prev = null;
        for (DocModel.Block b : p.blocks()) {
            if (b.kind() == DocModel.Kind.GAP) {
                if (prev != null) y += lh / 2 + 2;
                prev = DocModel.Kind.GAP;
                continue;
            }
            boolean bullet = b.kind() == DocModel.Kind.BULLET;
            String mark = bullet ? (b.mark().isEmpty() ? "•" : b.mark()) : "";
            int indent = bullet ? Math.max(10, m.width(mark) + 8) : 0;
            List<List<Seg>> lines = wrapRuns(b.runs(), Math.max(8, w - indent - (bullet ? 3 : 0)), m);
            if (lines.isEmpty()) continue;
            if (prev == DocModel.Kind.BULLET || (bullet && prev != null && prev != DocModel.Kind.GAP)) y += 3;
            else if (prev == DocModel.Kind.PARAGRAPH) y += 2;
            int top = y + (bullet ? 3 : 0);
            if (bullet) {
                int h = lines.size() * step - 1 + 6;
                out.add(new Fill(0, y, w, h, ROW));
                out.add(new Fill(0, y, 2, h, accent));
                out.add(new Text(5, top, mark, accent, true));
            }
            int ty = top;
            for (List<Seg> line : lines) {
                for (Seg s : line) out.add(new Text(indent + s.x(), ty, s.text(), s.key() ? KEY : TEXT, true));
                ty += step;
            }
            y = bullet ? y + lines.size() * step - 1 + 6 : ty - 1;
            prev = b.kind();
        }
        return new Content(List.copyOf(out), y + 2);
    }

    // ------------------------------------------------------------------------------------------ wrapping runs

    private static boolean breaksAnywhere(int cp) {
        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
        return s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA
                || s == Character.UnicodeScript.HANGUL || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private record Piece(String text, boolean key) {
    }

    /**
     * Wraps styled runs to {@code width}: Latin words break at spaces, CJK text between any two characters, and a word wider
     * than the line inside itself. Neighbouring pieces of the same style join into one segment.
     */
    static List<List<Seg>> wrapRuns(List<DocModel.Run> runs, int width, Meter m) {
        width = Math.max(1, width);
        List<List<Piece>> lines = new ArrayList<>();
        List<Piece> cur = new ArrayList<>();
        int curW = 0;
        for (DocModel.Run run : runs) {
            String text = run.text();
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                int start = i;
                if (cp == ' ') {
                    i++;
                    if (curW > 0 && !endsWithSpace(cur)) {
                        int sw = m.width(" ");
                        if (curW + sw <= width) {
                            cur.add(new Piece(" ", run.key()));
                            curW += sw;
                        }
                    }
                    continue;
                }
                if (breaksAnywhere(cp)) {
                    i += Character.charCount(cp);
                } else {
                    while (i < text.length()) {
                        int c = text.codePointAt(i);
                        if (c == ' ' || breaksAnywhere(c)) break;
                        i += Character.charCount(c);
                    }
                }
                String unit = text.substring(start, i);
                int uw = m.width(unit);
                if (curW + uw <= width) {
                    cur.add(new Piece(unit, run.key()));
                    curW += uw;
                    continue;
                }
                if (curW > 0) {
                    lines.add(trim(cur));
                    cur = new ArrayList<>();
                    curW = 0;
                }
                int from = 0;
                while (from < unit.length()) {
                    int end = from;
                    while (end < unit.length()) {
                        int next = end + Character.charCount(unit.codePointAt(end));
                        if (end > from && m.width(unit.substring(from, next)) > width) break;
                        end = next;
                    }
                    String piece = unit.substring(from, end);
                    from = end;
                    if (from < unit.length()) {
                        lines.add(List.of(new Piece(piece, run.key())));
                    } else {
                        cur.add(new Piece(piece, run.key()));
                        curW = m.width(piece);
                    }
                }
            }
        }
        if (!cur.isEmpty()) lines.add(trim(cur));
        List<List<Seg>> out = new ArrayList<>();
        for (List<Piece> line : lines) {
            if (line.isEmpty()) continue;
            List<Seg> segs = new ArrayList<>();
            int x = 0;
            int k = 0;
            while (k < line.size()) {
                boolean key = line.get(k).key();
                StringBuilder text = new StringBuilder();
                while (k < line.size() && line.get(k).key() == key) text.append(line.get(k++).text());
                segs.add(new Seg(x, text.toString(), key));
                x += m.width(text.toString());
            }
            out.add(segs);
        }
        return out;
    }

    private static boolean endsWithSpace(List<Piece> cur) {
        return !cur.isEmpty() && cur.get(cur.size() - 1).text().equals(" ");
    }

    private static List<Piece> trim(List<Piece> line) {
        List<Piece> out = new ArrayList<>(line);
        while (!out.isEmpty() && out.get(out.size() - 1).text().equals(" ")) out.remove(out.size() - 1);
        return out;
    }
}
