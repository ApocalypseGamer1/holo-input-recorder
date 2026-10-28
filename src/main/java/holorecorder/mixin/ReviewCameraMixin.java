package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Round review: the camera sits in the player's recorded eyes (before the frustum is built from it). */
@Mixin(Camera.class)
abstract class ReviewCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void holo$reviewEyes(float partialTicks, CallbackInfo ci) {
        if (!ReviewClient.active()) return;
        ReviewClient.frame(Minecraft.getInstance(), partialTicks);
        double[] e = ReviewClient.eyes();
        if (e == null) return;
        setRotation((float) e[3], (float) e[4]);
        setPosition(e[0], e[1], e[2]);
    }
}
