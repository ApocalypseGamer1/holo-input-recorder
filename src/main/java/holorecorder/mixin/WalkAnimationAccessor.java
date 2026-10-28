package holorecorder.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Round review: the bot copy's legs are posed from the recording, not ticked. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccessor {
    @Accessor("speedOld")
    void holo$setSpeedOld(float v);

    @Accessor("speed")
    void holo$setSpeed(float v);

    @Accessor("position")
    void holo$setPosition(float v);
}
