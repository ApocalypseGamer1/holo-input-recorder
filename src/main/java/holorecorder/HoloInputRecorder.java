package holorecorder;

import holorecorder.protocol.ClientInputPayload;
import holorecorder.protocol.RoundStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class HoloInputRecorder implements ClientModInitializer {
    private static boolean active;
    private static String roundId = "";
    private static String mode = "";
    private static long sequence;
    private static float previousYaw, previousPitch;
    private static boolean haveView;

    @Override public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(ClientInputPayload.TYPE, ClientInputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundStatePayload.TYPE, RoundStatePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(RoundStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> setRound(context.client(), payload)));
        ClientTickEvents.START_CLIENT_TICK.register(HoloInputRecorder::captureTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void setRound(Minecraft client, RoundStatePayload state) {
        active = state.active();
        roundId = active ? state.roundId() : "";
        mode = active ? state.mode() : "";
        sequence = 0;
        haveView = false;
        RawInputCapture.clear();
        if (client.player != null) {
            String text = active ? "Holo input capture active for this round." : "Holo input capture saved.";
            client.player.displayClientMessage(Component.literal(text), true);
        }
    }

    private static void captureTick(Minecraft client) {
        if (!active || client.player == null || client.level == null) {
            RawInputCapture.clear();
            haveView = false;
            return;
        }
        if (!ClientPlayNetworking.canSend(ClientInputPayload.TYPE)) {
            reset();
            return;
        }
        LocalPlayer p = client.player;
        RawInputCapture.Snapshot raw = RawInputCapture.drain();
        float yaw = p.getYRot(), pitch = p.getXRot();
        float turnYaw = haveView ? wrapDegrees(yaw - previousYaw) : 0.0f;
        float turnPitch = haveView ? pitch - previousPitch : 0.0f;
        previousYaw = yaw; previousPitch = pitch; haveView = true;

        int flags = 0;
        if (client.options.keyUp.isDown()) flags |= 1 << 0;
        if (client.options.keyDown.isDown()) flags |= 1 << 1;
        if (client.options.keyLeft.isDown()) flags |= 1 << 2;
        if (client.options.keyRight.isDown()) flags |= 1 << 3;
        if (client.options.keyJump.isDown()) flags |= 1 << 4;
        if (client.options.keyShift.isDown()) flags |= 1 << 5;
        if (client.options.keySprint.isDown()) flags |= 1 << 6;
        if (client.options.keyAttack.isDown()) flags |= 1 << 7;
        if (client.options.keyUse.isDown()) flags |= 1 << 8;
        if (p.isSprinting()) flags |= 1 << 9;
        if (p.isUsingItem()) flags |= 1 << 10;
        if (client.screen != null) flags |= 1 << 11;

        ClientPlayNetworking.send(new ClientInputPayload(ClientInputPayload.VERSION, roundId, ++sequence,
                p.tickCount, System.currentTimeMillis(), raw.dx(), raw.dy(), turnYaw, turnPitch, flags,
                raw.swings(), raw.leftPresses(), raw.rightPresses(), p.getInventory().getSelectedSlot()));
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private static void reset() {
        active = false; roundId = mode = ""; sequence = 0; haveView = false; RawInputCapture.clear();
    }
}
