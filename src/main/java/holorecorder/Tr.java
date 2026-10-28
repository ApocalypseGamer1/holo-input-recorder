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
 * Mod text in the player's language. English text is the key; {0}, {1}... are filled in order. Spanish lives in
 * holorecorder/lang/es/*.json. The language is the server's (what the player picked there), else the game's.
 */
public final class Tr {
    static final String[] FILES = {"screen.json", "text.json"};
    private static final Map<String, String> ES = load("es");
    private static volatile String server;
    /** Tests only: forces a language. */
    static volatile String forced;

    private Tr() {
    }

    public static String t(String english, Object... args) {
        String s = spanish() ? ES.getOrDefault(english, english) : english;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    public static boolean spanish() {
        String l = forced != null ? forced : server;
        if (l == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                l = mc == null || mc.options == null ? "en" : mc.options.languageCode;
            } catch (RuntimeException | LinkageError e) {
                l = "en";
            }
        }
        return l != null && l.toLowerCase(java.util.Locale.ROOT).startsWith("es");
    }

    public static void server(String code) {
        server = code == null || code.isEmpty() ? null : code;
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
        return ES;
    }
}
