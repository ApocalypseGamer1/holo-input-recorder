package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round review: the bot's replay copy wears the bot's skin. */
@Mixin(AbstractClientPlayer.class)
abstract class ReviewSkinMixin {
    @Inject(method = "getPlayerInfo", at = @At("HEAD"), cancellable = true)
    private void holo$ghostSkin(CallbackInfoReturnable<PlayerInfo> ci) {
        if ((Object) this == ReviewClient.ghost()) ci.setReturnValue(ReviewClient.ghostInfo());
    }
}
