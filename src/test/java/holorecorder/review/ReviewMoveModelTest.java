package holorecorder.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Java against the Python trainer's own outputs (review/moves/expected.json, written by export.py). */
class ReviewMoveModelTest {
    static JsonObject expected() throws Exception {
        try (InputStream in = ReviewMoveModelTest.class.getResourceAsStream("/review/moves/expected.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static double[] doubles(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    @Test
    void bundledModelLoads() {
        ReviewMoveModel m = ReviewMoveModel.get();
        assertNotNull(m);
        assertEquals(14, m.groups().size());
    }

    @Test
    void netMatchesPythonOnSavedWindows() throws Exception {
        ReviewMoveModel m = ReviewMoveModel.get();
        JsonObject e = expected();
        JsonArray vectors = e.getAsJsonArray("vectors"), embeds = e.getAsJsonArray("embeds"), groups = e.getAsJsonArray("groups");
        for (int i = 0; i < vectors.size(); i++) {
            double[] x = doubles(vectors.get(i)), want = doubles(embeds.get(i)), got = m.embed(x);
            for (int k = 0; k < want.length; k++) assertEquals(want[k], got[k], 1e-3, "window " + i + " dim " + k);
            assertEquals(groups.get(i).getAsInt(), m.group(x), "window " + i);
        }
    }

    @Test
    void roundsGroupLikePython() throws Exception {
        ReviewMoveModel m = ReviewMoveModel.get();
        for (JsonElement el : expected().getAsJsonArray("rounds")) {
            JsonObject r = el.getAsJsonObject();
            ReviewRecord rec;
            try (InputStream in = ReviewMoveModelTest.class.getResourceAsStream("/review/moves/" + r.get("file").getAsString())) {
                rec = ReviewRecord.fromBytes(in.readAllBytes());
            }
            List<ReviewMoveModel.Hit> hits = m.of(rec);
            JsonArray at = r.getAsJsonArray("hits"), g = r.getAsJsonArray("groups");
            assertEquals(at.size(), hits.size(), r.get("file").getAsString());
            for (int i = 0; i < hits.size(); i++) {
                assertEquals(at.get(i).getAsInt(), hits.get(i).hit(), r.get("file").getAsString());
                assertEquals(g.get(i).getAsInt(), hits.get(i).group().id(), r.get("file").getAsString());
            }
        }
    }
}
