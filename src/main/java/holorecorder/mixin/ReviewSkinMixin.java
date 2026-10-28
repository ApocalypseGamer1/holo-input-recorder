package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round review: the bot's replay copy wears the bot's skin, but never its game mode (a spectator bot draws as a head). */
@Mixin(AbstractClientPlayer.class)
abstract class ReviewSkinMixin {
    @Inject(method = "getPlayerInfo", at = @At("HEAD"), cancellable = true)
    private void holo$ghostSkin(CallbackInfoReturnable<PlayerInfo> ci) {
        if ((Object) this == ReviewClient.ghost()) ci.setReturnValue(ReviewClient.ghostInfo());
    }

    @Inject(method = "gameMode", at = @At("HEAD"), cancellable = true)
    private void holo$ghostMode(CallbackInfoReturnable<GameType> ci) {
        if ((Object) this == ReviewClient.ghost()) ci.setReturnValue(GameType.SURVIVAL);
    }
}
