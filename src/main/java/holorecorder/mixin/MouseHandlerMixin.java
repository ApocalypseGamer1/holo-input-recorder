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

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void holo$rawTurn(double elapsed, CallbackInfo ci) {
        RawInputCapture.mouseTurn(accumulatedDX, accumulatedDY);
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void holo$rawPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action == 1) RawInputCapture.mousePress(button);
    }
}
