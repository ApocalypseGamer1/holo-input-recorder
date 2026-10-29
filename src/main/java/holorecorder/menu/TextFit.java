package holorecorder.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Wraps and shortens text to a pixel width, whatever the language. Pure: the caller gives the width of a string
 * (the game font's, in the screen; a fixed table, in tests). Latin words break at spaces; Chinese, Japanese and Korean
 * text breaks between any two characters; a word wider than the line breaks inside the word. Every returned line is
 * at most {@code width} wide, and a text cut short ends in an ellipsis, so nothing ever overflows its box.
 */
public final class TextFit {
    public static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /** True for characters that may break from their neighbours without a space (CJK scripts, kana, hangul, full-width forms). */
    static boolean breaksAnywhere(int cp) {
        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
        return s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA
                || s == Character.UnicodeScript.HANGUL || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    /**
     * Wraps {@code text} to at most {@code maxLines} lines of at most {@code width} pixels. Blank text gives no lines.
     * When the text does not fit, the last line ends in "…".
     */
    public static List<String> wrap(String text, int width, int maxLines, ToIntFunction<String> measure) {
        List<String> out = new ArrayList<>();
        if (text == null || maxLines <= 0) return out;
        text = text.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) return out;
        width = Math.max(1, width);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            // one unit: a run of non-space non-CJK characters, a single CJK character, or a space
            int start = i;
            int cp = text.codePointAt(i);
            if (cp == ' ') {
                i++;
                if (line.length() > 0 && line.charAt(line.length() - 1) != ' ') line.append(' ');
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
            String joined = line + unit;
            if (measure.applyAsInt(joined) <= width) {
                line.append(unit);
                continue;
            }
            // does not fit: close the line (without its trailing space), then place the unit on a new line
            if (!line.isEmpty()) {
                lines.add(stripEnd(line.toString()));
                line.setLength(0);
            }
            // a unit wider than a whole line breaks inside itself
            int from = 0;
            while (from < unit.length()) {
                int end = from;
                while (end < unit.length()) {
                    int next = end + Character.charCount(unit.codePointAt(end));
                    if (end > from && measure.applyAsInt(unit.substring(from, next)) > width) break;
                    end = next;
                }
                String piece = unit.substring(from, end);
                from = end;
                if (from < unit.length()) lines.add(piece);
                else line.append(piece);
            }
        }
        if (!line.isEmpty()) lines.add(stripEnd(line.toString()));
        if (lines.size() <= maxLines) return lines;
        out.addAll(lines.subList(0, maxLines - 1));
        String last = lines.get(maxLines - 1);
        // the rest of the text is cut: show that on the last line
        out.add(ellipsize(last + " " + String.join(" ", lines.subList(maxLines, lines.size())), width, measure));
        return out;
    }

    /** {@code text} cut to fit {@code width} pixels, with "…" when something was cut. */
    public static String ellipsize(String text, int width, ToIntFunction<String> measure) {
        if (text == null) return "";
        text = text.strip();
        if (measure.applyAsInt(text) <= width) return text;
        int cut = text.length();
        while (cut > 0) {
            cut = text.offsetByCodePoints(cut, -1);
            String head = stripEnd(text.substring(0, cut)) + ELLIPSIS;
            if (measure.applyAsInt(head) <= width) return head;
        }
        return measure.applyAsInt(ELLIPSIS) <= width ? ELLIPSIS : "";
    }

    private static String stripEnd(String s) {
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == ' ') e--;
        return s.substring(0, e);
    }
}
