package holorecorder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrCoverageTest {
    private static final Pattern CALL = Pattern.compile("Tr\\.t\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ARG = Pattern.compile("\\{\\d+}");

    @AfterEach
    void reset() {
        Tr.forced = null;
    }

    @Test
    void everyTextHasSpanishWithTheSamePlaceholders() throws Exception {
        Map<String, String> es = Tr.spanishTable();
        List<String> missing = new ArrayList<>(), badArgs = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = CALL.matcher(Files.readString(p));
                while (m.find()) {
                    String key = unescape(m.group(1));
                    String s = es.get(key);
                    if (s == null) missing.add(p.getFileName() + ": " + key);
                    else if (!args(key).equals(args(s))) badArgs.add(key + " -> " + s);
                }
            }
        }
        assertTrue(missing.isEmpty(), "no Spanish for:\n" + String.join("\n", missing));
        assertTrue(badArgs.isEmpty(), "placeholders differ:\n" + String.join("\n", badArgs));
    }

    @Test
    void fillsInOrderAndFallsBackToEnglish() {
        Tr.forced = "en";
        assertEquals("a 1 b 2", Tr.t("a {0} b {1}", 1, 2));
        Tr.forced = "es_mx";
        assertTrue(Tr.spanish());
        assertEquals("not in the table 3x", Tr.t("not in the table {0}x", 3));
    }

    @Test
    void everyLanguageHasEverySpanishEntry() {
        Map<String, String> es = Tr.spanishTable();
        for (String code : Tr.CODES) {
            Map<String, String> t = Tr.table(code);
            List<String> missing = new ArrayList<>(), badArgs = new ArrayList<>();
            for (var e : es.entrySet()) {
                String s = t.get(e.getKey());
                if (s == null || s.isBlank()) missing.add(e.getKey());
                else if (!args(e.getValue()).equals(args(s))) badArgs.add(e.getKey() + " -> " + s);
            }
            assertTrue(missing.isEmpty(), code + ": " + missing.size() + " missing, e.g. " + missing.stream().limit(5).toList());
            assertTrue(badArgs.isEmpty(), code + ": placeholders differ:\n" + String.join("\n", badArgs));
        }
    }

    @Test
    void gameLocalesMapToTheirLanguage() {
        assertEquals("pt", Tr.resolve("pt_br"));
        assertEquals("yue", Tr.resolve("zh_hk"));
        assertEquals("zh", Tr.resolve("zh_cn"));
        assertEquals("en", Tr.resolve("nl_nl"));
        assertEquals("es", Tr.resolve("es_mx"));
    }

    private static TreeSet<String> args(String s) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = ARG.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            if (n == 'u') {
                b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                b.append(switch (n) { case 'n' -> '\n'; case 't' -> '\t'; default -> n; });
            }
        }
        return b.toString();
    }
}
