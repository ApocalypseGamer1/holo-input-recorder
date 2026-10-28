package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 has no {@code ServerboundPunchPacket}: a left click and a right-click arm swing send the same swing packet, so
 * the split is made at the call sites. {@code startAttack} swings twice (piercing weapon, everything else): both are
 * attack swings; {@code continueAttack} (mining) is not counted. The {@code startUseItem} swings are use swings.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void holo$attackSwing(CallbackInfoReturnable<Boolean> ci) {
        RawInputCapture.swing();
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void holo$useSwing(CallbackInfo ci) {
        RawInputCapture.useSwing();
    }
}
