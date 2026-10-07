package holorecorder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModReportPlanTest {
    @Test void sizesMatchTheVarIntAndUtfEncoding() {
        assertEquals(1, ModReportPlan.varInt(0));
        assertEquals(1, ModReportPlan.varInt(127));
        assertEquals(2, ModReportPlan.varInt(128));
        assertEquals(3, ModReportPlan.varInt(16_384));
        assertEquals(5, ModReportPlan.varInt(-1));
        assertEquals(1, ModReportPlan.varLong(0));
        assertEquals(10, ModReportPlan.varLong(-1L));
        assertEquals(1, ModReportPlan.utf(""));
        assertEquals(1, ModReportPlan.utf(null));
        assertEquals(1 + 3, ModReportPlan.utf("abc"));
        assertEquals(1 + 3, ModReportPlan.utf("€"));            // one char, three UTF-8 bytes
        assertEquals(2 + 128, ModReportPlan.utf("a".repeat(128)));
    }

    @Test void partsKeepOrderAndStayUnderTheBudget() {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 1000; i++) sizes.add(50 + (i * 37) % 300);
        List<List<Integer>> parts = ModReportPlan.split(sizes, Integer::intValue, 5_000, 400, 64);
        List<Integer> joined = new ArrayList<>();
        for (List<Integer> p : parts) {
            assertTrue(p.stream().mapToInt(Integer::intValue).sum() <= 5_000);
            joined.addAll(p);
        }
        assertEquals(sizes, joined);
    }

    @Test void partsHoldAtMostMaxItems() {
        List<Integer> tiny = new ArrayList<>();
        for (int i = 0; i < 1000; i++) tiny.add(1);
        List<List<Integer>> parts = ModReportPlan.split(tiny, Integer::intValue, 30_000, 400, 16);
        assertEquals(List.of(400, 400, 200), parts.stream().map(List::size).toList());
    }

    @Test void beyondMaxPartsTheTailIsDropped() {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 100; i++) sizes.add(100);
        List<List<Integer>> parts = ModReportPlan.split(sizes, Integer::intValue, 1_000, 400, 3);
        assertEquals(3, parts.size());
        assertEquals(30, parts.stream().mapToInt(List::size).sum());
        assertEquals(0, parts.get(0).get(0) - 100);
    }

    @Test void anEmptyListIsOneEmptyPart() {
        assertEquals(List.of(List.of()), ModReportPlan.split(List.<Integer>of(), Integer::intValue, 100, 10, 4));
    }
}
