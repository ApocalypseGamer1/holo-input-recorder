package holorecorder.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The helper script is fixed text; these checks read it and the command line, and never run it. */
class SwapScriptTest {
    @Test
    void waitsForTheGameBeforeTouchingAnyFile() {
        String t = SwapScript.TEXT;
        int wait = t.indexOf("Wait-Process -Id $GamePid");
        assertTrue(wait > 0);
        assertTrue(wait < t.indexOf("Get-FileHash"));
        assertTrue(wait < t.indexOf("Move-Item"));
    }

    @Test
    void checksTheHashAgainAndKeepsAnUndoCopy() {
        String t = SwapScript.TEXT;
        assertTrue(t.contains("Get-FileHash -LiteralPath $New -Algorithm SHA256"));
        assertTrue(t.contains("'.bak'"));
        assertTrue(t.contains("last-result.txt"));
        assertTrue(t.indexOf("Move-Item -LiteralPath $Old -Destination $bak") < t.indexOf("Move-Item -LiteralPath $New -Destination $Target"));
    }

    @Test
    void holdsNoDataAndNoNetworkCalls() {
        String t = SwapScript.TEXT.toLowerCase();
        for (String forbidden : new String[] {"http", "invoke-webrequest", "downloadfile", "invoke-expression", "iex ", "start-process", ".jar", "holo-input", "github"}) {
            assertFalse(t.contains(forbidden), forbidden);
        }
        assertTrue(SwapScript.TEXT.chars().allMatch(c -> c < 128));
    }

    @Test
    void everyValueGoesInAsAnArgument() {
        List<String> cmd = SwapScript.command("powershell.exe", Path.of("d", "swap.ps1"), 4242, Path.of("m", "old.jar"), Path.of("d", "n.jar.verified"),
                Path.of("m", "n.jar"), "ab".repeat(32), Path.of("d"), "1.13.0");
        assertEquals("powershell.exe", cmd.get(0));
        assertTrue(cmd.contains("-File"));
        assertTrue(cmd.contains("Hidden"));
        assertEquals("4242", cmd.get(cmd.indexOf("-GamePid") + 1));
        assertEquals("1.13.0", cmd.get(cmd.indexOf("-Version") + 1));
        assertEquals("ab".repeat(32), cmd.get(cmd.indexOf("-Sha") + 1));
        assertTrue(cmd.indexOf("-File") < cmd.indexOf("-GamePid"));
        for (String param : new String[] {"GamePid", "Old", "New", "Target", "Sha", "Dir", "Version"}) {
            assertTrue(SwapScript.TEXT.contains("$" + param), param);
        }
    }
}
