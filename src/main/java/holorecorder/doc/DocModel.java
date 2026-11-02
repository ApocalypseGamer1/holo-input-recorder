package holorecorder.doc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The document screen's data, read from the server's JSON ({@code holoserver:doc_open}, protocol v1). Pure: no game classes.
 * Reading is forgiving: unknown fields, block types and run styles are skipped or drawn plain, missing text is empty. Only a
 * payload that is not a JSON object, or has no page at all, is refused ({@link #parse} returns null).
 */
public final class DocModel {
    public static final int DEFAULT_ACCENT = 0xFF4FD8FF;
    private static final int MAX_PAGES = 200, MAX_BLOCKS = 300, MAX_RUNS = 60, MAX_TEXT = 4000, MAX_ENTRIES = 12;

    private DocModel() {
    }

    /** A piece of a line; {@code key} marks a command or button name. */
    public record Run(String text, boolean key) {
    }

    /** Text blocks, then the pictures: item icons, the lobby hotbar, a chain of steps, a bar chart, a row of buttons. */
    public enum Kind { PARAGRAPH, BULLET, GAP, ICONS, HOTBAR, STEPS, BARS, BUTTONS, ANIM }

    /**
     * One picture or button: an item {@code icon} id ("" for none), a {@code label}, a {@code tip} (hover or step text; a bar's
     * value text), a bar {@code value}, an ARGB {@code colour} (0: the accent), a button {@code action}, a hotbar {@code slot}, an {@code anim} id ("" for none: see DocAnims).
     */
    public record Entry(String icon, String label, String tip, double value, int colour, String action, int slot, String anim) {
    }

    /** {@code mark} is the bullet's own marker ("1."), or "" for the default dot. {@code entries}: a picture block's parts. */
    public record Block(Kind kind, String mark, List<Run> runs, List<Entry> entries) {
        public Block(Kind kind, String mark, List<Run> runs) {
            this(kind, mark, runs, List.of());
        }
    }

    public record Page(String head, List<Block> blocks) {
    }

    /** {@code back} is null when the server sends none (no Back button). */
    public record Doc(String title, String subtitle, int accent, int page, String back, String close, List<Page> pages) {
    }

    public static Doc parse(String json) {
        try {
            if (json == null) return null;
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            List<Page> pages = new ArrayList<>();
            for (JsonElement p : array(o, "pages")) {
                if (pages.size() >= MAX_PAGES) break;
                if (p.isJsonObject()) pages.add(page(p.getAsJsonObject()));
            }
            if (pages.isEmpty()) return null;
            int page = (int) Math.max(0, Math.min(pages.size() - 1, number(o, "page", 0)));
            String back = str(o, "back");
            return new Doc(str(o, "title"), str(o, "subtitle"), colour(str(o, "accent")), page, back.isBlank() ? null : back,
                    strOr(o, "close", "X"), List.copyOf(pages));
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }

    private static Page page(JsonObject o) {
        List<Block> blocks = new ArrayList<>();
        for (JsonElement b : array(o, "blocks")) {
            if (blocks.size() >= MAX_BLOCKS) break;
            if (!b.isJsonObject()) continue;
            Block block;
            try {
                block = block(b.getAsJsonObject());
            } catch (RuntimeException e) {
                block = null;   // one bad block does not take the others down
            }
            if (block != null) blocks.add(block);
        }
        return new Page(str(o, "head").strip(), List.copyOf(blocks));
    }

    private static Block block(JsonObject o) {
        switch (str(o, "t")) {
            case "gap":
                return new Block(Kind.GAP, "", List.of());
            case "p":
                return new Block(Kind.PARAGRAPH, "", runs(o));
            case "bullet":
                return new Block(Kind.BULLET, str(o, "mark").strip(), runs(o));
            case "icons":
                return visual(Kind.ICONS, o);
            case "hotbar":
                return visual(Kind.HOTBAR, o);
            case "steps":
                return visual(Kind.STEPS, o);
            case "bars":
                return visual(Kind.BARS, o);
            case "buttons":
                return visual(Kind.BUTTONS, o);
            case "anim":
                return visual(Kind.ANIM, o);
            default:
                return null;
        }
    }

    /** A picture block; entries without a label (an anim block's: without an anim) are dropped, and a block left with none too. */
    private static Block visual(Kind kind, JsonObject o) {
        List<Entry> out = new ArrayList<>();
        for (JsonElement e : array(o, "items")) {
            if (out.size() >= MAX_ENTRIES) break;
            if (!e.isJsonObject()) continue;
            JsonObject eo = e.getAsJsonObject();
            String label = clean(str(eo, "label")).strip();
            String anim = str(eo, "anim").strip();
            if (kind == Kind.ANIM ? anim.isEmpty() : label.isEmpty()) continue;
            String action = str(eo, "action").strip();
            if (kind == Kind.BUTTONS && action.isEmpty()) continue;
            int slot = (int) number(eo, "slot", -1);
            if (kind == Kind.HOTBAR && (slot < 0 || slot > 8)) continue;
            String colour = str(eo, "color");
            out.add(new Entry(str(eo, "icon").strip(), label, clean(str(eo, "tip")).strip(), Math.max(0, number(eo, "value", 0)),
                    colour.isBlank() ? 0 : colour(colour), action, slot, anim));
        }
        return out.isEmpty() ? null : new Block(kind, "", List.of(), List.copyOf(out));
    }

    private static List<Run> runs(JsonObject o) {
        List<Run> out = new ArrayList<>();
        for (JsonElement r : array(o, "runs")) {
            if (out.size() >= MAX_RUNS) break;
            if (r.isJsonPrimitive()) {
                out.add(new Run(clean(r.getAsString()), false));
            } else if (r.isJsonObject()) {
                JsonObject ro = r.getAsJsonObject();
                String text = clean(str(ro, "text"));
                if (!text.isEmpty()) out.add(new Run(text, "key".equals(str(ro, "s"))));
            }
        }
        if (out.isEmpty() && o.has("text")) out.add(new Run(clean(str(o, "text")), false));
        return List.copyOf(out);
    }

    /** A line break inside a run is a space; the server sends one block per line. */
    private static String clean(String s) {
        return s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    /** "#rrggbb" as opaque ARGB; anything else is the default accent. */
    static int colour(String hex) {
        String h = hex.strip();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return DEFAULT_ACCENT;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return DEFAULT_ACCENT;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return "";
        String s = e.getAsString();
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    private static String strOr(JsonObject o, String key, String fallback) {
        String s = str(o, key);
        return s.isBlank() ? fallback : s;
    }

    private static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double d = e.getAsDouble();
        return Double.isNaN(d) || Double.isInfinite(d) ? fallback : d;
    }
}
