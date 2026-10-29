package holorecorder.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The menu layout at every GUI scale (screen sizes from a phone-sized window to 4K at scale 1) and in long languages. */
class MenuLayoutTest {
    static final int LH = 9;
    /** (width, height) in scaled GUI pixels: 1080p at scales 1-4, 4K at 8, small windows at 4. */
    static final int[][] SCREENS = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {427, 240}, {320, 180}, {213, 120}, {240, 135}};

    /** Name and first lore line of the 17 tiles of a Play-like menu, in several languages. */
    static final Map<String, String[][]> LANGS = Map.of(
            "en", new String[][]{{"Sword", "Diamond sword and armour; first to die loses"}, {"Choose your opponent", "Automatic, your holoplayer, your challenge bot,"}},
            "de", new String[][]{{"Speer und Streitkolben", "Speer, Streitkolben, Windkugeln und Perlen; nicht aufgezeichnet"}, {"Gegner und Kit-Layouts", "Automatisch, dein Holoplayer, dein Herausforderungsbot"}},
            "ru", new String[][]{{"Копьё и булава", "Копьё, булава, ветряные заряды и жемчуг; не записывается"}, {"Выбери соперника", "Автоматически, твой голоплеер, твой бот-вызов или общий бот"}},
            "zh", new String[][]{{"长矛与重锤", "长矛、重锤、风弹和末影珍珠；不会被录制，也不会用于训练"}, {"选择你的对手", "自动、你的 holoplayer、你的挑战机器人、共享机器人"}},
            "ja", new String[][]{{"槍とメイス", "槍、メイス、ウィンドチャージ、パール。記録されません"}, {"相手を選ぶ", "自動、あなたのholoplayer、チャレンジボット"}},
            "ko", new String[][]{{"창과 철퇴", "창, 철퇴, 바람 충전 구체와 진주. 기록되지 않습니다"}, {"상대 고르기", "자동, 내 holoplayer, 내 도전 봇, 공유 봇"}});

    /** The Play menu's shape: rows of 2, 6, 5 and 3 tiles. */
    static final int[] ROW_SIZES = {2, 6, 5, 3};

    static MenuLayout.Meter meter(String[][] text, Map<Integer, Integer> nameOverride) {
        return new MenuLayout.Meter() {
            @Override public int lineHeight() { return LH; }
            @Override public int nameLines(int slot, int w) {
                return Math.max(1, TextFit.wrap(text[slot % 2][0], w, 2, TextFitTest.WIDTH).size());
            }
            @Override public int subLines(int slot, int w) {
                return TextFit.wrap(text[slot % 2][1], w, 2, TextFitTest.WIDTH).size();
            }
        };
    }

    static List<MenuLayout.Item> playItems() {
        List<MenuLayout.Item> items = new ArrayList<>();
        int slot = 0;
        for (int row = 0; row < ROW_SIZES.length; row++) {
            for (int i = 0; i < ROW_SIZES[row]; i++) items.add(new MenuLayout.Item(row * 9 + i, row));
        }
        return items;
    }

    private static boolean inside(MenuLayout.Rect inner, MenuLayout.Rect outer) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.x() + inner.w() <= outer.x() + outer.w()
                && inner.y() + inner.h() <= outer.y() + outer.h();
    }

    private static boolean overlap(MenuLayout.Rect a, MenuLayout.Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    private static void check(String where, int sw, int sh, MenuLayout.Result r, MenuLayout.Meter m, int expectCards) {
        MenuLayout.Rect screen = new MenuLayout.Rect(0, 0, sw, sh);
        assertTrue(inside(r.panel(), screen), where + ": panel " + r.panel());
        assertTrue(inside(r.viewport(), r.panel()), where + ": viewport");
        assertTrue(inside(r.footer(), r.panel()), where + ": footer");
        assertTrue(inside(r.close(), r.footer()), where + ": close button " + r.close());
        assertTrue(r.back() == null || inside(r.back(), r.footer()), where + ": back button");
        if (r.back() != null) assertFalse(overlap(r.back(), r.close()), where + ": Back overlaps Close");
        assertFalse(overlap(r.viewport(), r.footer()), where + ": scroll area runs into the footer");
        assertFalse(overlap(r.titleBar(), r.viewport()), where + ": scroll area runs into the title bar");
        assertEquals(expectCards, r.cards().size(), where);
        MenuLayout.Rect content = new MenuLayout.Rect(0, 0, r.contentWidth(), r.contentHeight());
        for (MenuLayout.Card c : r.cards()) {
            assertTrue(inside(c.box(), content), where + ": card " + c.slot() + " " + c.box() + " outside " + content);
            assertTrue(c.textW() >= 8, where + ": no room for text");
            if (sw >= 213) assertTrue(c.textW() >= 60, where + ": text only " + c.textW() + " wide");
            int textH = c.nameLines() * LH + c.subLines() * LH + (c.subLines() > 0 ? 2 : 0);
            assertTrue(textH + 2 * MenuLayout.CARD_PAD <= c.box().h(), where + ": text taller than card " + c.slot());
            assertTrue(c.textX() + c.textW() <= c.box().w(), where + ": text wider than card");
            for (MenuLayout.Card o : r.cards()) if (o != c) assertFalse(overlap(c.box(), o.box()), where + ": cards overlap");
        }
        // everything can be scrolled into view
        assertEquals(Math.max(0, r.contentHeight() - r.viewport().h()), r.maxScroll(), where);
        for (MenuLayout.Heading h : r.headings()) assertTrue(inside(h.box(), content), where + ": heading");
    }

    @Test
    void playMenuFitsEveryScreenInEveryLanguage() {
        for (var lang : LANGS.entrySet()) {
            for (int[] s : SCREENS) {
                MenuLayout.Meter m = meter(lang.getValue(), Map.of());
                MenuLayout.Result r = MenuLayout.compute(s[0], s[1], m, playItems(), Set.of(0, 1, 2, 3), 150, 40);
                check(lang.getKey() + " " + s[0] + "x" + s[1], s[0], s[1], r, m, 16);
            }
        }
    }

    @Test
    void theNewShortMenusFitEveryScreenInEveryLanguage() {
        // Play (6 modes + 2 others), a mode screen (3 tiles), the main menu (5 tiles) and the pause row.
        int[][] shapes = {{6, 2}, {3}, {5}, {1}};
        for (var lang : LANGS.entrySet()) {
            for (int[] s : SCREENS) {
                for (int[] shape : shapes) {
                    List<MenuLayout.Item> items = new ArrayList<>();
                    int n = 0;
                    for (int row = 0; row < shape.length; row++) {
                        for (int i = 0; i < shape[row]; i++, n++) items.add(new MenuLayout.Item(row * 9 + i, row));
                    }
                    MenuLayout.Meter m = meter(lang.getValue(), Map.of());
                    MenuLayout.Result r = MenuLayout.compute(s[0], s[1], m, items, Set.of(0, 1), 150, 40);
                    check(lang.getKey() + " " + s[0] + "x" + s[1] + " " + n + " tiles", s[0], s[1], r, m, n);
                }
            }
        }
    }

    @Test
    void aSingleTileGetsAWiderCardThanARowOfSix() {
        MenuLayout.Meter m = meter(LANGS.get("en"), Map.of());
        MenuLayout.Result one = MenuLayout.compute(1920, 1080, m, List.of(new MenuLayout.Item(13, 1)), Set.of(), -1, 40);
        MenuLayout.Result six = MenuLayout.compute(1920, 1080, m, playItems(), Set.of(), -1, 40);
        assertTrue(one.cards().get(0).box().w() > six.cards().get(2).box().w());
        assertTrue(one.cards().get(0).box().w() <= MenuLayout.WIDE_CARD);
    }

    @Test
    void widerScreensKeepRowsTogetherNarrowOnesWrapThem() {
        MenuLayout.Meter m = meter(LANGS.get("en"), Map.of());
        MenuLayout.Result wide = MenuLayout.compute(1920, 1080, m, playItems(), Set.of(), -1, 40);
        MenuLayout.Result narrow = MenuLayout.compute(213, 120, m, playItems(), Set.of(), -1, 40);
        long wideColumns = wide.cards().stream().filter(c -> c.box().y() == wide.cards().get(2).box().y()).count();
        assertTrue(wideColumns >= 3, "a 560-wide panel puts several cards side by side");
        assertEquals(1, narrow.cards().stream().filter(c -> c.box().y() == narrow.cards().get(0).box().y()).count(), "a tiny screen uses one column");
        assertTrue(narrow.maxScroll() > 0, "a tiny screen scrolls");
        assertEquals(0, wide.maxScroll(), "1080p at scale 1 shows everything");
    }

    @Test
    void rowsStayInOrderTopToBottomAndLeftToRight() {
        MenuLayout.Meter m = meter(LANGS.get("de"), Map.of());
        MenuLayout.Result r = MenuLayout.compute(640, 360, m, playItems(), Set.of(0, 1), -1, 40);
        int lastRow = -1, lastBottom = -1;
        for (MenuLayout.Card c : r.cards()) {
            int row = c.slot() / 9;
            if (row != lastRow) {
                assertTrue(c.box().y() >= lastBottom, "row " + row + " starts below the previous one");
                lastRow = row;
            }
            lastBottom = Math.max(lastBottom, c.box().y() + c.box().h());
        }
        assertEquals(2, r.headings().size());
    }

    @Test
    void emptyMenuAndSingleTileStillGetAPanelAndClose() {
        MenuLayout.Meter m = meter(LANGS.get("en"), Map.of());
        MenuLayout.Result none = MenuLayout.compute(480, 270, m, List.of(), Set.of(), -1, 40);
        assertEquals(0, none.cards().size());
        assertEquals(0, none.maxScroll());
        assertTrue(inside(none.close(), none.panel()));
        MenuLayout.Result one = MenuLayout.compute(480, 270, m, List.of(new MenuLayout.Item(13, 1)), Set.of(), 120, 40);
        check("one", 480, 270, one, m, 1);
        assertNotNull(one.back());
    }

    @Test
    void headingOnlyAppearsOverARowThatHasTiles() {
        MenuLayout.Meter m = meter(LANGS.get("en"), Map.of());
        MenuLayout.Result r = MenuLayout.compute(480, 270, m, List.of(new MenuLayout.Item(9, 1)), Set.of(0, 1, 2), -1, 40);
        assertEquals(1, r.headings().size());
        assertEquals(1, r.headings().get(0).row());
    }
}
