package holorecorder.mixin;

import holorecorder.RawInputCapture;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An attack swing is a left click: the client sends {@link ServerboundPunchPacket}, the same packet the server counts. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerImplMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void holo$punch(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundPunchPacket) RawInputCapture.swing();
    }
}
