package holorecorder;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * GLFW (this Minecraft version) to the wire numbering. The wire is the 26.x one: SDL3 scancodes for keys,
 * SDL buttons (1 left, 2 middle, 3 right), SDL_Keymod bits, and action -1 for a key repeat. GLFW key tokens
 * name the physical key by its US-layout position, the same thing an SDL scancode names, so the table is exact
 * for every key; a key with no SDL equivalent goes out as 0 (SDL_SCANCODE_UNKNOWN) so its timing is kept.
 *
 * <p>The privacy whitelist always works on the native number, before this translation.
 */
public final class InputCodes {
    private InputCodes() {}

    /** Native GLFW mouse buttons (what {@code MouseHandler.onPress} passes). */
    public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;     // 0
    public static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;   // 1
    public static final int MOUSE_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE; // 2

    private static final int GLFW_REPEAT = 2;

    /** A key can be bound to the whitelist when it is a keyboard key (26.x: {@code Type.KEYBOARD}). */
    public static boolean isBindableKey(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0;
    }

    /** GLFW action to wire action: press 1, release 0, repeat -1 (GLFW sends 2). */
    public static int action(int glfwAction) {
        return glfwAction == GLFW_REPEAT ? -1 : glfwAction;
    }

    public static int buttonToWire(int glfwButton) {
        return switch (glfwButton) {
            case 0 -> 1;   // left
            case 1 -> 3;   // right
            case 2 -> 2;   // middle
            default -> glfwButton + 1;   // 3 and 4 (back, forward) are SDL buttons 4 and 5
        };
    }

    /** GLFW_MOD_* to SDL_Keymod. GLFW does not say left or right, so the left bit is used. */
    public static int modsToWire(int glfwMods) {
        int out = 0;
        if ((glfwMods & 0x01) != 0) out |= 0x0001;   // shift -> SDL_KMOD_LSHIFT
        if ((glfwMods & 0x02) != 0) out |= 0x0040;   // control -> SDL_KMOD_LCTRL
        if ((glfwMods & 0x04) != 0) out |= 0x0100;   // alt -> SDL_KMOD_LALT
        if ((glfwMods & 0x08) != 0) out |= 0x0400;   // super -> SDL_KMOD_LGUI
        if ((glfwMods & 0x10) != 0) out |= 0x2000;   // caps lock -> SDL_KMOD_CAPS
        if ((glfwMods & 0x20) != 0) out |= 0x1000;   // num lock -> SDL_KMOD_NUM
        return out;
    }

    private static final int[] SCAN = new int[349];

    static {
        // Letters: GLFW 65..90 -> SDL 4..29. Digits: GLFW 48..57 (0..9) -> SDL 39, 30..38.
        for (int i = 0; i < 26; i++) SCAN[65 + i] = 4 + i;
        SCAN[48] = 39;
        for (int i = 1; i <= 9; i++) SCAN[48 + i] = 29 + i;
        SCAN[32] = 44;    // space
        SCAN[39] = 52;    // apostrophe
        SCAN[44] = 54;    // comma
        SCAN[45] = 45;    // minus
        SCAN[46] = 55;    // period
        SCAN[47] = 56;    // slash
        SCAN[59] = 51;    // semicolon
        SCAN[61] = 46;    // equals
        SCAN[91] = 47;    // left bracket
        SCAN[92] = 49;    // backslash
        SCAN[93] = 48;    // right bracket
        SCAN[96] = 53;    // grave accent
        SCAN[256] = 41;   // escape
        SCAN[257] = 40;   // enter
        SCAN[258] = 43;   // tab
        SCAN[259] = 42;   // backspace
        SCAN[260] = 73;   // insert
        SCAN[261] = 76;   // delete
        SCAN[262] = 79;   // right
        SCAN[263] = 80;   // left
        SCAN[264] = 81;   // down
        SCAN[265] = 82;   // up
        SCAN[266] = 75;   // page up
        SCAN[267] = 78;   // page down
        SCAN[268] = 74;   // home
        SCAN[269] = 77;   // end
        SCAN[280] = 57;   // caps lock
        SCAN[281] = 71;   // scroll lock
        SCAN[282] = 83;   // num lock
        SCAN[283] = 70;   // print screen
        SCAN[284] = 72;   // pause
        for (int i = 0; i < 12; i++) SCAN[290 + i] = 58 + i;    // F1..F12
        for (int i = 0; i < 12; i++) SCAN[302 + i] = 104 + i;   // F13..F24 (F25 has no SDL scancode)
        SCAN[320] = 98;                                          // keypad 0
        for (int i = 1; i <= 9; i++) SCAN[320 + i] = 88 + i;    // keypad 1..9
        SCAN[330] = 99;   // keypad decimal
        SCAN[331] = 84;   // keypad divide
        SCAN[332] = 85;   // keypad multiply
        SCAN[333] = 86;   // keypad subtract
        SCAN[334] = 87;   // keypad add
        SCAN[335] = 88;   // keypad enter
        SCAN[336] = 103;  // keypad equal
        SCAN[340] = 225;  // left shift
        SCAN[341] = 224;  // left control
        SCAN[342] = 226;  // left alt
        SCAN[343] = 227;  // left super
        SCAN[344] = 229;  // right shift
        SCAN[345] = 228;  // right control
        SCAN[346] = 230;  // right alt
        SCAN[347] = 231;  // right super
        SCAN[348] = 118;  // menu
    }

    /** GLFW key token to SDL3 scancode; 0 when SDL has no such key. */
    public static int keyToWire(int glfwKey) {
        return glfwKey >= 0 && glfwKey < SCAN.length ? SCAN[glfwKey] : 0;
    }
}
