package holorecorder.update;

import java.nio.file.Path;
import java.util.List;

/**
 * The Windows helper: a fixed PowerShell script that waits for the game to exit, then swaps the jars. The script text never
 * changes and holds no data; the paths, the hash and the version go in as arguments (-File, so they are never parsed as code).
 */
final class SwapScript {
    private SwapScript() {
    }

    static final String TEXT = String.join("\r\n",
            "param([int]$GamePid, [string]$Old, [string]$New, [string]$Target, [string]$Sha, [string]$Dir, [string]$Version)",
            "$ErrorActionPreference = 'Stop'",
            "$result = Join-Path $Dir 'last-result.txt'",
            "$prev = Join-Path $Dir 'previous'",
            "function Finish([string]$text) { try { Set-Content -LiteralPath $result -Value $text -Encoding ASCII } catch {} }",
            "try {",
            "    if ($GamePid -gt 0) { Wait-Process -Id $GamePid -ErrorAction SilentlyContinue }",
            "    $haveNew = Test-Path -LiteralPath $New",
            "    if ($haveNew) {",
            "        $hash = (Get-FileHash -LiteralPath $New -Algorithm SHA256).Hash",
            "        if ($hash -ne $Sha) { Finish 'fail hash'; exit 1 }",
            "    } elseif (-not (Test-Path -LiteralPath $Target)) { Finish 'fail missing'; exit 1 }",
            "    $same = $Old -eq $Target",
            "    $bak = Join-Path $prev ((Split-Path -Leaf $Old) + '.bak')",
            "    $done = $false",
            "    for ($i = 0; $i -lt 40 -and -not $done; $i++) {",
            "        $moved = $false",
            "        try {",
            "            if ((Test-Path -LiteralPath $Old) -and ((-not $same) -or $haveNew)) {",
            "                New-Item -ItemType Directory -Force -Path $prev | Out-Null",
            "                Move-Item -LiteralPath $Old -Destination $bak -Force",
            "                $moved = $true",
            "            }",
            "            if ($haveNew) { Move-Item -LiteralPath $New -Destination $Target -Force }",
            "            $done = $true",
            "        } catch {",
            "            if ($moved -and -not (Test-Path -LiteralPath $Old)) { try { Move-Item -LiteralPath $bak -Destination $Old -Force } catch {} }",
            "            Start-Sleep -Seconds 1",
            "        }",
            "    }",
            "    if ($done) {",
            "        Finish \"ok $Version\"",
            "        Remove-Item -LiteralPath (Join-Path $Dir 'pending.json') -Force -ErrorAction SilentlyContinue",
            "    } else { Finish 'fail locked'; exit 1 }",
            "} catch { Finish 'fail error'; exit 1 } finally { Remove-Item -LiteralPath (Join-Path $Dir 'helper.pid') -Force -ErrorAction SilentlyContinue }",
            "");

    /** The command line that starts the helper. */
    static List<String> command(String powershell, Path script, long gamePid, Path old, Path verified, Path target, String sha256, Path dir, String version) {
        return List.of(powershell, "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-GamePid", Long.toString(gamePid), "-Old", old.toString(), "-New", verified.toString(),
                "-Target", target.toString(), "-Sha", sha256, "-Dir", dir.toString(), "-Version", version);
    }
}
