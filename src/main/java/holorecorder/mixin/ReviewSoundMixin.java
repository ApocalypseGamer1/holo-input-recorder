package holorecorder.mixin;

import holorecorder.review.ReviewClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Round review: the live world's player sounds (a Swordbot clip being played out on the server) would not match the replay. */
@Mixin(SoundManager.class)
abstract class ReviewSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void holo$muteLivePlayers(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> ci) {
        if (ReviewClient.active() && sound.getSource() == SoundSource.PLAYERS) ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
