package holorecorder.update;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the address policy; nothing here opens a connection. */
class UpdateHttpTest {
    @Test
    void officialHostsOverHttpsAreAllowed() {
        assertTrue(UpdateHttp.allowed(URI.create(UpdateManifest.MANIFEST_URL)));
        assertTrue(UpdateHttp.allowed(URI.create("https://objects.githubusercontent.com/github-production-release-asset/1")));
        assertTrue(UpdateHttp.allowed(URI.create("https://release-assets.githubusercontent.com/x?y=1")));
        assertTrue(UpdateHttp.allowed(URI.create("HTTPS://GitHub.com:443/a")));
    }

    @Test
    void everythingElseIsRefused() {
        for (String bad : new String[] {"http://github.com/a", "https://evil.example/a", "https://github.com.evil.example/a",
                "https://evilgithub.com/a", "https://user@github.com/a", "https://github.com:8443/a", "ftp://github.com/a",
                "https://raw.githubusercontent.com/a", "file:///c:/x", "//github.com/a", "https://githubusercontent.com/a"}) {
            assertFalse(UpdateHttp.allowed(URI.create(bad)), bad);
        }
        assertFalse(UpdateHttp.allowed(null));
    }

    @Test
    void aRelativeRedirectStaysOnTheSameHost() {
        URI base = URI.create(UpdateManifest.MANIFEST_URL);
        assertTrue(UpdateHttp.allowed(base.resolve("/other/path")));
        assertFalse(UpdateHttp.allowed(base.resolve("https://evil.example/x")));
    }
}
