package holorecorder.improve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The Improve screen's data, read from the server's JSON (protocol v1). Pure: no game classes. Reading is forgiving:
 * unknown fields and unknown block types are skipped, missing text is empty, numbers are clamped. Only a payload that is
 * not a JSON object, or has no tab at all, is refused ({@link #parse} returns null).
 */
public final class ImproveModel {
    public static final int NEUTRAL = 0xFF8A96A3, NO_EDGE = 0;
    private static final int MAX_TABS = 16, MAX_BLOCKS = 400, MAX_DOTS = 40, MAX_ROWS = 200, MAX_TEXT = 1500, MAX_RANGES = 4, MAX_ACTION = 64;

    private ImproveModel() {
    }

    public sealed interface Block permits Section, Card, Row, Note, Grid {
    }

    public record Section(String text) implements Block {
    }

    /** {@code bar} and {@code marker} are 0..1 or -1 when absent. {@code icon} is an item id or "". */
    public record Card(int color, String icon, String title, String sub, String tip, double bar, double marker, String count,
                       List<Boolean> dots) implements Block {
    }

    public record Row(String label, String sub, double bar, double marker, int color, String value) implements Block {
    }

    public record Note(String text, int color) implements Block {
    }

    /** {@code head} always has 5 entries; each grid row has a label and 4 cells. */
    public record Grid(List<String> head, List<GridRow> rows) implements Block {
    }

    public record GridRow(String label, List<Cell> cells) {
    }

    /** {@code edge} is an ARGB colour, or {@link #NO_EDGE}. */
    public record Cell(String text, String sub, int edge) {
    }

    public record Tab(String label, String footer, List<Block> blocks) {
    }

    /**
     * A time-range toggle button (protocol: "ranges"). {@code action} is what is sent back when it is clicked, at most 64
     * characters; {@code on} marks the range that is shown.
     */
    public record Range(String label, String action, boolean on) {
    }

    /** {@code footer} may be "" ; a tab's own footer replaces it. {@code ranges} is empty when the server sends none. */
    public record Screen(String title, String subtitle, String footer, String back, String close, int tab, List<Tab> tabs,
                         List<Range> ranges) {
        public Screen(String title, String subtitle, String footer, String back, String close, int tab, List<Tab> tabs) {
            this(title, subtitle, footer, back, close, tab, tabs, List.of());
        }

        public String footerOf(int tabIndex) {
            String own = tabs.get(tabIndex).footer();
            return own.isBlank() ? footer : own;
        }
    }

    /** The Improve screen for {@code json}, or null when the payload is unusable. Never throws. */
    public static Screen parse(String json) {
        try {
            if (json == null) return null;
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            List<Tab> tabs = new ArrayList<>();
            for (JsonElement t : array(o, "tabs")) {
                if (tabs.size() >= MAX_TABS) break;
                if (t.isJsonObject()) tabs.add(tab(t.getAsJsonObject()));
            }
            if (tabs.isEmpty()) return null;
            int tab = Math.max(0, Math.min(tabs.size() - 1, (int) number(o, "tab", 0)));
            return new Screen(str(o, "title"), str(o, "subtitle"), str(o, "footer"), strOr(o, "back", "Back"),
                    strOr(o, "close", "Close"), tab, List.copyOf(tabs), ranges(o));
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }

    /** The range buttons: an entry needs a label and an action of at most 64 characters, else it is skipped. */
    private static List<Range> ranges(JsonObject o) {
        List<Range> out = new ArrayList<>();
        for (JsonElement e : array(o, "ranges")) {
            if (out.size() >= MAX_RANGES) break;
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            String label = str(r, "label").strip(), action = str(r, "action");
            JsonElement on = r.get("on");
            if (label.isEmpty() || action.isBlank() || action.length() > MAX_ACTION) continue;
            out.add(new Range(label, action, on != null && on.isJsonPrimitive() && on.getAsJsonPrimitive().isBoolean() && on.getAsBoolean()));
        }
        return List.copyOf(out);
    }

    private static Tab tab(JsonObject o) {
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
        return new Tab(str(o, "label"), str(o, "footer"), List.copyOf(blocks));
    }

    private static Block block(JsonObject o) {
        switch (str(o, "t")) {
            case "section":
                return new Section(str(o, "text"));
            case "card": {
                List<Boolean> dots = new ArrayList<>();
                for (JsonElement d : array(o, "dots")) {
                    if (dots.size() >= MAX_DOTS) break;
                    dots.add(d.isJsonPrimitive() && d.getAsJsonPrimitive().isBoolean() ? d.getAsBoolean()
                            : d.isJsonPrimitive() && d.getAsJsonPrimitive().isNumber() && d.getAsDouble() != 0);
                }
                return new Card(colour(str(o, "color"), NEUTRAL), str(o, "icon"), str(o, "title"), str(o, "sub"), str(o, "tip"),
                        unit(o, "bar"), unit(o, "marker"), str(o, "count"), List.copyOf(dots));
            }
            case "row":
                return new Row(str(o, "label"), str(o, "sub"), unit(o, "bar"), unit(o, "marker"), colour(str(o, "color"), NEUTRAL),
                        str(o, "value"));
            case "note":
                return new Note(str(o, "text"), colour(str(o, "color"), NEUTRAL));
            case "grid": {
                List<String> head = new ArrayList<>();
                for (JsonElement h : array(o, "head")) {
                    if (head.size() >= 5) break;
                    head.add(h.isJsonPrimitive() ? cut(h.getAsString()) : "");
                }
                while (head.size() < 5) head.add("");
                List<GridRow> rows = new ArrayList<>();
                for (JsonElement r : array(o, "rows")) {
                    if (rows.size() >= MAX_ROWS) break;
                    if (!r.isJsonObject()) continue;
                    JsonObject ro = r.getAsJsonObject();
                    List<Cell> cells = new ArrayList<>();
                    for (JsonElement c : array(ro, "cells")) {
                        if (cells.size() >= 4) break;
                        if (!c.isJsonObject()) {
                            cells.add(new Cell("", "", NO_EDGE));
                            continue;
                        }
                        JsonObject co = c.getAsJsonObject();
                        cells.add(new Cell(str(co, "text"), str(co, "sub"), colour(str(co, "edge"), NO_EDGE)));
                    }
                    while (cells.size() < 4) cells.add(new Cell("", "", NO_EDGE));
                    rows.add(new GridRow(str(ro, "label"), List.copyOf(cells)));
                }
                return new Grid(List.copyOf(head), List.copyOf(rows));
            }
            default:
                return null;
        }
    }

    /** The five colour names of the protocol; "none" is no colour at all; anything else is {@code unknown}. */
    static int colour(String name, int missing) {
        if (name.isEmpty()) return missing;
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "bad": return 0xFFFF5C5C;
            case "warn": return 0xFFFFB347;
            case "good": return 0xFF5FD38A;
            case "neutral": return NEUTRAL;
            case "info": return 0xFF4FD8FF;
            case "none": return NO_EDGE;
            default: return missing;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String cut(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? cut(e.getAsString()) : "";
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

    /** A 0..1 value clamped, or -1 when the field is missing or not a number. */
    private static double unit(JsonObject o, String key) {
        double d = number(o, key, Double.NaN);
        return Double.isNaN(d) ? -1 : Math.max(0, Math.min(1, d));
    }
}
