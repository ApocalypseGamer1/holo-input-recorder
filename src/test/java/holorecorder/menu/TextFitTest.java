package holorecorder.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Text is wrapped or cut to its box in every script; no line is ever wider than the box. */
class TextFitTest {

    /** Worst-case pixel widths: wide Latin 7, other 6, space 4, CJK 9. */
    static final ToIntFunction<String> WIDTH = s -> {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += cp == ' ' ? 4 : TextFit.breaksAnywhere(cp) ? 9 : "WMmwÑÜÖ".indexOf(cp) >= 0 || Character.isUpperCase(cp) ? 7 : 6;
        }
        return w;
    };

    static final String[] SAMPLES = {
            "Diamond sword and armour; first to die loses",
            "Speer-und-Streitkolben-Kit-Layout Herausforderungsbot Donaudampfschifffahrtsgesellschaftskapitän",
            "Раскладка кита: Копьё и булава — Выбери соперника для следующего раунда",
            "钻石剑和盔甲；先死的人输掉比赛，选择你的对手，然后点击开始",
            "ダイヤの剣とよろい。先に倒れた方が負けです。相手を選んでください",
            "다이아몬드 검과 갑옷. 먼저 죽는 사람이 집니다. 상대를 고르세요",
            "Zażółć gęślą jaźń — Układ zestawów Rywal i trening",
            "A", "", "   ", "😀😀😀😀😀😀😀😀😀😀"};

    @Test
    void everyLineFitsAtEveryWidthAndLineCount() {
        for (String s : SAMPLES) {
            for (int width : new int[]{9, 20, 40, 60, 92, 147, 183, 300}) {
                for (int max = 1; max <= 3; max++) {
                    List<String> lines = TextFit.wrap(s, width, max, WIDTH);
                    assertTrue(lines.size() <= max, s + " @" + width + ": " + lines.size() + " lines");
                    for (String line : lines) {
                        assertTrue(WIDTH.applyAsInt(line) <= Math.max(width, 9), "'" + line + "' is " + WIDTH.applyAsInt(line) + " wide in " + width);
                        assertFalse(line.isBlank());
                    }
                    if (s.isBlank()) assertTrue(lines.isEmpty());
                }
            }
        }
    }

    @Test
    void textThatFitsIsKeptWholeAndCutTextEndsInAnEllipsis() {
        assertEquals(List.of("Click to play"), TextFit.wrap("Click to play", 200, 2, WIDTH));
        assertEquals(List.of("Click to", "play"), TextFit.wrap("Click to play", 60, 2, WIDTH));
        List<String> cut = TextFit.wrap(SAMPLES[0], 80, 2, WIDTH);
        assertEquals(2, cut.size());
        assertTrue(cut.get(1).endsWith(TextFit.ELLIPSIS));
        assertFalse(cut.get(0).endsWith(TextFit.ELLIPSIS));
    }

    @Test
    void cjkBreaksBetweenCharactersWithoutSpaces() {
        List<String> lines = TextFit.wrap("选择你的对手然后点击开始", 45, 5, WIDTH);
        assertEquals(List.of("选择你的对", "手然后点击", "开始"), lines);
    }

    @Test
    void aWordWiderThanTheBoxBreaksInsideTheWord() {
        List<String> lines = TextFit.wrap("Donaudampfschifffahrt", 60, 5, WIDTH);
        assertTrue(lines.size() >= 2);
        assertEquals("Donaudampfschifffahrt", String.join("", lines));
    }

    @Test
    void ellipsizeKeepsTheStartAndNeverExceedsTheWidth() {
        for (String s : SAMPLES) {
            for (int width : new int[]{6, 30, 100, 400}) {
                String cut = TextFit.ellipsize(s, width, WIDTH);
                assertTrue(WIDTH.applyAsInt(cut) <= width || cut.isEmpty(), cut);
                if (WIDTH.applyAsInt(s.strip()) <= width) assertEquals(s.strip(), cut);
                else if (!cut.isEmpty()) assertTrue(cut.endsWith(TextFit.ELLIPSIS));
            }
        }
        assertEquals("", TextFit.ellipsize(null, 50, WIDTH));
    }
}
