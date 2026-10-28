package holorecorder.mixin;

import holorecorder.InputCodes;
import holorecorder.RawInputCapture;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key press and release timing. {@link RawInputCapture} refuses any key the player has not bound to a game
 * control, and the capture gate is shut whenever a screen is open, so typed text is never seen here.
 * 1.21.9 and later: {@code keyPress(window, action, KeyEvent)}; the action is GLFW's before 26.3 and SDL's on 26.3,
 * {@link InputCodes#action} maps either to the wire value.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void holo$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        RawInputCapture.key(event.key(), InputCodes.action(action), event.modifiers());
    }
}
