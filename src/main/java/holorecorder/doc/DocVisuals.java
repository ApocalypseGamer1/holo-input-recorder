package holorecorder.doc;

import holorecorder.doc.DocLayout.Bar;
import holorecorder.doc.DocLayout.Choice;
import holorecorder.doc.DocLayout.Fill;
import holorecorder.doc.DocLayout.Hot;
import holorecorder.doc.DocLayout.HotKind;
import holorecorder.doc.DocLayout.Icon;
import holorecorder.doc.DocLayout.Meter;
import holorecorder.doc.DocLayout.Prim;
import holorecorder.doc.DocLayout.Text;
import holorecorder.menu.TextFit;

import java.util.ArrayList;
import java.util.List;

/** Lays out the picture blocks of a document page (icons, hotbar, steps, bars, buttons) as {@link Prim}s. No game classes. */
final class DocVisuals {
    static final int SLOT = 20, SLOT_BG = 0xFF0E1115, SLOT_EDGE = 0xFF3A414C, BOX = 0xFF1A1E25, BOX_ON = 0xFF263142,
            DETAIL = 0xFF12151A, TRACK = 0xFF2A2F38;
    private static final int ARROW = 12, MIN_STEP = 52, MAX_STEP = 96, MIN_ICON = 44, MAX_ICON = 76;

    private DocVisuals() {
    }

    /** Adds the block's prims from content height {@code y}; returns the height after it. */
    static int place(DocModel.Block b, int group, int y, int w, Meter m, int accent, List<Prim> out) {
        return switch (b.kind()) {
            case HOTBAR -> w >= 9 * SLOT ? hotbar(b.entries(), group, y, w, m, accent, out) : tiles(b.entries(), y, w, m, out);
            case STEPS -> steps(b.entries(), group, y, w, m, accent, out);
            case BARS -> bars(b.entries(), y, w, m, accent, out);
            case BUTTONS -> buttons(b.entries(), y, w, m, out);
            default -> tiles(b.entries(), y, w, m, out);
        };
    }

    private static void slot(int x, int y, List<Prim> out) {
        out.add(new Fill(x, y, SLOT, SLOT, SLOT_EDGE));
        out.add(new Fill(x + 1, y + 1, SLOT - 2, SLOT - 2, SLOT_BG));
    }

    private static void centred(String text, int x, int w, int y, int argb, Meter m, List<Prim> out) {
        out.add(new Text(x + (w - m.width(text)) / 2, y, text, argb, true));
    }

    /** Icons in slots with their names under them, in rows; hover shows the tip. */
    private static int tiles(List<DocModel.Entry> entries, int y, int w, Meter m, List<Prim> out) {
        int lh = m.lineHeight();
        int tw = Math.max(Math.min(MIN_ICON, w), Math.min(MAX_ICON, w / entries.size()));
        int perRow = Math.max(1, w / tw);
        int rows = (entries.size() + perRow - 1) / perRow;
        int th = SLOT + 3 + 2 * (lh + 1);
        for (int i = 0; i < entries.size(); i++) {
            DocModel.Entry e = entries.get(i);
            int inRow = Math.min(perRow, entries.size() - (i / perRow) * perRow);
            int x = (w - inRow * tw) / 2 + (i % perRow) * tw, ty = y + (i / perRow) * (th + 4);
            slot(x + (tw - SLOT) / 2, ty, out);
            if (!e.icon().isEmpty()) out.add(new Icon(x + (tw - 16) / 2, ty + 2, e.icon()));
            int ly = ty + SLOT + 3;
            for (String line : TextFit.wrap(e.label(), tw - 2, 2, m::width)) {
                centred(line, x, tw, ly, DocLayout.TEXT, m, out);
                ly += lh + 1;
            }
            if (!e.tip().isEmpty()) out.add(new Hot(x, ty, tw, th, HotKind.TIP, -1, i, e.label(), e.tip(), ""));
        }
        return y + rows * (th + 4);
    }

    /** The detail panel under a hotbar or a chain of steps: the picked entry's label and tip, one choice per entry. */
    private static int detail(List<DocModel.Entry> entries, int group, int y, int w, Meter m, int accent, boolean withLabel, List<Prim> out) {
        int lh = m.lineHeight(), step = lh + 1;
        List<List<String>> lines = new ArrayList<>();
        int most = 1;
        for (DocModel.Entry e : entries) {
            List<String> l = new ArrayList<>();
            if (withLabel) l.add(e.label());
            if (!e.tip().isEmpty()) l.addAll(TextFit.wrap(e.tip(), w - 12, 4, m::width));
            lines.add(l);
            most = Math.max(most, l.size());
        }
        int h = most * step + 8;
        out.add(new Fill(0, y, w, h, DETAIL));
        out.add(new Fill(0, y, 2, h, accent));
        for (int i = 0; i < entries.size(); i++) {
            List<Prim> shown = new ArrayList<>();
            int ty = y + 4;
            for (int k = 0; k < lines.get(i).size(); k++) {
                shown.add(new Text(7, ty, lines.get(i).get(k), withLabel && k == 0 ? DocLayout.KEY : DocLayout.TEXT, true));
                ty += step;
            }
            out.add(new Choice(group, i, List.copyOf(shown)));
        }
        return y + h;
    }

