package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round review: arm swings follow the recording at the cursor, so scrubbing and slow motion show them. */
@Mixin(LivingEntity.class)
abstract class ReviewSwingMixin {
    @Inject(method = "getSwingAnimation", at = @At("HEAD"), cancellable = true)
    private void holo$reviewSwing(float partial, CallbackInfoReturnable<Float> ci) {
        float p = ReviewClient.swingProgress(this);
        if (!Float.isNaN(p)) ci.setReturnValue(p);
    }
}
