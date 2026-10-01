package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A use swing is a right click that animates the arm (ender pearl, wind charge, crystal / block placement). 26.x sends
 * no packet for it, so count the predicted swing calls inside {@code startUseItem} (entity use, block use, item use).
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;"
                    + "Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private void holo$useSwing(CallbackInfo ci) {
        RawInputCapture.useSwing();
    }
}
