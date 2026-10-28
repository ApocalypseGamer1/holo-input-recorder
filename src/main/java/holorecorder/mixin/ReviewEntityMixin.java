package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round review: only the bot's replay copy is drawn; the live world's entities would get in the way. */
@Mixin(EntityRenderDispatcher.class)
abstract class ReviewEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void holo$reviewOnly(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> ci) {
        if (ReviewClient.active() && entity != ReviewClient.ghost()) ci.setReturnValue(false);
    }
}
