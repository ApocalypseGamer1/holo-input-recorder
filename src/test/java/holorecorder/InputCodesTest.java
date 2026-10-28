package holorecorder;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GLFW clients (26.2): GLFW numbers on this client must reach the wire as the 26.x (SDL3) numbers the server already stores. */
class InputCodesTest {
    @Test void theGameKeysMapToTheSdlScancodesThe26ClientSends() {
        assertEquals(26, InputCodes.keyToWire(InputConstants.KEY_W));
        assertEquals(4, InputCodes.keyToWire(InputConstants.KEY_A));
        assertEquals(22, InputCodes.keyToWire(InputConstants.KEY_S));
        assertEquals(7, InputCodes.keyToWire(InputConstants.KEY_D));
        assertEquals(44, InputCodes.keyToWire(InputConstants.KEY_SPACE));
        assertEquals(225, InputCodes.keyToWire(InputConstants.KEY_LSHIFT));
        assertEquals(224, InputCodes.keyToWire(InputConstants.KEY_LCONTROL));
        assertEquals(39, InputCodes.keyToWire(InputConstants.KEY_0));
        assertEquals(30, InputCodes.keyToWire(InputConstants.KEY_1));
        assertEquals(38, InputCodes.keyToWire(InputConstants.KEY_9));
        assertEquals(58, InputCodes.keyToWire(InputConstants.KEY_F1));
        assertEquals(69, InputCodes.keyToWire(InputConstants.KEY_F12));
        assertEquals(41, InputCodes.keyToWire(InputConstants.KEY_ESCAPE));
        assertEquals(43, InputCodes.keyToWire(InputConstants.KEY_TAB));
        assertEquals(79, InputCodes.keyToWire(InputConstants.KEY_RIGHT));
        assertEquals(82, InputCodes.keyToWire(InputConstants.KEY_UP));
        assertEquals(98, InputCodes.keyToWire(InputConstants.KEY_NUMPAD0));
        assertEquals(89, InputCodes.keyToWire(InputConstants.KEY_NUMPAD1));
    }

    @Test void everyLetterMapsAndNoTwoKeysShareAScancode() {
        Set<Integer> seen = new HashSet<>();
        for (int glfw = 0; glfw < 349; glfw++) {
            int wire = InputCodes.keyToWire(glfw);
            if (wire != 0) assertTrue(seen.add(wire), "scancode " + wire + " mapped twice (glfw " + glfw + ")");
        }
        for (int k = InputConstants.KEY_A; k <= InputConstants.KEY_Z; k++) assertEquals(k - 65 + 4, InputCodes.keyToWire(k));
        assertEquals(0, InputCodes.keyToWire(-1));
        assertEquals(0, InputCodes.keyToWire(100000));
        assertEquals(0, InputCodes.keyToWire(161));   // GLFW_KEY_WORLD_1: no SDL equivalent
    }

    @Test void mouseButtonsAreRenumberedToSdl() {
        assertEquals(1, InputCodes.buttonToWire(InputCodes.MOUSE_LEFT));
        assertEquals(3, InputCodes.buttonToWire(InputCodes.MOUSE_RIGHT));
        assertEquals(2, InputCodes.buttonToWire(InputCodes.MOUSE_MIDDLE));
        assertEquals(4, InputCodes.buttonToWire(3));
        assertEquals(5, InputCodes.buttonToWire(4));
    }

    @Test void aGlfwRepeatBecomesMinusOneAndPressReleaseAreKept() {
        assertEquals(1, InputCodes.action(1));
        assertEquals(0, InputCodes.action(0));
        assertEquals(-1, InputCodes.action(2));
    }

    @Test void modifierBitsBecomeSdlKeymodBits() {
        assertEquals(0, InputCodes.modsToWire(0));
        assertEquals(0x0001, InputCodes.modsToWire(0x01));
        assertEquals(0x0040, InputCodes.modsToWire(0x02));
        assertEquals(0x0001 | 0x0040 | 0x0100, InputCodes.modsToWire(0x07));
    }

    @Test void theRecordedKeyEventCarriesTheWireNumbersAndTheFilterSeesTheNativeOne() {
        RawInputCapture.clear();
        RawInputCapture.screenProbe(() -> RawInputCapture.SCREEN_NONE);
        RawInputCapture.arm(true, key -> key == InputConstants.KEY_W);   // whitelist is the native GLFW 87
        RawInputCapture.key(InputConstants.KEY_W, 1, 0x01);
        RawInputCapture.key(InputConstants.KEY_A, 1, 0);                  // unbound: refused
        RawInputCapture.button(InputCodes.MOUSE_RIGHT, true, 0);
        RawInputCapture.Events ev = RawInputCapture.drainEvents();
        RawInputCapture.arm(false, null);
        RawInputCapture.screenProbe(null);
        RawInputCapture.clear();
        assertEquals(2, ev.size());
        assertEquals(26f, ev.a()[0]);
        assertEquals(1f, ev.b()[0]);          // shift becomes SDL_KMOD_LSHIFT
        assertEquals(3f, ev.a()[1]);          // right button becomes SDL 3
    }
}
