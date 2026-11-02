package holorecorder.doc;

import holorecorder.doc.DocLayout.Bar;
import holorecorder.doc.DocLayout.Choice;
import holorecorder.doc.DocLayout.Fill;
import holorecorder.doc.DocLayout.Hot;
import holorecorder.doc.DocLayout.HotKind;
import holorecorder.doc.DocLayout.Icon;
import holorecorder.doc.DocLayout.Prim;
import holorecorder.doc.DocLayout.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The guide's pictures and buttons: read forgivingly, and laid out inside the page at every size. */
class DocVisualsTest {

    static final String PAGES = """
            {"title": "Guide", "pages": [
              {"head": "Steps", "blocks": [
                {"t": "p", "runs": ["Text first."]},
                {"t": "gap"},
                {"t": "steps", "items": [
                  {"icon": "minecraft:compass", "label": "Kalibrierung", "tip": "Wir finden dein Niveau mit Bots bekannter Stärke."},
                  {"icon": "minecraft:writable_book", "label": "Aufnahme", "tip": "10 aufgenommene Runden."},
                  {"icon": "minecraft:enchanting_table", "label": "Training", "tip": "Deine Kopie entsteht."},
                  {"icon": "minecraft:target", "label": "Herausforderungsbot", "tip": "Ein Bot lernt, deine Kopie zu schlagen."},
                  {"icon": "minecraft:diamond_sword", "label": "Übung", "tip": "Spiel!"},
                  {"label": ""}, "junk"
                ]}
              ]},
              {"head": "Bar", "blocks": [
                {"t": "hotbar", "items": [
                  {"icon": "minecraft:diamond_sword", "label": "Play", "tip": "Right-click to pick a duel", "slot": 0},
                  {"icon": "minecraft:compass", "label": "Region", "tip": "Another server", "slot": 8},
                  {"icon": "minecraft:book", "label": "Bad slot", "slot": 12}
                ]},
                {"t": "icons", "items": [
                  {"icon": "minecraft:diamond_sword", "label": "Sword", "tip": "Diamond sword"},
                  {"icon": "minecraft:end_crystal", "label": "Crystal", "tip": "Crystals"},
                  {"icon": "minecraft:mace", "label": "Mace"}, {"icon": "minecraft:diamond_axe", "label": "Axe"},
                  {"icon": "minecraft:netherite_spear", "label": "Spearmace"}, {"icon": "minecraft:golden_apple", "label": "UHC"}
                ]},
                {"t": "bars", "items": [
                  {"label": "Win", "value": 100, "tip": "100 XP", "color": "#5fd38a"},
                  {"label": "Draw", "value": 60, "tip": "60 XP"}, {"label": "Loss", "value": 40, "tip": "40 XP", "color": "nope"}
                ]},
                {"t": "buttons", "items": [
                  {"label": "Open the Play menu", "action": "open:play"}, {"label": "No action"},
                  {"label": "Einstellungen öffnen", "action": "open:settings"}, {"label": "Back to page 1", "action": "page:0"}
                ]},
                {"t": "buttons", "items": [{"label": "only broken"}]}
              ]}
            ]}""";

    @Test
    void picturesAreReadAndBadPartsDropped() {
        DocModel.Doc doc = DocModel.parse(PAGES);
        assertNotNull(doc);
        List<DocModel.Block> steps = doc.pages().get(0).blocks();
        assertEquals(DocModel.Kind.STEPS, steps.get(2).kind());
        assertEquals(5, steps.get(2).entries().size());
        List<DocModel.Block> bar = doc.pages().get(1).blocks();
        assertEquals(4, bar.size(), "the button block with no action is dropped");
        assertEquals(List.of(0, 8), bar.get(0).entries().stream().map(DocModel.Entry::slot).toList());
        assertEquals(0xFF5FD38A, bar.get(2).entries().get(0).colour());
        assertEquals(0, bar.get(2).entries().get(1).colour());
        assertEquals(List.of("open:play", "open:settings", "page:0"), bar.get(3).entries().stream().map(DocModel.Entry::action).toList());
    }

