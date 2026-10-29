package holorecorder.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackInstanceTest {
    @TempDir Path tmp;

    private Path dir(String... parts) throws IOException {
        Path p = tmp;
        for (String s : parts) p = p.resolve(s);
        return Files.createDirectories(p);
    }

    @Test
    void plainFolderIsNotManaged() throws IOException {
        assertEquals(PackInstance.NONE, PackInstance.detect(dir("games", ".minecraft")));
        assertEquals(PackInstance.NONE, PackInstance.detect(null));
        assertEquals(PackInstance.NONE, PackInstance.detect(tmp.resolve("does-not-exist")));
    }

    @Test
    void modrinthIndexInGameDirOrParent() throws IOException {
        Path game = dir("inst");
        Files.writeString(game.resolve("modrinth.index.json"), "{}");
        assertEquals(PackInstance.MODRINTH, PackInstance.detect(game));
        Path inner = dir("inst2", "minecraft");
        Files.writeString(inner.getParent().resolve("modrinth.index.json"), "{}");
        assertEquals(PackInstance.MODRINTH, PackInstance.detect(inner));
    }

    @Test
    void modrinthAppProfilesPath() throws IOException {
        assertEquals(PackInstance.MODRINTH, PackInstance.detect(dir("ModrinthApp", "profiles", "Holoplayer")));
        assertEquals(PackInstance.MODRINTH, PackInstance.detect(dir("com.modrinth.theseus", "profiles", "Holo")));
        assertEquals(PackInstance.MODRINTH, PackInstance.detect(dir("theseus", "profiles", "Holo")));
        assertEquals(PackInstance.NONE, PackInstance.detect(dir("ModrinthApp", "other", "Holo")));
        assertEquals(PackInstance.NONE, PackInstance.detect(dir("profiles", "Holo")));
    }

    @Test
    void prismManagedPackNeedsTheFlagAndTheRightFolderName() throws IOException {
        Path inst = dir("prism", "instances", "Holo");
        Path game = dir("prism", "instances", "Holo", ".minecraft");
        Files.writeString(inst.resolve("instance.cfg"), "name=Holo\nManagedPack=true\nManagedPackType=modrinth\n");
        assertEquals(PackInstance.PRISM, PackInstance.detect(game));
        Path game2 = dir("prism", "instances", "Holo", "minecraft");
        assertEquals(PackInstance.PRISM, PackInstance.detect(game2));
        Path other = dir("prism", "instances", "Holo", "elsewhere");
        assertEquals(PackInstance.NONE, PackInstance.detect(other));

        Path plain = dir("prism2", "instances", "Plain");
        Files.writeString(plain.resolve("instance.cfg"), "name=Plain\nManagedPack=false\n");
        assertEquals(PackInstance.NONE, PackInstance.detect(dir("prism2", "instances", "Plain", ".minecraft")));
    }

    @Test
    void curseForgeFiles() throws IOException {
        Path a = dir("cf", "Instances", "Holo");
        Files.writeString(a.resolve("minecraftinstance.json"), "{}");
        assertEquals(PackInstance.CURSEFORGE, PackInstance.detect(a));

        Path b = dir("cf2", "Holo");
        Files.writeString(b.resolve("manifest.json"), "{\"manifestType\":\"minecraftModpack\",\"name\":\"x\"}");
        assertEquals(PackInstance.CURSEFORGE, PackInstance.detect(b));
        assertEquals(PackInstance.CURSEFORGE, PackInstance.detect(dir("cf2", "Holo", "minecraft")));

        Path c = dir("cf3");
        Files.writeString(c.resolve("manifest.json"), "{\"manifestType\":\"somethingElse\"}");
        assertEquals(PackInstance.NONE, PackInstance.detect(c));
        Files.writeString(c.resolve("manifest.json"), "garbage {");
        assertEquals(PackInstance.NONE, PackInstance.detect(c));
    }

    @Test
    void otherPackManagers() throws IOException {
        Path p = dir("packwiz");
        Files.writeString(p.resolve("pack.toml"), "name = \"x\"");
        assertEquals(PackInstance.OTHER_PACK, PackInstance.detect(p));
    }
}
