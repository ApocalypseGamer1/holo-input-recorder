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
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void holo$rawTurn(double elapsed, CallbackInfo ci) {
        RawInputCapture.mouseTurn(accumulatedDX, accumulatedDY);
    }

    /**
     * The GLFW cursor-position callback, one call per queued event. At HEAD {@code xpos}/{@code ypos} still hold the
     * previous position, so the difference is that single report's delta (26.3 passes it as arguments instead).
     */
    @Inject(method = "onMove", at = @At("HEAD"))
    private void holo$rawMove(long window, double x, double y, CallbackInfo ci) {
        // Not grabbed: the cursor inside the player inventory screen (window pixels, client flag 12 marks the tick).
        if (mouseGrabbed ? !ignoreFirstMove : RawInputCapture.cursorCapture()) RawInputCapture.move(x - xpos, y - ypos);
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