    @Test
    void oldTextOnlyDocumentsStillHaveNoPictures() {
        DocModel.Doc doc = DocModel.parse("{\"pages\":[{\"head\":\"A\",\"blocks\":[{\"t\":\"p\",\"runs\":[\"x\"]}]}]}");
        assertTrue(doc.pages().get(0).blocks().get(0).entries().isEmpty());
    }

    private static void collect(List<Prim> in, List<Prim> out) {
        for (Prim p : in) {
            out.add(p);
            if (p instanceof Choice c) collect(c.prims(), out);
        }
    }

    @Test
    void everythingFitsTheViewAtEverySize() {
        DocModel.Doc doc = DocModel.parse(PAGES);
        List<int[]> sizes = new ArrayList<>(List.of(DocLayoutTest.REAL));
        sizes.addAll(List.of(DocLayoutTest.TINY));
        for (int[] s : sizes) {
            DocLayout.Result r = DocLayout.compute(s[0], s[1], DocLayoutTest.METER, doc);
            for (int page = 0; page < doc.pages().size(); page++) {
                DocLayout.Content c = r.contents().get(page);
                List<Prim> all = new ArrayList<>();
                collect(c.prims(), all);
                String where = s[0] + "x" + s[1] + " page " + page;
                for (Prim p : all) {
                    int x, w, y, h;
                    if (p instanceof Fill f) { x = f.x(); w = f.w(); y = f.y(); h = f.h(); }
                    else if (p instanceof Text t) { x = t.x(); w = DocLayoutTest.width(t.text()); y = t.y(); h = DocLayoutTest.LH; }
                    else if (p instanceof Icon i) { x = i.x(); w = 16; y = i.y(); h = 16; }
                    else if (p instanceof Bar b) { x = b.x(); w = b.w(); y = b.y(); h = b.h(); assertTrue(b.frac() >= 0 && b.frac() <= 1); }
                    else if (p instanceof Hot hot) { x = hot.x(); w = hot.w(); y = hot.y(); h = hot.h(); }
                    else continue;
                    if (r.contentWidth() >= 60) {
                        assertTrue(x >= 0 && x + w <= r.contentWidth(), where + " " + p + " width " + r.contentWidth());
                    }
                    assertTrue(y >= 0 && y + h <= c.height(), where + " " + p + " height " + c.height());
                }
            }
        }
    }

    @Test
    void stepsPickTheirTextAndButtonsCarryTheirAction() {
        DocModel.Doc doc = DocModel.parse(PAGES);
        DocLayout.Result r = DocLayout.compute(960, 540, DocLayoutTest.METER, doc);
        List<Prim> steps = r.contents().get(0).prims();
        List<Hot> picks = steps.stream().filter(p -> p instanceof Hot h && h.kind() == HotKind.PICK).map(p -> (Hot) p).toList();
        assertEquals(5, picks.size());
        assertTrue(picks.stream().allMatch(h -> h.group() == 0));
        long texts = steps.stream().filter(p -> p instanceof Choice c && c.prims().stream().anyMatch(q -> q instanceof Text)).count();
        assertEquals(5, texts, "one shown text per step");
        for (int i = 0; i < picks.size(); i++) for (int k = i + 1; k < picks.size(); k++) {
            Hot a = picks.get(i), b = picks.get(k);
            assertFalse(a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h(), "steps overlap");
        }
        List<Prim> second = r.contents().get(1).prims();
        List<String> actions = second.stream().filter(p -> p instanceof Hot h && h.kind() == HotKind.BUTTON).map(p -> ((Hot) p).action()).toList();
        assertEquals(List.of("open:play", "open:settings", "page:0"), actions);
        assertEquals(2, second.stream().filter(p -> p instanceof Hot h && h.kind() == HotKind.PICK).count(), "hotbar slots");
        assertEquals(3, second.stream().filter(p -> p instanceof Bar).count());
    }
}
