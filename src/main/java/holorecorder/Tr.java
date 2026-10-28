package holorecorder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Mod text in the player's language. English text is the key; {0}, {1}... are filled in order. Translations live in
 * holorecorder/lang/<code>/*.json. The language is the server's (what the player picked there), else the game's.
 */
public final class Tr {
    static final String[] FILES = {"screen.json", "text.json", "extra.json"};
    /** Language codes with files, as the server sends them; a game locale maps by prefix (pt_br -> pt), zh_hk -> yue. */
    public static final String[] CODES = {"es", "pt", "fr", "de", "it", "ru", "pl", "tr", "zh", "yue", "ja", "ko"};
    private static final Map<String, Map<String, String>> TABLES = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile String server;
    /** Tests only: forces a language. */
    static volatile String forced;

    private Tr() {
    }

    public static String t(String english, Object... args) {
        String code = code();
        String s = code.equals("en") ? english : table(code).getOrDefault(english, english);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    public static boolean spanish() {
        return code().equals("es");
    }

    /** The current language code: "en", or one of {@link #CODES}. */
    public static String code() {
        String l = forced != null ? forced : server;
        if (l == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                l = mc == null || mc.options == null ? "en" : mc.options.languageCode;
            } catch (RuntimeException | LinkageError e) {
                l = "en";
            }
        }
        return resolve(l);
    }

    static String resolve(String locale) {
        if (locale == null) return "en";
        String l = locale.toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("zh_hk") || l.startsWith("yue")) return "yue";
        for (String c : CODES) if (l.startsWith(c)) return c;
        return "en";
    }

    public static void server(String code) {
        server = code == null || code.isEmpty() ? null : code;
    }

    static Map<String, String> table(String code) {
        return TABLES.computeIfAbsent(code, Tr::load);
    }

    static Map<String, String> load(String lang) {
        Map<String, String> m = new HashMap<>();
        for (String f : FILES) {
            try (InputStream in = Tr.class.getResourceAsStream("/holorecorder/lang/" + lang + "/" + f)) {
                if (in == null) continue;
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            } catch (Exception e) {
                // a broken file leaves English, never a crash
            }
        }
        return m;
    }

    static Map<String, String> spanishTable() {
        return table("es");
    }
}
