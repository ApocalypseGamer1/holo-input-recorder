package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "swing", at = @At("HEAD"))
    private void holo$swing(InteractionHand hand, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND) RawInputCapture.swing();
    }
}
