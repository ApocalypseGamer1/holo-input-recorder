package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Round review: the server holds the player in spectator mode, but the replay shows their hand like in the fight. */
@Mixin(GameRenderer.class)
abstract class ReviewRenderMixin {
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType holo$reviewHand(MultiPlayerGameMode mode) {
        GameType t = mode.getPlayerMode();
        return ReviewClient.active() && t == GameType.SPECTATOR ? GameType.SURVIVAL : t;
    }
}
