package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private boolean mouseGrabbed;
    @Shadow private boolean ignoreFirstMove;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void holo$rawTurn(double elapsed, CallbackInfo ci) {
        RawInputCapture.mouseTurn(accumulatedDX, accumulatedDY);
    }

    /**
     * The cursor callback, one call per queued event. 26.x passes the per-event delta ({@code dx}, {@code dy})
     * itself and adds it to {@code accumulatedDX/DY} while the mouse is grabbed, so this is the same quantity
     * as the tick total - with raw mouse motion on, one report per mouse poll rather than one merged delta per frame.
     */
    @Inject(method = "onMove", at = @At("HEAD"))
    private void holo$rawMove(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        if (mouseGrabbed && !ignoreFirstMove) RawInputCapture.move(dx, dy);
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void holo$rawPress(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1) RawInputCapture.mousePress(info.button());
        if (action == 1 || action == 0) RawInputCapture.button(info.button(), action == 1, info.modifiers());
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void holo$rawScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        RawInputCapture.scroll(xOffset, yOffset);
    }
}
