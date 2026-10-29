package holorecorder.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateVersionTest {
    @Test
    void newerIsNumericNotText() {
        assertTrue(UpdateVersion.isNewer("1.13.0", "1.12.0"));
        assertTrue(UpdateVersion.isNewer("1.12.0", "1.9.0"));
        assertFalse(UpdateVersion.isNewer("1.9.0", "1.12.0"));
        assertTrue(UpdateVersion.isNewer("1.12.1", "1.12"));
        assertFalse(UpdateVersion.isNewer("1.12", "1.12.0"));
    }

    @Test
    void suffixAfterPlusOrDashIsIgnored() {
        assertFalse(UpdateVersion.isNewer("1.13.0+mc26.2", "1.13.0"));
        assertFalse(UpdateVersion.isNewer("1.13.0", "1.13.0+mc26.2"));
        assertEquals(0, UpdateVersion.compare("1.13.0+mc26.2", "1.13.0"));
        assertTrue(UpdateVersion.isNewer("1.13.0", "1.12.0+mc26.2"));
        assertEquals(0, UpdateVersion.compare("1.13.0-beta", "1.13.0"));
    }

    @Test
    void garbageIsNeverNewer() {
        for (String bad : new String[] {null, "", "abc", "1..2", "1.x", "-1", ".1", "99999999999.0"}) {
            assertFalse(UpdateVersion.isNewer(bad, "1.0.0"), String.valueOf(bad));
            assertFalse(UpdateVersion.isNewer("2.0.0", bad), String.valueOf(bad));
            assertNull(UpdateVersion.compare(bad, "1.0.0"));
        }
    }
}
