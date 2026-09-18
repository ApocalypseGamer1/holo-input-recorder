package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private boolean mouseGrabbed;
    @Shadow private boolean ignoreFirstMove;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void holo$rawTurn(double elapsed, CallbackInfo ci) {
        RawInputCapture.mouseTurn(accumulatedDX, accumulatedDY);
    }

    /**
     * The GLFW cursor-position callback, one call per queued event. At HEAD {@code xpos}/{@code ypos} still
     * hold the previous position, so the difference is that single report's delta - with raw mouse motion on,
     * one per mouse poll rather than one merged delta per frame.
     */
    @Inject(method = "onMove", at = @At("HEAD"))
    private void holo$rawMove(long window, double x, double y, CallbackInfo ci) {
        if (mouseGrabbed && !ignoreFirstMove) RawInputCapture.move(x - xpos, y - ypos);
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void holo$rawPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == 1) RawInputCapture.mousePress(button);
        if (action == 1 || action == 0) RawInputCapture.button(button, action == 1, modifiers);
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void holo$rawScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        RawInputCapture.scroll(xOffset, yOffset);
    }
}
