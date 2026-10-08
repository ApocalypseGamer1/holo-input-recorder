package holorecorder;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallIdentityTest {
    @Test void aFreshIdIs32LowercaseHexAndRandom() {
        String a = InstallIdentity.fresh(), b = InstallIdentity.fresh();
        assertTrue(InstallIdentity.isValid(a));
        assertEquals(InstallIdentity.HEX_LENGTH, a.length());
        assertEquals(a.toLowerCase(), a);
        assertNotEquals(a, b);
    }

    @Test void itIsCreatedOnceThenStable() throws Exception {
        Path file = Files.createTempDirectory("holoid").resolve("sub").resolve("install-id.txt");
        String first = InstallIdentity.loadOrCreate(file);
        assertTrue(InstallIdentity.isValid(first));
        assertTrue(Files.isRegularFile(file));
        assertEquals(first, InstallIdentity.loadOrCreate(file));   // same id on the next launch
        assertEquals(first, Files.readString(file).trim());
    }

    @Test void deletingTheFileResetsTheId() throws Exception {
        Path file = Files.createTempDirectory("holoid").resolve("install-id.txt");
        String first = InstallIdentity.loadOrCreate(file);
        Files.delete(file);
        String second = InstallIdentity.loadOrCreate(file);
        assertTrue(InstallIdentity.isValid(second));
        assertNotEquals(first, second);
    }

    @Test void aGarbageFileIsReplaced() throws Exception {
        Path file = Files.createTempDirectory("holoid").resolve("install-id.txt");
        Files.writeString(file, "not-a-valid-id\n");
        String id = InstallIdentity.loadOrCreate(file);
        assertTrue(InstallIdentity.isValid(id));
        assertEquals(id, Files.readString(file).trim());
    }

    @Test void validatorRejectsWrongLengthAndNonHex() {
        assertFalse(InstallIdentity.isValid(null));
        assertFalse(InstallIdentity.isValid(""));
        assertFalse(InstallIdentity.isValid("abc"));
        assertFalse(InstallIdentity.isValid("g".repeat(32)));
        assertFalse(InstallIdentity.isValid("A".repeat(32)));   // uppercase is not accepted
    }
}
