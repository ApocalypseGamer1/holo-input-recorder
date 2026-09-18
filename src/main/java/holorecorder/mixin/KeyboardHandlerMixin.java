package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key press and release timing. {@link RawInputCapture} refuses any key the player has not bound to a game
 * control, and the capture gate is shut whenever a screen is open, so typed text is never seen here.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void holo$keyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        RawInputCapture.key(key, action, modifiers);
    }
}
