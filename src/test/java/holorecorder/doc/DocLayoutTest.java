package holorecorder.doc;

import holorecorder.doc.DocLayout.Fill;
import holorecorder.doc.DocLayout.Prim;
import holorecorder.doc.DocLayout.Result;
import holorecorder.doc.DocLayout.Text;
import holorecorder.menu.MenuLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The document JSON is read forgivingly, and its layout stays inside the panel at every screen size and in long languages. */
class DocLayoutTest {
    static final int LH = 9;
    static final int[][] REAL = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {320, 180}, {640, 480}};
    static final int[][] TINY = {{213, 120}, {160, 90}, {100, 60}};

    /** Worst-case widths: 6 px per character, 4 for a space, 9 for CJK. */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += cp == ' ' ? 4 : Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA ? 9 : 6;
        }
        return w;
    }

    static final DocLayout.Meter METER = new DocLayout.Meter() {
        @Override public int lineHeight() { return LH; }
        @Override public int width(String text) { return DocLayoutTest.width(text); }
    };

    /** A German guide with long compound words, a list, a numbered bullet and a very long page; plus a Japanese page. */
    static final String SAMPLE = """
            {"v": 1, "future_field": {"a": [1]}, "title": "Holoplayer-Anleitung", "subtitle": "Server-Regeln - Holoplayer Practice",
             "accent": "#ffb347", "page": 1, "close": "Schließen", "back": "Zurück",
             "pages": [
              {"head": "Starte ein Duell", "blocks": [
                {"t": "p", "runs": [{"text": "Benutze das "}, {"text": "Schwert", "s": "key"}, {"text": " in deiner Schnellleiste, um ein Duell zu wählen, oder klicke auf eine "}, {"text": "Statue", "s": "key"}, {"text": " in der Lobby."}]},
                {"t": "gap"},
                {"t": "p", "runs": [{"text": "Beim ersten Mal wählst du, ob deine Runden aufgenommen werden dürfen."}]},
                {"t": "mystery", "runs": []}
              ]},
              {"head": "Die Modi", "blocks": [
                {"t": "bullet", "runs": [{"text": "Schwert", "s": "key"}, {"text": ": Diamantschwert und Rüstung"}]},
                {"t": "bullet", "mark": "2.", "runs": [{"text": "Donaudampfschifffahrtsgesellschaftskapitänsmützenabzeichen", "s": "sparkle"}]},
                {"t": "bullet", "runs": ["plain string run"]},
                {"t": "gap"},
                {"t": "p", "runs": [{"text": "Ein grauer Modus kommt bald."}]}
              ]},
              {"head": "日本語のページ", "blocks": [
                {"t": "p", "runs": [{"text": "このサーバーはあなたの戦い方を学び、あなたのAIコピーである"}, {"text": "ホロプレイヤー", "s": "key"}, {"text": "を作ります。ページをめくって、どのように動くか見てください。"}]}
              ]},
              {"head": "Lang", "blocks": [
                {"t": "p", "runs": [{"text": "%s"}]}
              ]},
              {"head": "", "blocks": []}
             ]}
            """.formatted("Lorem ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat sed diam voluptua. ".repeat(12));

    static DocModel.Doc sample() {
        return DocModel.parse(SAMPLE);
    }

    private static boolean inside(Rect outer, Rect r) {
        return r.x() >= outer.x() && r.y() >= outer.y() && r.x() + r.w() <= outer.x() + outer.w() && r.y() + r.h() <= outer.y() + outer.h();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    @Test
    void readsTheFieldsAndSkipsWhatItDoesNotKnow() {
        DocModel.Doc d = sample();
        assertEquals("Holoplayer-Anleitung", d.title());
        assertEquals("Server-Regeln - Holoplayer Practice", d.subtitle());
        assertEquals(0xFFFFB347, d.accent());
        assertEquals(1, d.page());
        assertEquals("Schließen", d.close());
        assertEquals("Zurück", d.back());
        assertEquals(5, d.pages().size());
        assertEquals(3, d.pages().get(0).blocks().size());   // the unknown block type is gone
        DocModel.Block first = d.pages().get(0).blocks().get(0);
        assertEquals(DocModel.Kind.PARAGRAPH, first.kind());
        assertEquals(List.of(false, true, false, true, false), first.runs().stream().map(DocModel.Run::key).toList());
        DocModel.Block numbered = d.pages().get(1).blocks().get(1);
        assertEquals("2.", numbered.mark());
        assertFalse(numbered.runs().get(0).key());   // an unknown style is drawn plain
        assertEquals("plain string run", d.pages().get(1).blocks().get(2).runs().get(0).text());
    }

    @Test
    void refusesWhatIsNotADocumentAndFillsDefaults() {
        assertNull(DocModel.parse(null));
        assertNull(DocModel.parse("[1,2]"));
        assertNull(DocModel.parse("not json"));
        assertNull(DocModel.parse("{\"title\": \"x\"}"));
        assertNull(DocModel.parse("{\"pages\": []}"));
        DocModel.Doc d = DocModel.parse("{\"pages\": [{\"blocks\": [{\"t\": \"p\", \"runs\": [{\"text\": \"a\\nb\"}]}]}], \"page\": 99, \"accent\": \"nope\"}");
        assertNotNull(d);
        assertEquals(0, d.page());
        assertEquals(DocModel.DEFAULT_ACCENT, d.accent());
        assertNull(d.back());
        assertEquals("X", d.close());
        assertEquals("a b", d.pages().get(0).blocks().get(0).runs().get(0).text());
    }

    // ---- layout -----------------------------------------------------------------------------------------------------

    @Test
    void everythingStaysInsideThePanelAtEverySize() {
        DocModel.Doc d = sample();
        for (int[] size : concat(REAL, TINY)) {
            Result r = DocLayout.compute(size[0], size[1], METER, d);
            String at = size[0] + "x" + size[1];
            boolean real = size[0] >= 213;
            if (real) assertTrue(inside(new Rect(0, 0, size[0], size[1]), r.panel()), "panel on screen " + at);
            for (Rect part : new Rect[]{r.header(), r.progress(), r.viewport(), r.footer(), r.close().box()}) {
                assertTrue(inside(r.panel(), part), "part inside panel " + at + " " + part);
            }
            if (r.list() != null) assertTrue(inside(r.panel(), r.list()), "list " + at);
            if (r.back() != null) assertTrue(inside(r.footer(), r.back().box()), "back " + at);
            assertTrue(inside(r.footer(), r.close().box()), "close " + at);
            assertTrue(inside(r.footer(), r.prev()) && inside(r.footer(), r.next()) && inside(r.footer(), r.counter()), "pager " + at);
            List<Rect> row = new ArrayList<>(List.of(r.prev(), r.counter(), r.next(), r.close().box()));
            if (r.back() != null) row.add(r.back().box());
            for (int i = 0; i < row.size(); i++) {
                for (int j = i + 1; j < row.size(); j++) assertFalse(overlap(row.get(i), row.get(j)), "footer overlap " + at + " " + i + "," + j);
            }
            assertFalse(overlap(r.viewport(), r.footer()), "viewport over footer " + at);
            assertFalse(overlap(r.viewport(), r.header()), "viewport over header " + at);
            assertEquals(d.pages().size(), r.contents().size());
            assertEquals(r.list() != null ? d.pages().size() : 0, r.chapters().size());
            for (DocLayout.ChapterBox c : r.chapters()) {
                assertTrue(c.box().x() >= 0 && c.box().x() + c.box().w() <= r.list().w(), "chapter x " + at);
                assertTrue(width(c.label()) <= c.box().w(), "chapter label " + at + " " + c.label());
            }
            for (int p = 0; p < d.pages().size(); p++) {
                DocLayout.Content c = r.contents().get(p);
                for (Prim prim : c.prims()) {
                    if (prim instanceof Fill f) {
                        assertTrue(f.x() >= 0 && f.x() + f.w() <= r.contentWidth(), "fill x " + at);
                        assertTrue(f.y() >= 0 && f.y() + f.h() <= c.height(), "fill y " + at + " p" + p);
                    } else if (prim instanceof Text t) {
                        assertTrue(t.x() >= 0 && t.x() + width(t.text()) <= r.contentWidth(), "text x " + at + " p" + p + " " + t.text());
                        assertTrue(t.y() >= 0 && t.y() + LH <= c.height(), "text y " + at + " p" + p);
                    }
                }
                assertTrue(r.maxScroll(p) >= 0);
            }
            for (Text t : r.headerText()) {
                assertTrue(t.x() >= r.panel().x() && t.x() + width(t.text()) <= r.panel().x() + r.panel().w(), "header text " + at);
            }
        }
    }

    @Test
    void theChapterListAppearsOnlyWhenThereIsRoomAndPages() {
        DocModel.Doc d = sample();
        assertNotNull(DocLayout.compute(1920, 1080, METER, d).list());
        assertNotNull(DocLayout.compute(480, 270, METER, d).list());
        assertNull(DocLayout.compute(213, 120, METER, d).list());
        DocModel.Doc one = new DocModel.Doc("Rules", "", 0xFFFFB347, 0, null, "Close", List.of(d.pages().get(0)));
        Result r = DocLayout.compute(1920, 1080, METER, one);
        assertNull(r.list());
        assertNull(r.prev());
        assertNull(r.back());
    }

    @Test
    void aLongPageScrollsAndAShortOneDoesNot() {
        DocModel.Doc d = sample();
        Result r = DocLayout.compute(480, 270, METER, d);
        assertTrue(r.maxScroll(3) > 0, "the long page scrolls");
        assertEquals(0, r.maxScroll(4), "the empty page fits");
        Result small = DocLayout.compute(213, 120, METER, d);
        assertTrue(small.maxScroll(0) > 0, "even the short page scrolls in a tiny window");
    }

    @Test
    void wrappedLinesKeepTheirStyleAndNeverExceedTheWidth() {
        List<DocModel.Run> runs = List.of(new DocModel.Run("Open the ", false), new DocModel.Run("Settings", true),
                new DocModel.Run(" menu and click ", false), new DocModel.Run("Guide book", true), new DocModel.Run(" to read this again.", false));
        for (int w : new int[]{30, 60, 100, 200, 1000}) {
            List<List<DocLayout.Seg>> lines = DocLayout.wrapRuns(runs, w, METER);
            StringBuilder all = new StringBuilder();
            for (List<DocLayout.Seg> line : lines) {
                int end = 0;
                for (DocLayout.Seg s : line) {
                    assertEquals(end, s.x());
                    end += width(s.text());
                    all.append(s.text());
                }
                assertTrue(end <= Math.max(w, 6), "line width " + end + " in " + w);
                assertFalse(line.get(0).text().startsWith(" "));
                assertFalse(line.get(line.size() - 1).text().endsWith(" "));
            }
            assertEquals("Open the Settings menu and click Guide book to read this again.".replace(" ", ""), all.toString().replace(" ", ""));
        }
        List<List<DocLayout.Seg>> one = DocLayout.wrapRuns(runs, 1000, METER);
        assertEquals(1, one.size());
        assertEquals(List.of(false, true, false, true, false), one.get(0).stream().map(DocLayout.Seg::key).toList());
    }

    @Test
    void cjkBreaksAnywhereAndALongWordBreaksInsideItself() {
        List<List<DocLayout.Seg>> cjk = DocLayout.wrapRuns(List.of(new DocModel.Run("このサーバーはあなたの戦い方を学びます", false)), 45, METER);
        assertTrue(cjk.size() > 2);
        for (List<DocLayout.Seg> l : cjk) assertTrue(width(l.get(0).text()) <= 45);
        List<List<DocLayout.Seg>> word = DocLayout.wrapRuns(List.of(new DocModel.Run("Donaudampfschifffahrtsgesellschaft", false)), 40, METER);
        assertTrue(word.size() > 1);
        for (List<DocLayout.Seg> l : word) assertTrue(width(l.get(0).text()) <= 40);
    }

    private static int[][] concat(int[][] a, int[][] b) {
        int[][] out = new int[a.length + b.length][];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