    /** The lobby hotbar: nine slots with the items in theirs, the key number under each; hover picks the text below. */
    private static int hotbar(List<DocModel.Entry> entries, int group, int y, int w, Meter m, int accent, List<Prim> out) {
        int lh = m.lineHeight();
        int x0 = (w - 9 * SLOT) / 2;
        out.add(new Fill(x0 - 1, y - 1, 9 * SLOT + 2, SLOT + 2, 0xFF000000));
        for (int s = 0; s < 9; s++) slot(x0 + s * SLOT, y, out);
        for (int i = 0; i < entries.size(); i++) {
            DocModel.Entry e = entries.get(i);
            int x = x0 + e.slot() * SLOT;
            out.add(new Choice(group, i, List.of(new Fill(x, y, SLOT, SLOT, accent), new Fill(x + 2, y + 2, SLOT - 4, SLOT - 4, SLOT_BG))));
            if (!e.icon().isEmpty()) out.add(new Icon(x + 2, y + 2, e.icon()));
            centred(String.valueOf(e.slot() + 1), x, SLOT, y + SLOT + 2, DocLayout.GREY, m, out);
            out.add(new Hot(x, y, SLOT, SLOT + lh + 2, HotKind.PICK, group, i, e.label(), e.tip(), ""));
        }
        return detail(entries, group, y + SLOT + lh + 6, w, m, accent, true, out);
    }

    /** Numbered boxes joined by arrows, in rows when narrow; hover or click picks the text shown below. */
    private static int steps(List<DocModel.Entry> entries, int group, int y, int w, Meter m, int accent, List<Prim> out) {
        int lh = m.lineHeight(), n = entries.size();
        int perRow = n;
        while (perRow > 1 && (w - (perRow - 1) * ARROW) / perRow < MIN_STEP) perRow--;
        int bw = Math.min(MAX_STEP, (w - (perRow - 1) * ARROW) / perRow);
        int bh = 4 + 16 + 3 + 2 * (lh + 1) + 2;
        int rows = (n + perRow - 1) / perRow;
        for (int i = 0; i < n; i++) {
            DocModel.Entry e = entries.get(i);
            int inRow = Math.min(perRow, n - (i / perRow) * perRow);
            int rowW = inRow * bw + (inRow - 1) * ARROW;
            int x = (w - rowW) / 2 + (i % perRow) * (bw + ARROW), by = y + (i / perRow) * (bh + 4);
            out.add(new Fill(x, by, bw, bh, DocLayout.BORDER));
            out.add(new Fill(x + 1, by + 1, bw - 2, bh - 2, BOX));
            out.add(new Choice(group, i, List.of(new Fill(x, by, bw, bh, accent), new Fill(x + 1, by + 1, bw - 2, bh - 2, BOX_ON))));
            out.add(new Text(x + 3, by + 3, String.valueOf(i + 1), DocLayout.GREY, false));
            if (!e.icon().isEmpty()) out.add(new Icon(x + (bw - 16) / 2, by + 4, e.icon()));
            int ly = by + 4 + 16 + 3;
            for (String line : TextFit.wrap(e.label(), bw - 4, 2, m::width)) {
                centred(line, x, bw, ly, 0xFFFFFFFF, m, out);
                ly += lh + 1;
            }
            if (i % perRow < inRow - 1 && i < n - 1) centred(">", x + bw, ARROW, by + (bh - lh) / 2, accent, m, out);
            out.add(new Hot(x, by, bw, bh, HotKind.PICK, group, i, e.label(), e.tip(), ""));
        }
        return detail(entries, group, y + rows * (bh + 4), w, m, accent, false, out);
    }

    /** A bar chart: name, a bar sized to the largest value (it grows when the page opens), and the value text on it. */
    private static int bars(List<DocModel.Entry> entries, int y, int w, Meter m, int accent, List<Prim> out) {
        int lh = m.lineHeight(), rowH = lh + 7;
        double max = 0;
        int labelW = 0;
        for (DocModel.Entry e : entries) {
            max = Math.max(max, e.value());
            labelW = Math.max(labelW, m.width(e.label()));
        }
        labelW = Math.min(labelW, w / 3);
        int bx = labelW + 6, bw = Math.max(20, w - bx);
        for (int i = 0; i < entries.size(); i++) {
            DocModel.Entry e = entries.get(i);
            int ry = y + i * (rowH + 3);
            String label = TextFit.ellipsize(e.label(), labelW, m::width);
            out.add(new Text(labelW - m.width(label), ry + 4, label, DocLayout.TEXT, true));
            out.add(new Fill(bx, ry, bw, rowH, TRACK));
            out.add(new Bar(bx, ry, bw, rowH, max <= 0 ? 0 : e.value() / max, e.colour() == 0 ? accent : e.colour()));
            if (!e.tip().isEmpty()) out.add(new Text(bx + 4, ry + 4, TextFit.ellipsize(e.tip(), bw - 8, m::width), 0xFFFFFFFF, true));
        }
        return y + entries.size() * (rowH + 3);
    }

    /** A row of buttons (in rows when narrow), centred. */
    private static int buttons(List<DocModel.Entry> entries, int y, int w, Meter m, List<Prim> out) {
        int bh = m.lineHeight() + 8, gap = 6;
        List<List<int[]>> rows = new ArrayList<>();   // {entry, width}
        List<int[]> row = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < entries.size(); i++) {
            int bw = Math.min(w, m.width(entries.get(i).label()) + 20);
            if (!row.isEmpty() && used + gap + bw > w) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            used += (row.isEmpty() ? 0 : gap) + bw;
            row.add(new int[]{i, bw});
        }
        if (!row.isEmpty()) rows.add(row);
        int ry = y;
        for (List<int[]> r : rows) {
            int rowW = -gap;
            for (int[] b : r) rowW += b[1] + gap;
            int x = (w - rowW) / 2;
            for (int[] b : r) {
                DocModel.Entry e = entries.get(b[0]);
                out.add(new Hot(x, ry, b[1], bh, HotKind.BUTTON, -1, b[0], TextFit.ellipsize(e.label(), b[1] - 8, m::width), "", e.action()));
                x += b[1] + gap;
            }
            ry += bh + 4;
        }
        return ry;
    }
}
