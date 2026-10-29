package holorecorder;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.protocol.ClientEventsPayload;
import holorecorder.protocol.ClientInputPayload;
import holorecorder.protocol.ClientModsPayload;
import holorecorder.protocol.ClientModsV2Payload;
import holorecorder.protocol.IntegrityChallengePayload;
import holorecorder.protocol.IntegrityResponsePayload;
import holorecorder.protocol.ImproveActionPayload;
import holorecorder.protocol.ImproveOpenPayload;
import holorecorder.protocol.MenuClickPayload;
import holorecorder.protocol.MenuClosePayload;
import holorecorder.protocol.MenuClosedPayload;
import holorecorder.protocol.MenuOpenPayload;
import holorecorder.protocol.MenuUpdatePayload;
import holorecorder.protocol.RoundStatePayload;
import holorecorder.protocol.UpdatePromptPayload;
import holorecorder.protocol.ReviewClipPayload;
import holorecorder.protocol.ReviewClipRequestPayload;
import holorecorder.protocol.ReviewDataPayload;
import holorecorder.protocol.ReviewEndPayload;
import holorecorder.protocol.WinBarPayload;
import holorecorder.improve.ImproveClient;
import holorecorder.menu.MenuClient;
import holorecorder.review.ReviewClient;
import holorecorder.update.UpdateClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import holorecorder.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HoloInputRecorder implements ClientModInitializer {
    private static boolean active;
    private static String roundId = "";
    private static String mode = "";
    private static long sequence;
    private static float previousYaw, previousPitch;
    private static boolean haveView;
    /**
     * Key codes (SDL3 scancodes) the player has bound to a game control; nothing else is ever logged.
     *
     * <p>Replaced wholesale rather than mutated: it is read on the render thread from the key callback and
     * written on the client tick, so a HashSet being rebuilt in place could be observed half-cleared and let
     * an unbound key through.
     */
    private static volatile Set<Integer> boundKeys = Set.of();
    private static final Logger LOG = LoggerFactory.getLogger("holorec-client");
    private static long eventPackets, eventsSent;
    private static final AtomicBoolean integrityBusy = new AtomicBoolean();

    @Override public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(ClientInputPayload.TYPE, ClientInputPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientEventsPayload.TYPE, ClientEventsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientModsPayload.TYPE, ClientModsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientModsV2Payload.TYPE, ClientModsV2Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(IntegrityResponsePayload.TYPE, IntegrityResponsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoundStatePayload.TYPE, RoundStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(IntegrityChallengePayload.TYPE, IntegrityChallengePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WinBarPayload.TYPE, WinBarPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewDataPayload.TYPE, ReviewDataPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReviewEndPayload.TYPE, ReviewEndPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ReviewClipPayload.TYPE, ReviewClipPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReviewClipRequestPayload.TYPE, ReviewClipRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(holorecorder.protocol.LangPayload.TYPE, holorecorder.protocol.LangPayload.CODEC);
        // Custom menus (1.10.0): the server draws no chest for a client that takes these channels.
        PayloadTypeRegistry.clientboundPlay().register(MenuOpenPayload.TYPE, MenuOpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MenuUpdatePayload.TYPE, MenuUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MenuClosePayload.TYPE, MenuClosePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MenuClickPayload.TYPE, MenuClickPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MenuClosedPayload.TYPE, MenuClosedPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MenuOpenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MenuClient.open(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(MenuUpdatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> MenuClient.update(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(MenuClosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> MenuClient.close(context.client(), payload)));
        // Improve screen (1.11.0): a receiver for the open payload is what tells the server this client can draw it.
        PayloadTypeRegistry.clientboundPlay().register(ImproveOpenPayload.TYPE, ImproveOpenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ImproveActionPayload.TYPE, ImproveActionPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ImproveOpenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ImproveClient.open(context.client(), payload)));
        // Mod updates (1.13.0): a receiver for the prompt tells the server this client can show it; the client checks the official release page itself.
        PayloadTypeRegistry.clientboundPlay().register(UpdatePromptPayload.TYPE, UpdatePromptPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(UpdatePromptPayload.TYPE, (payload, context) ->
                context.client().execute(() -> UpdateClient.prompt(context.client(), payload)));
        UpdateClient.init();
        ClientPlayNetworking.registerGlobalReceiver(holorecorder.protocol.LangPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Tr.server(payload.code())));
        ClientPlayNetworking.registerGlobalReceiver(ReviewDataPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ReviewClient.receive(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(ReviewClipPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ReviewClient.receiveClip(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RoundStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> setRound(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(IntegrityChallengePayload.TYPE, (payload, context) ->
                answerIntegrity(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(WinBarPayload.TYPE, (payload, context) ->
                context.client().execute(() -> WinBarHud.receive(context.client(), payload)));
        WinBarHud.register();
        ClientTickEvents.END_CLIENT_TICK.register(WinBarHud::demoTick);
        ClientTickEvents.END_CLIENT_TICK.register(holorecorder.review.ReviewDemo::tick);
        ClientTickEvents.END_CLIENT_TICK.register(holorecorder.improve.ImproveDemo::tick);
        RawInputCapture.screenProbe(() -> screenKind(Minecraft.getInstance()));
        ClientTickEvents.START_CLIENT_TICK.register(HoloInputRecorder::captureTick);
        // Mod list for data protection: once per connection (JOIN also fires after a transfer), only if the server declared
        // the channel. Version 2 (with file fingerprints) when the server takes it, else version 1.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ClientModsV2Payload.TYPE)) {
                sendModsV2(client, handler);
            } else {
                ClientModsReport.sendIfSupported(() -> ClientPlayNetworking.canSend(ClientModsPayload.TYPE),
                        ClientModsReport::fromLoader, ClientPlayNetworking::send);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            Tr.server(null);
            WinBarHud.reset();
            ReviewClient.reset();
            UpdateClient.reset();
        });
    }

    /**
     * Mod list version 2: fingerprints are computed off the render thread, then every part is sent on the client thread,
     * only while the same connection is still open and the server still takes the channel. Every join gets its own
     * report (a transfer to another server included); the fingerprint cache makes repeats cheap.
     */
    private static void sendModsV2(Minecraft client, Object connection) {
        Thread.ofPlatform().daemon().name("holo-mod-report").start(() -> {
            try {
                List<ClientModsV2Payload> parts = ClientModsReport.fromLoaderV2();
                client.execute(() -> {
                    if (client.getConnection() != connection || !ClientPlayNetworking.canSend(ClientModsV2Payload.TYPE)) return;
                    for (ClientModsV2Payload part : parts) ClientPlayNetworking.send(part);
                });
            } catch (RuntimeException e) {
                LOG.warn("holorec: mod report not sent: {}", e.toString());
            }
        });
    }

    /** File check for data protection: one response per challenge, hashed off the render thread, only if the server declared the channel. */
    private static void answerIntegrity(Minecraft client, IntegrityChallengePayload challenge) {
        if (challenge.version() != IntegrityChallengePayload.VERSION
                || !ClientPlayNetworking.canSend(IntegrityResponsePayload.TYPE)
                || !integrityBusy.compareAndSet(false, true)) return;
        Thread.ofPlatform().daemon().name("holo-file-check").start(() -> {
            try {
                IntegrityResponsePayload response = IntegrityCheck.respond(challenge);
                client.execute(() -> {
                    if (ClientPlayNetworking.canSend(IntegrityResponsePayload.TYPE)) ClientPlayNetworking.send(response);
                });
            } finally {
                integrityBusy.set(false);
            }
        });
    }

    private static void setRound(Minecraft client, RoundStatePayload state) {
        if (active && !state.active() && eventPackets > 0) {
            LOG.info("holorec: sub-tick capture sent {} events in {} packets", eventsSent, eventPackets);
        }
        active = state.active();
        roundId = active ? state.roundId() : "";
        mode = active ? state.mode() : "";
        sequence = 0;
        haveView = false;
        eventPackets = eventsSent = 0;
        refreshBoundKeys(client);
        RawInputCapture.clear();
        RawInputCapture.arm(false, HoloInputRecorder::isBoundKey);
        if (client.player != null) {
            String text = active ? Tr.t("Holo input capture active for this round.") : Tr.t("Holo input capture saved.");
            client.player.sendOverlayMessage(Component.literal(text));
        }
    }

    private static void captureTick(Minecraft client) {
        if (!active || client.player == null || client.level == null) {
            RawInputCapture.arm(false, null);
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
        RawInputCapture.Events events = RawInputCapture.drainEvents();
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
        int screen = screenKind(client);
        if (client.gui.screen() != null) flags |= 1 << 11;
        if (screen == RawInputCapture.SCREEN_INVENTORY) flags |= 1 << 12;

        ClientPlayNetworking.send(ClientInputPayload.of(roundId, ++sequence, p.tickCount, System.currentTimeMillis(),
                raw, turnYaw, turnPitch, flags, p.getInventory().getSelectedSlot()));

        if (events.size() > 0 && ClientPlayNetworking.canSend(ClientEventsPayload.TYPE)) {
            eventPackets++;
            eventsSent += events.size();
            ClientPlayNetworking.send(new ClientEventsPayload(ClientEventsPayload.VERSION, roundId, sequence,
                    p.tickCount, events.baseUnixMs(), events.dropped(),
                    events.offsetMicros(), events.types(), events.a(), events.b()));
        }
        // Capture only while a consented round runs, with no screen or the player's own inventory open (mouse only there).
        RawInputCapture.arm(screen != RawInputCapture.SCREEN_OTHER, HoloInputRecorder::isBoundKey);
    }

    /** No screen, the player's own inventory screen (exactly that class: no creative tabs, no mod subclass), or another one. */
    static int screenKind(Minecraft client) {
        Screen s = client == null || client.gui == null ? null : client.gui.screen();
        if (s == null) return RawInputCapture.SCREEN_NONE;
        return s.getClass() == InventoryScreen.class ? RawInputCapture.SCREEN_INVENTORY : RawInputCapture.SCREEN_OTHER;
    }

    /** True when the key is bound to one of the player's game controls. */
    static boolean isBoundKey(int key) {
        return boundKeys.contains(key);
    }

    private static void refreshBoundKeys(Minecraft client) {
        if (client.options == null) {
            boundKeys = Set.of();
            return;
        }
        Set<Integer> bound = new HashSet<>();
        for (KeyMapping mapping : client.options.keyMappings) {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).holo$currentKey();
            if (key != null && InputCodes.isBindableKey(key)) {
                bound.add(key.getValue());
            }
        }
        boundKeys = Set.copyOf(bound);
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private static void reset() {
        active = false; roundId = mode = ""; sequence = 0; haveView = false;
        RawInputCapture.arm(false, null);
        RawInputCapture.clear();
    }
}

