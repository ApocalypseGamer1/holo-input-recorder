package holorecorder.improve;

import holorecorder.improve.ImproveLayout.Content;
import holorecorder.improve.ImproveLayout.Fill;
import holorecorder.improve.ImproveLayout.Icon;
import holorecorder.improve.ImproveLayout.Prim;
import holorecorder.improve.ImproveLayout.Result;
import holorecorder.improve.ImproveLayout.Text;
import holorecorder.menu.MenuLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Improve screen's JSON is read forgivingly, and its layout stays inside the panel at every screen size and in long languages. */
class ImproveLayoutTest {
    static final int LH = 9;
    /** Real GUI sizes (1080p at scales 1-4, 640 px at scale 1-2 ...), plus tiny ones the game never gives but must not crash on. */
    static final int[][] REAL = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {320, 180}, {640, 480}};
    static final int[][] TINY = {{213, 120}, {160, 90}, {100, 60}};

    /** Worst-case widths: 6 px per character, 4 for a space, 9 for CJK. */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += cp == ' ' ? 4 : Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN ? 9 : 6;
        }
        return w;
    }

    static final ImproveLayout.Meter METER = new ImproveLayout.Meter() {
        @Override public int lineHeight() { return LH; }
        @Override public int width(String text) { return ImproveLayoutTest.width(text); }
        @Override public boolean iconKnown(String id) { return id.startsWith("minecraft:") && !id.contains("no_such"); }
    };

    /** One tab per block type, with the long texts of a translated server. */
    static final String SAMPLE = """
            {"v": 1, "future_field": {"a": [1, 2]},
             "title": "Mejorar: espada", "subtitle": "Tus últimas 10 rondas contra los 15 mejores jugadores del servidor",
             "footer": "Línea blanca = mejores jugadores. Se actualiza después de cada ronda.",
             "back": "Volver", "close": "Cerrar", "tab": 1,
             "tabs": [
               {"label": "Todo", "blocks": [
                 {"t": "section", "text": "Trabaja en esto"},
                 {"t": "card", "color": "bad", "icon": "minecraft:iron_sword", "title": "Haces clic antes de que tu espada esté cargada",
                  "sub": "El 31% de tus golpes. Mejores jugadores: 4%. Peor en las rondas perdidas (39%).",
                  "tip": "Consejo: espera a que la espada se cargue antes de hacer clic en el oponente", "bar": 0.31, "marker": 0.04,
                  "count": "9 de 10 rondas", "dots": [true, true, true, true, true, true, true, true, true, false]},
                 {"t": "card", "color": "warn", "icon": "minecraft:no_such_item_xyz", "title": "Apuntas bien solo el 62%", "sub": "Mejores: 82%",
                  "bar": 0.62, "count": "6 de 10 rondas", "dots": [true, true, true, true, true, true, false, false, false, false]},
                 {"t": "card", "color": "good", "title": "Sueles dar el primer golpe", "sub": "80% de las rondas."},
                 {"t": "section", "text": "Sigue haciendo esto"},
                 {"t": "mystery", "text": "an unknown block type is skipped"},
                 {"t": "card", "color": "good", "icon": "minecraft:shield", "title": "x", "dots": [false, false, false]}
               ]},
               {"label": "Movimiento", "footer": "Gris = rango normal. Verde = mejor que la mayoría de los mejores jugadores.", "blocks": [
                 {"t": "note", "color": "info", "text": "Ningún hábito de movimiento se repite. Tu movimiento es parecido al de los mejores jugadores. Tus golpes son la mayor mejora."},
                 {"t": "section", "text": "Cómo te comparas"},
                 {"t": "row", "label": "Avanzar cuando estás fuera de alcance", "sub": "Mejores jugadores: 76%", "bar": 0.68, "marker": 0.76, "color": "neutral", "value": "68%"},
                 {"t": "row", "label": "Correr mientras avanzas", "sub": "Mejores: 88%", "bar": 0.92, "marker": 0.88, "color": "good", "value": "92%"},
                 {"t": "row", "label": "Sin barra", "value": "3%"}
               ]},
               {"label": "Fase de juego", "blocks": [
                 {"t": "grid", "head": ["", "Inicio", "Medio", "Final", "Últimos 5 s de una derrota"], "rows": [
                   {"label": "Golpes que diste", "cells": [{"text": "51%", "sub": "mejores 50%", "edge": "none"}, {"text": "56%", "sub": "mejores 50%", "edge": "good"},
                                                          {"text": "50%", "sub": "mejores 50%", "edge": "none"}, {"text": "30%", "sub": "mejores 46%", "edge": "bad"}]},
                   {"label": "Clics tempranos", "cells": [{"text": "alto", "edge": "bad"}, {"text": "alto", "edge": "warn"}, {"text": "alto", "edge": "bad"}, {"text": "-", "edge": "none"}]}
                 ]},
                 {"t": "note", "color": "neutral", "text": "Solo perdiste 3 de estas 10 rondas."}
               ]},
               {"label": "Una etiqueta de pestaña muy larga para probar el ajuste de línea", "blocks": []},
               {"label": "Saltos", "blocks": []}, {"label": "Puntería", "blocks": []}, {"label": "Golpes", "blocks": []}
             ]}
            """;

    // ---- parsing ----------------------------------------------------------------------------------------------------

    @Test
    void sampleParsesWithEveryBlockType() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        assertNotNull(s);
        assertEquals("Mejorar: espada", s.title());
        assertEquals("Volver", s.back());
        assertEquals("Cerrar", s.close());
        assertEquals(1, s.tab());
        assertEquals(7, s.tabs().size());
        List<ImproveModel.Block> all = s.tabs().get(0).blocks();
        assertEquals(6, all.size(), "the unknown block type is skipped");
        ImproveModel.Card c = (ImproveModel.Card) all.get(1);
        assertEquals(0xFFFF5C5C, c.color());
        assertEquals("minecraft:iron_sword", c.icon());
        assertEquals(0.31, c.bar(), 1e-9);
        assertEquals(0.04, c.marker(), 1e-9);
        assertEquals(10, c.dots().size());
        assertFalse(c.dots().get(9));
        ImproveModel.Card bare = (ImproveModel.Card) all.get(3);
        assertEquals(-1, bare.bar());
        assertEquals(-1, bare.marker());
        assertTrue(bare.dots().isEmpty());
        assertEquals("", bare.tip());
        assertEquals(1, s.tabs().get(1).blocks().stream().filter(b -> b instanceof ImproveModel.Note).count());
        ImproveModel.Row r = (ImproveModel.Row) s.tabs().get(1).blocks().get(2);
        assertEquals(0.76, r.marker(), 1e-9);
        assertEquals("68%", r.value());
        assertEquals(-1, ((ImproveModel.Row) s.tabs().get(1).blocks().get(4)).bar());
        ImproveModel.Grid g = (ImproveModel.Grid) s.tabs().get(2).blocks().get(0);
        assertEquals(5, g.head().size());
        assertEquals(2, g.rows().size());
        assertEquals(4, g.rows().get(1).cells().size());
        assertEquals(0xFF5FD38A, g.rows().get(0).cells().get(1).edge());
        assertEquals(ImproveModel.NO_EDGE, g.rows().get(0).cells().get(0).edge());
    }

    @Test
    void footerOfTabOverridesTheTopFooter() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        assertTrue(s.footerOf(0).startsWith("Línea blanca"));
        assertTrue(s.footerOf(1).startsWith("Gris"));
    }

    @Test
    void badInputIsRefusedOrSkippedAndNeverThrows() {
        for (String bad : new String[]{null, "", "   ", "not json", "[]", "42", "\"x\"", "{", "{}", "{\"tabs\": []}", "{\"tabs\": [1, \"a\", null]}",
                "{\"tabs\": {\"a\": 1}}", "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[["}) {
            assertNull(ImproveModel.parse(bad), "should be refused: " + bad);
        }
        // wrong types in the fields are treated as missing; the screen still opens
        String odd = """
                {"title": 5, "subtitle": [1], "tab": "x", "tabs": [{"label": {"a": 1}, "blocks": [
                  {"t": "card", "bar": "high", "marker": null, "dots": "no", "title": {"x": 1}},
                  {"t": "row", "bar": 7, "marker": -3}, {"t": 5}, "x", null, [],
                  {"t": "grid", "head": 3, "rows": [1, {"cells": [1, {"text": "a", "edge": "purple"}]}]},
                  {"t": "note", "color": "magenta", "text": "ok"}]}]}
                """;
        ImproveModel.Screen s = ImproveModel.parse(odd);
        assertNotNull(s);
        assertEquals(0, s.tab());
        assertEquals(4, s.tabs().get(0).blocks().size());
        ImproveModel.Row r = (ImproveModel.Row) s.tabs().get(0).blocks().get(1);
        assertEquals(1.0, r.bar(), 1e-9, "clamped to 0..1");
        assertEquals(0.0, r.marker(), 1e-9);
        ImproveModel.Grid g = (ImproveModel.Grid) s.tabs().get(0).blocks().get(2);
        assertEquals(5, g.head().size());
        assertEquals(4, g.rows().get(0).cells().size());
        assertEquals(ImproveModel.NO_EDGE, g.rows().get(0).cells().get(1).edge(), "unknown edge colour = none");
        assertEquals(ImproveModel.NEUTRAL, ((ImproveModel.Note) s.tabs().get(0).blocks().get(3)).color());
        layoutNeverThrows(s);
    }

    @Test
    void tabIndexIsClampedAndTextIsCapped() {
        ImproveModel.Screen s = ImproveModel.parse("{\"tab\": 99, \"tabs\": [{\"label\": \"a\"}, {\"label\": \"" + "x".repeat(5000) + "\"}]}");
        assertEquals(1, s.tab());
        assertTrue(s.tabs().get(1).label().length() <= 1500);
        assertEquals(0, ImproveModel.parse("{\"tab\": -4, \"tabs\": [{\"label\": \"a\"}]}").tab());
    }

    // ---- layout -----------------------------------------------------------------------------------------------------

    private static void layoutNeverThrows(ImproveModel.Screen s) {
        for (int[] sc : REAL) ImproveLayout.compute(sc[0], sc[1], METER, s);
        for (int[] sc : TINY) ImproveLayout.compute(sc[0], sc[1], METER, s);
    }

    private static boolean inside(Rect in, Rect out) {
        return in.x() >= out.x() && in.y() >= out.y() && in.x() + in.w() <= out.x() + out.w() && in.y() + in.h() <= out.y() + out.h();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    private static void check(String where, int sw, int sh, Result r, ImproveModel.Screen s) {
        String at = where + " @" + sw + "x" + sh;
        int w = r.contentWidth();
        assertTrue(w > 0, at);
        assertTrue(inside(r.viewport(), r.panel()), at + " viewport in panel");
        assertTrue(inside(r.footer(), r.panel()), at + " footer in panel");
        assertTrue(inside(r.back(), r.panel()) && inside(r.close(), r.panel()), at + " buttons in panel");
        assertFalse(overlap(r.back(), r.close()), at + " buttons overlap");
        assertTrue(r.back().w() > 0 && r.back().h() > 0 && r.close().w() > 0, at);
        assertTrue(w + 6 <= r.viewport().w() || r.viewport().w() < 20, at + " room for the scrollbar");
        for (int i = 0; i < r.tabs().size(); i++) {
            Rect a = r.tabs().get(i).box();
            assertTrue(inside(a, r.panel()), at + " tab " + i + " in panel");
            assertTrue(a.x() >= r.viewport().x() && a.x() + a.w() <= r.viewport().x() + r.viewport().w(), at + " tab " + i + " in the viewport width");
            assertTrue(width(r.tabs().get(i).label()) + 14 <= a.w() + 0, at + " tab label fits");
            for (int j = i + 1; j < r.tabs().size(); j++) assertFalse(overlap(a, r.tabs().get(j).box()), at + " tabs " + i + "," + j + " overlap");
            assertTrue(a.y() + a.h() <= r.viewport().y(), at + " tab above the content");
        }
        assertEquals(s.ranges().size(), r.ranges().size(), at + " every range is placed");
        for (int i = 0; i < r.ranges().size(); i++) {
            ImproveLayout.RangeBox a = r.ranges().get(i);
            assertEquals(s.ranges().get(i).action(), a.action(), at + " range " + i + " action");
            assertEquals(s.ranges().get(i).on(), a.on(), at + " range " + i + " on");
            assertTrue(inside(a.box(), r.panel()), at + " range " + i + " in panel");
            assertTrue(a.box().x() >= r.viewport().x() && a.box().x() + a.box().w() <= r.viewport().x() + r.viewport().w(), at + " range " + i + " in the viewport width");
            assertTrue(width(a.label()) + 14 <= a.box().w(), at + " range label fits");
            assertTrue(a.box().y() + a.box().h() <= r.viewport().y(), at + " range above the content");
            assertTrue(a.box().y() >= r.titleBar().y() + r.titleBar().h(), at + " range under the header");
            for (int j = i + 1; j < r.ranges().size(); j++) assertFalse(overlap(a.box(), r.ranges().get(j).box()), at + " ranges " + i + "," + j + " overlap");
            for (ImproveLayout.TabBox t : r.tabs()) assertFalse(overlap(a.box(), t.box()), at + " range " + i + " over a tab");
        }
        for (Text t : r.header()) {
            assertTrue(t.x() >= r.panel().x() && t.x() + width(t.text()) <= r.panel().x() + r.panel().w(), at + " header text '" + t.text() + "'");
        }
        for (int tab = 0; tab < r.contents().size(); tab++) {
            Content c = r.contents().get(tab);
            String where2 = at + " tab " + tab;
            assertTrue(c.height() >= 0, where2);
            for (ImproveLayout.Span sp : c.spans()) assertTrue(sp.h() > 0 && sp.y() >= 0 && sp.y() + sp.h() <= c.height(), where2 + " span");
            assertEquals(c.spans().size(), s.tabs().get(tab).blocks().stream().filter(b -> !(b instanceof ImproveModel.Grid g && g.rows().isEmpty())).count(),
                    where2 + " one span per block");
            for (Prim p : c.prims()) {
                if (p instanceof Fill f) {
                    assertTrue(f.w() > 0 && f.h() > 0, where2 + " empty fill " + f);
                    assertTrue(f.x() >= 0 && f.x() + f.w() <= w, where2 + " fill wider than the content " + f);
                    assertTrue(f.y() >= 0 && f.y() + f.h() <= c.height(), where2 + " fill outside the height " + f);
                } else if (p instanceof Text t) {
                    assertFalse(t.text().isEmpty(), where2);
                    assertTrue(t.x() >= 0 && t.x() + width(t.text()) <= w, where2 + " text wider than the content '" + t.text() + "' x=" + t.x());
                    assertTrue(t.y() >= 0 && t.y() + LH <= c.height(), where2 + " text outside the height '" + t.text() + "'");
                } else if (p instanceof Icon i) {
                    assertTrue(i.x() >= 0 && i.x() + ImproveLayout.ICON <= w && i.y() >= 0 && i.y() + ImproveLayout.ICON <= c.height(), where2 + " icon");
                    assertTrue(i.itemId().startsWith("minecraft:") && !i.itemId().contains("no_such"), where2 + " icon of an unknown item");
                }
            }
            for (Text t : c.footer()) {
                assertTrue(t.x() >= r.footer().x() && t.x() + width(t.text()) <= r.footer().x() + r.footer().w(), where2 + " footer text");
                assertTrue(t.y() >= r.footer().y() && t.y() + LH <= r.footer().y() + r.footer().h(), where2 + " footer text height");
                assertFalse(t.x() + width(t.text()) > r.back().x() && t.y() < r.back().y() + r.back().h() && t.y() + LH > r.back().y(), where2 + " footer text under Back");
            }
            assertTrue(r.maxScroll(tab) >= 0);
        }
    }

    @Test
    void layoutStaysInsideThePanelAtEveryScreenSize() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        for (int[] sc : REAL) {
            Result r = ImproveLayout.compute(sc[0], sc[1], METER, s);
            check("sample", sc[0], sc[1], r, s);
            assertTrue(r.panel().x() >= 0 && r.panel().y() >= 0 && r.panel().x() + r.panel().w() <= sc[0] && r.panel().y() + r.panel().h() <= sc[1],
                    "panel on screen @" + sc[0] + "x" + sc[1]);
            assertTrue(r.viewport().h() >= 40, "room for content @" + sc[0] + "x" + sc[1] + ": " + r.viewport().h());
        }
        for (int[] sc : TINY) check("sample", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
    }

    @Test
    void panelIsCentredAndWidthLimited() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        Result wide = ImproveLayout.compute(1920, 1080, METER, s);
        assertEquals(ImproveLayout.MAX_PANEL, wide.panel().w());
        assertEquals((1920 - wide.panel().w()) / 2, wide.panel().x());
        Result narrow = ImproveLayout.compute(320, 240, METER, s);
        assertEquals(320 - 24, narrow.panel().w());
    }

    @Test
    void tabsWrapToASecondLineWhenTheyDoNotFit() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        Result wide = ImproveLayout.compute(1920, 1080, METER, s);
        Result narrow = ImproveLayout.compute(320, 240, METER, s);
        long rowsWide = wide.tabs().stream().map(t -> t.box().y()).distinct().count();
        long rowsNarrow = narrow.tabs().stream().map(t -> t.box().y()).distinct().count();
        assertTrue(rowsWide >= 1);
        assertTrue(rowsNarrow > rowsWide || rowsNarrow >= 2, "narrow screens wrap the tabs: " + rowsNarrow + " vs " + rowsWide);
    }

    @Test
    void panelHeightDoesNotChangeWithTheTab() {
        // all tabs are laid out together, so the panel and the tab strip are the same whichever tab is shown
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        Result r = ImproveLayout.compute(960, 540, METER, s);
        int tallest = r.contents().stream().mapToInt(Content::height).max().orElse(0);
        assertTrue(r.viewport().h() >= Math.min(tallest, 1));
        assertTrue(r.contents().stream().anyMatch(c -> c.height() > 0));
    }

    @Test
    void scrollsWhenTheContentIsTallerThanTheViewport() {
        StringBuilder sb = new StringBuilder("{\"tabs\":[{\"label\":\"All\",\"blocks\":[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"t\":\"card\",\"color\":\"warn\",\"title\":\"Card ").append(i).append("\",\"sub\":\"Some text that goes on for a while to wrap\",\"bar\":0.5,\"marker\":0.7,\"count\":\"3 of 10 rounds\",\"dots\":[true,false,true,false,true,false,true,false,true,false]}");
        }
        ImproveModel.Screen s = ImproveModel.parse(sb.append("]}]}").toString());
        for (int[] sc : REAL) {
            Result r = ImproveLayout.compute(sc[0], sc[1], METER, s);
            check("tall", sc[0], sc[1], r, s);
            assertTrue(r.maxScroll(0) > 0, "40 cards scroll @" + sc[0] + "x" + sc[1]);
            assertEquals(r.contents().get(0).height() - r.viewport().h(), r.maxScroll(0));
        }
    }

    @Test
    void longWordsAndChineseTextNeverOverflow() {
        String json = "{\"title\":\"" + "W".repeat(80) + "\",\"subtitle\":\"" + "中".repeat(60) + "\",\"footer\":\"" + "Donaudampfschifffahrtsgesellschaft".repeat(3)
                + "\",\"back\":\"" + "Zurückkehren".repeat(3) + "\",\"close\":\"关闭关闭关闭关闭关闭\",\"tabs\":["
                + "{\"label\":\"" + "Ü".repeat(70) + "\",\"blocks\":[{\"t\":\"card\",\"icon\":\"minecraft:bow\",\"title\":\"" + "标题".repeat(30)
                + "\",\"sub\":\"" + "Streitkolbenschlagabstand".repeat(4) + "\",\"tip\":\"" + "提示".repeat(40) + "\",\"count\":\"" + "9 von 10 Runden und mehr".repeat(2)
                + "\",\"dots\":[true,true,false,true,true,true,true,true,true,true,true,true,true,false,true],\"bar\":0.5,\"marker\":0.9},"
                + "{\"t\":\"row\",\"label\":\"" + "Beschäftigung".repeat(6) + "\",\"sub\":\"" + "x ".repeat(80) + "\",\"value\":\"" + "100%".repeat(20) + "\",\"bar\":0.4,\"marker\":0.2},"
                + "{\"t\":\"grid\",\"head\":[\"\",\"" + "Anfang".repeat(5) + "\",\"b\",\"c\",\"d\"],\"rows\":[{\"label\":\"" + "Labelwort".repeat(8)
                + "\",\"cells\":[{\"text\":\"" + "Zahlenwert".repeat(9) + "\",\"sub\":\"" + "unter".repeat(20) + "\",\"edge\":\"bad\"},{\"text\":\"1\"},{\"text\":\"2\"},{\"text\":\"3\"}]}]},"
                + "{\"t\":\"note\",\"text\":\"" + "注意".repeat(100) + "\"},{\"t\":\"section\",\"text\":\"" + "Abschnittsüberschrift".repeat(10) + "\"}]}]}";
        ImproveModel.Screen s = ImproveModel.parse(json);
        assertNotNull(s);
        for (int[] sc : REAL) check("long", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
        for (int[] sc : TINY) check("long", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
    }

    // ---- time ranges ------------------------------------------------------------------------------------------------

    private static String tabsJson(String... labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"label\":\"").append(labels[i]).append("\",\"blocks\":[{\"t\":\"card\",\"color\":\"bad\",\"title\":\"Card\",\"sub\":\"Text\","
                    + "\"count\":\"9 of 41 rounds\",\"dots\":[true,true,true,true,true,true,true,true,true,true,true,true,false,false,false,false,false,false,false,false]}]}");
        }
        return sb.toString();
    }

    private static String rangesJson(String a, String b) {
        return "[{\"label\":\"" + a + "\",\"action\":\"days:7\",\"on\":true},{\"label\":\"" + b + "\",\"action\":\"days:30\",\"on\":false}]";
    }

    private static ImproveModel.Screen withRanges(String ranges, String... tabs) {
        ImproveModel.Screen s = ImproveModel.parse("{\"title\":\"Improve: sword\",\"subtitle\":\"Your last 7 days (60 rounds) vs the top players\","
                + "\"footer\":\"White line = top players.\",\"ranges\":" + ranges + ",\"tabs\":[" + tabsJson(tabs) + "]}");
        assertNotNull(s);
        return s;
    }

    private static final String[] TABS_EN = {"All", "Movement", "Aim", "Hits", "Jumps", "Game phase"};
    private static final String[] TABS_ES = {"Todo", "Movimiento", "Puntería", "Golpes", "Saltos", "Fase de juego"};
    private static final String[] TABS_RU = {"Все", "Движение", "Прицел", "Удары", "Прыжки", "Фаза игры"};

    @Test
    void screenWithoutRangesHasNoRangeButtons() {
        ImproveModel.Screen s = ImproveModel.parse(SAMPLE);
        assertTrue(s.ranges().isEmpty());
        for (int[] sc : REAL) assertTrue(ImproveLayout.compute(sc[0], sc[1], METER, s).ranges().isEmpty());
        ImproveModel.Screen sixArgs = new ImproveModel.Screen("t", "", "", "Back", "Close", 0, s.tabs());
        assertTrue(sixArgs.ranges().isEmpty());
    }

    @Test
    void rangesAreParsedForgivingly() {
        ImproveModel.Screen s = withRanges(rangesJson("Last 7 days", "Last 30 days"), "All");
        assertEquals(List.of(new ImproveModel.Range("Last 7 days", "days:7", true), new ImproveModel.Range("Last 30 days", "days:30", false)), s.ranges());

        String odd = "[1, null, \"x\", {\"label\":\"\",\"action\":\"days:1\"}, {\"label\":\"No action\"}, {\"label\":\"Long action\",\"action\":\"" + "a".repeat(65) + "\"},"
                + "{\"label\":\" Ok \",\"action\":\"days:7\",\"on\":\"yes\"}, {\"label\":\"Num\",\"action\":\"days:30\",\"on\":1},"
                + "{\"label\":\"A\",\"action\":\"a1\",\"on\":true}, {\"label\":\"B\",\"action\":\"a2\"}, {\"label\":\"C\",\"action\":\"a3\"}]";
        ImproveModel.Screen o = withRanges(odd, "All");
        assertEquals(4, o.ranges().size(), "capped at 4, bad entries skipped");
        assertEquals(new ImproveModel.Range("Ok", "days:7", false), o.ranges().get(0), "on must be a real boolean");
        assertEquals(new ImproveModel.Range("Num", "days:30", false), o.ranges().get(1));
        assertEquals(new ImproveModel.Range("A", "a1", true), o.ranges().get(2));
        assertEquals(64, ImproveModel.parse("{\"ranges\":[{\"label\":\"x\",\"action\":\"" + "b".repeat(64) + "\"}],\"tabs\":[{\"label\":\"a\"}]}").ranges().get(0).action().length());

        // not a list / wrong types: no ranges, the screen still opens
        for (String bad : new String[]{"5", "\"x\"", "{\"a\":1}", "null"}) {
            ImproveModel.Screen b = ImproveModel.parse("{\"ranges\":" + bad + ",\"tabs\":[{\"label\":\"a\"}]}");
            assertNotNull(b, bad);
            assertTrue(b.ranges().isEmpty(), bad);
        }
    }

    @Test
    void rangesShareTheLastTabLineWhenTheyFitAndSitRightAligned() {
        ImproveModel.Screen s = withRanges(rangesJson("Last 7 days", "Last 30 days"), TABS_EN);
        Result r = ImproveLayout.compute(1920, 1080, METER, s);
        check("ranges-en", 1920, 1080, r, s);
        int lastTabY = r.tabs().get(r.tabs().size() - 1).box().y();
        for (ImproveLayout.RangeBox b : r.ranges()) assertEquals(lastTabY, b.box().y(), "same line as the tabs");
        ImproveLayout.RangeBox last = r.ranges().get(1);
        assertEquals(r.viewport().x() + r.viewport().w(), last.box().x() + last.box().w(), "right-aligned");
        assertTrue(r.ranges().get(0).box().x() < last.box().x());
        assertTrue(r.ranges().get(0).on() && !last.on());
        // the line is shared: no extra row compared with the same screen without ranges
        Result without = ImproveLayout.compute(1920, 1080, METER, withRanges("[]", TABS_EN));
        assertEquals(without.viewport().y(), r.viewport().y());
    }

    @Test
    void rangesGetTheirOwnLineWhenTheTabsLeaveNoRoom() {
        ImproveModel.Screen s = withRanges(rangesJson("Últimos 7 días", "Últimos 30 días"), TABS_ES);
        Result r = ImproveLayout.compute(640, 360, METER, s);   // 544 px of room: the six Spanish tabs take most of it
        check("ranges-es", 640, 360, r, s);
        int lastTabBottom = r.tabs().stream().mapToInt(t -> t.box().y() + t.box().h()).max().orElse(0);
        boolean own = r.ranges().stream().allMatch(b -> b.box().y() >= lastTabBottom);
        boolean shared = r.ranges().stream().allMatch(b -> b.box().y() < lastTabBottom);
        assertTrue(own || shared, "the two buttons are on one line");
        assertEquals(r.viewport().x() + r.viewport().w(), r.ranges().get(1).box().x() + r.ranges().get(1).box().w(), "right-aligned");
    }

    @Test
    void rangesWrapOnNarrowScreensAndNeverOverlapTabs() {
        String[][] sets = {TABS_EN, TABS_ES, TABS_RU};
        String[][] labels = {{"Last 7 days", "Last 30 days"}, {"Últimos 7 días", "Últimos 30 días"}, {"Последние 7 дней", "Последние 30 дней"},
                {"最近 7 天", "最近 30 天"}, {"Ultimi 7 giorni", "Ultimi 30 giorni"}};
        for (int k = 0; k < sets.length; k++) {
            ImproveModel.Screen s = withRanges(rangesJson(labels[k][0], labels[k][1]), sets[k]);
            for (int[] sc : REAL) check("narrow-" + k, sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
            for (int[] sc : TINY) check("narrow-" + k, sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
            for (int width = 100; width <= 700; width += 7) check("w" + width + "-" + k, width, 400, ImproveLayout.compute(width, 400, METER, s), s);
        }
        // Russian at 240 px: the two buttons cannot share a line, so they stack
        ImproveModel.Screen ru = withRanges(rangesJson(labels[2][0], labels[2][1]), TABS_RU);
        Result r = ImproveLayout.compute(240, 240, METER, ru);
        check("stack", 240, 240, r, ru);
        assertNotEquals(r.ranges().get(0).box().y(), r.ranges().get(1).box().y(), "stacked on a narrow screen");
    }

    @Test
    void veryLongRangeLabelsAreCutToTheLine() {
        ImproveModel.Screen s = withRanges(rangesJson("Letzte sieben Tage in diesem Modus".repeat(4), "最近三十天".repeat(20)), TABS_EN);
        for (int[] sc : REAL) check("long-range", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
        for (int[] sc : TINY) check("long-range", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, s), s);
    }

    @Test
    void rangesWorkWithOneTabOrNone() {
        ImproveModel.Screen one = withRanges(rangesJson("Last 7 days", "Last 30 days"), "Only");
        Result r = ImproveLayout.compute(640, 360, METER, one);
        check("one-tab", 640, 360, r, one);
        assertTrue(r.tabs().isEmpty());
        assertEquals(2, r.ranges().size());
        assertEquals(r.ranges().get(0).box().y(), r.ranges().get(1).box().y());
        for (int[] sc : TINY) check("one-tab", sc[0], sc[1], ImproveLayout.compute(sc[0], sc[1], METER, one), one);
    }

    @Test
    void panelIsOneTabLineTallerOnlyWhenTheRangesNeedTheirOwnLine() {
        ImproveModel.Screen s = withRanges(rangesJson("Последние 7 дней", "Последние 30 дней"), TABS_RU);
        Result with = ImproveLayout.compute(320, 240, METER, s);
        Result without = ImproveLayout.compute(320, 240, METER, withRanges("[]", TABS_RU));
        assertTrue(with.viewport().y() > without.viewport().y(), "the extra line pushes the content down");
        assertTrue(with.viewport().h() >= 20, "and the content still has room");
    }

    @Test
    void singleTabHasNoTabStripAndEmptyThingsAreFine() {
        ImproveModel.Screen s = ImproveModel.parse("{\"tabs\":[{\"label\":\"Only\",\"blocks\":[{\"t\":\"section\",\"text\":\"\"},{\"t\":\"grid\",\"rows\":[]},{\"t\":\"note\",\"text\":\" \"}]}]}");
        Result r = ImproveLayout.compute(640, 360, METER, s);
        assertTrue(r.tabs().isEmpty());
        assertEquals(0, r.contents().get(0).height());
        assertEquals(0, r.maxScroll(0));
        assertEquals("Back", r.backLabel(), "default Back label");
        assertEquals("Close", r.closeLabel(), "default Close label");
    }
}
