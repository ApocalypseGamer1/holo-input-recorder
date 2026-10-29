package holorecorder.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateManifestTest {
    static final String SHA = "a".repeat(64);

    static String json(String version, String tag, String file, String sha, String size) {
        return "{\"version\":\"" + version + "\",\"builds\":{\"26.3\":{\"tag\":\"" + tag + "\",\"file\":\"" + file + "\",\"sha256\":\"" + sha
                + "\",\"size\":" + size + "},\"26.2\":{\"tag\":\"v1.13.0-mc26.2\",\"file\":\"holo-input-recorder-1.13.0+mc26.2.jar\",\"sha256\":\""
                + SHA + "\",\"size\":2000}}}";
    }

    static String good() {
        return json("1.13.0", "v1.13.0", "holo-input-recorder-1.13.0.jar", SHA, "1000");
    }

    @Test
    void readsBothBuilds() {
        UpdateManifest m = UpdateManifest.parse(good());
        assertEquals("1.13.0", m.version());
        assertEquals("v1.13.0", m.build("26.3").tag());
        assertEquals(1000, m.build("26.3").size());
        assertEquals("holo-input-recorder-1.13.0+mc26.2.jar", m.build("26.2").file());
        assertNull(m.build("26.1"));
    }

    @Test
    void uppercaseHexIsAcceptedAndLowered() {
        UpdateManifest m = UpdateManifest.parse(json("1.13.0", "v1", "a.jar", "AB".repeat(32), "5"));
        assertEquals("ab".repeat(32), m.build("26.3").sha256());
    }

    @Test
    void rejectsBadFields() {
        String[][] bad = {
                {"1.13.0", "v1.13.0", "holo.jar", "g".repeat(64), "10"},
                {"1.13.0", "v1.13.0", "holo.jar", "a".repeat(63), "10"},
                {"1.13.0", "v1/../x", "holo.jar", SHA, "10"},
                {"1.13.0", "v1", "../holo.jar", SHA, "10"},
                {"1.13.0", "v1", "a/b.jar", SHA, "10"},
                {"1.13.0", "v1", "a\\\\b.jar", SHA, "10"},
                {"1.13.0", "v1", "..", SHA, "10"},
                {"1.13.0", "v1", ".hidden.jar", SHA, "10"},
                {"1.13.0", "v1", "holo.exe", SHA, "10"},
                {"1.13.0", "v1", "ho lo.jar", SHA, "10"},
                {"1.13.0", "v1", "holo.jar", SHA, "0"},
                {"1.13.0", "v1", "holo.jar", SHA, "-5"},
                {"1.13.0", "v1", "holo.jar", SHA, "9000000"},
                {"1.13.0", "v1", "holo.jar", SHA, "\"10\""},
                {"latest", "v1", "holo.jar", SHA, "10"},
        };
        for (String[] b : bad) {
            assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(json(b[0], b[1], b[2], b[3], b[4])), String.join("|", b));
        }
    }

    @Test
    void rejectsBrokenOrOversizeDocuments() {
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse("{\"version\":\"1.0.0\"}"));
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(null));
        String big = good().replace("\"builds\"", "\"pad\":\"" + "x".repeat(UpdateManifest.MAX_MANIFEST) + "\",\"builds\"");
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.parse(big));
    }

    @Test
    void aMissingBuildMeansNothingToInstall() {
        UpdateManifest m = UpdateManifest.parse("{\"version\":\"1.13.0\",\"builds\":{}}");
        assertTrue(m.newerFor("26.3", "1.12.0").isEmpty());
    }

    @Test
    void picksTheBuildByRunningMinecraft() {
        UpdateManifest m = UpdateManifest.parse(good());
        assertEquals("v1.13.0", m.newerFor("26.3", "1.12.0").orElseThrow().tag());
        assertEquals("v1.13.0", m.newerFor("26.3.1", "1.12.0").orElseThrow().tag());
        assertEquals("v1.13.0-mc26.2", m.newerFor("26.2", "1.12.0+mc26.2").orElseThrow().tag());
        assertTrue(m.newerFor("26.1", "1.12.0").isEmpty());
        assertTrue(m.newerFor("1.21.5", "1.12.0").isEmpty());
        assertTrue(m.newerFor("26.3-rc-1", "1.12.0").isEmpty());
        assertTrue(m.newerFor("", "1.12.0").isEmpty());
        assertTrue(m.newerFor(null, "1.12.0").isEmpty());
        assertTrue(m.newerFor("26.3", "1.13.0").isEmpty());
        assertTrue(m.newerFor("26.2", "1.13.0+mc26.2").isEmpty());
        assertTrue(m.newerFor("26.3", "garbage").isEmpty());
    }

    @Test
    void buildKeys() {
        assertEquals("26.3", UpdateManifest.buildKey("26.3"));
        assertEquals("26.2", UpdateManifest.buildKey("26.2"));
        assertNull(UpdateManifest.buildKey("26.4"));
        assertNull(UpdateManifest.buildKey("garbage"));
    }

    @Test
    void jarUrlAlwaysStartsWithTheCompiledBase() {
        UpdateManifest m = UpdateManifest.parse(good());
        String url = UpdateManifest.jarUrl(m.build("26.3"));
        assertEquals("https://github.com/ApocalypseGamer1/holo-input-recorder/releases/download/v1.13.0/holo-input-recorder-1.13.0.jar", url);
        assertTrue(UpdateManifest.jarUrl(m.build("26.2")).startsWith(UpdateManifest.BASE));
        assertTrue(UpdateManifest.MANIFEST_URL.startsWith(UpdateManifest.BASE));
    }

    @Test
    void aMaliciousNameCannotReachTheUrlBuilder() {
        for (String evil : new String[] {"../x.jar", "a/b.jar", "https://evil.example/x.jar", "x.jar?a=b", "x.jar#f", "..", "a b.jar", "", "x.jar\n"}) {
            assertThrows(IllegalArgumentException.class, () -> UpdateManifest.jarUrl(new UpdateManifest.Build("v1", evil, SHA, 10)), evil);
            assertThrows(IllegalArgumentException.class, () -> UpdateManifest.jarUrl(new UpdateManifest.Build(evil, "x.jar", SHA, 10)), evil);
        }
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.jarUrl(null));
    }
}
