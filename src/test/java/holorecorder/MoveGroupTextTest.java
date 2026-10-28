package holorecorder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import holorecorder.review.ReviewMoveModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The learned group names reach the player through Tr.t(variable), which TrCoverageTest cannot see. */
class MoveGroupTextTest {
    @Test
    void everyGroupTextHasSpanish() {
        ReviewMoveModel model = ReviewMoveModel.get();
        assertTrue(model != null, "move model missing");
        Map<String, String> es = Tr.spanishTable();
        List<String> missing = new ArrayList<>();
        for (ReviewMoveModel.Group g : model.groups()) {
            for (String s : new String[] {g.name(), g.sprintName(), g.description()}) if (!es.containsKey(s)) missing.add(s);
        }
        assertTrue(missing.isEmpty(), "no Spanish for:\n" + String.join("\n", missing));
    }
}
