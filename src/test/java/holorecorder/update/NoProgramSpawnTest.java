package holorecorder.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mod never starts another program: no helper process, no script, no shell. */
class NoProgramSpawnTest {
    @Test
    void mainSourceStartsNothing() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(p).toLowerCase(Locale.ROOT);
                for (String bad : new String[] {"processbuilder", "runtime.getruntime", "powershell", "cmd.exe", "desktop.getdesktop"}) {
                    if (text.contains(bad)) found.add(p.getFileName() + ": " + bad);
                }
            }
        }
        assertTrue(found.isEmpty(), "starts or names a program: " + found);
    }
}
