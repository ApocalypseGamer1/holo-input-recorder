package holorecorder.review;

import com.mojang.authlib.GameProfile;
import holorecorder.protocol.ReviewDataPayload;
import holorecorder.protocol.ReviewEndPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Round review playback: the server sends the last round, the camera then sits in the player's recorded eyes while a
 * client-only copy of the bot replays its moves. Everything here runs on the client thread.
 */
public final class ReviewClient {
    private static final Logger LOG = LoggerFactory.getLogger("holorec-review");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int GHOST_ID = -1_000_424_242;
    private static final float EYE = 1.62f, EYE_SNEAK = 1.27f;

    private static int session = -1;
    private static byte[][] parts;
    private static int received;
    private static boolean active;
    static ReviewRecord record;
    static ReviewAnalysis analysis;
    private static RemotePlayer ghost;
    private static UUID oppUuid;

    /** Playback position in ticks (fractional), speed as a multiple of real time. */
    static double cursor;
    static double speed = 1;
    static boolean playing;
    /** Pause when the cursor reaches this tick (-1 = no stop). */
    static int stopAt = -1;
    /** Look where the bot would have looked instead of where the player looked. */
    static boolean coachView;
    private static long lastNanos;
    private static int lastWhole = -1;
    private static double coachYawOff, coachPitchOff;

    private ReviewClient() {
    }

    public static boolean active() {
        return active;
    }

    public static Entity ghost() {
        return ghost;
    }

    public static void receive(Minecraft mc, ReviewDataPayload p) {
        if (p.count() == 0) {
            if (p.session() == session) stop(mc, false);
            return;
        }
        if (p.session() != session || parts == null || parts.length != p.count()) {
            if (active) stop(mc, false);
            session = p.session();
            parts = new byte[p.count()][];
            received = 0;
        }
        if (p.index() < 0 || p.index() >= parts.length || parts[p.index()] != null) return;
        parts[p.index()] = p.data();
        if (++received < parts.length) return;
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : parts) all.writeBytes(part);
        parts = null;
        try {
            record = ReviewRecord.fromBytes(all.toByteArray());
        } catch (IOException e) {
            LOG.warn("holorec: round review not readable: {}", e.toString());
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("This round review could not be opened. Update the Holoplayer mod."));
            send(new ReviewEndPayload(session));
            return;
        }
        analysis = ReviewAnalysis.of(record);
        start(mc);
    }

    /** Dev demo: a review from a file, with no server behind it. */
    static void startLocal(Minecraft mc, ReviewRecord r) {
        session = -1;
        record = r;
        analysis = ReviewAnalysis.of(r);
        start(mc);
    }

    private static void start(Minecraft mc) {
        if (mc.level == null || record.ticks.isEmpty()) {
            send(new ReviewEndPayload(session));
            return;
        }
        active = true;
        cursor = 0;
        speed = 1;
        playing = true;
        stopAt = -1;
        coachView = false;
        lastNanos = 0;
        lastWhole = -1;
        coachYawOff = coachPitchOff = 0;
        oppUuid = parseUuid(record.oppUuid);
        spawnGhost(mc.level);
        mc.gui.setScreen(new ReviewScreen());
    }

    /** Ends the review; {@code tellServer} when the player closed it (the server then puts them back). */
    public static void stop(Minecraft mc, boolean tellServer) {
        if (!active) return;
        active = false;
        if (tellServer) send(new ReviewEndPayload(session));
        if (ghost != null && mc.level != null) mc.level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        ghost = null;
        if (mc.gui.screen() instanceof ReviewScreen) mc.gui.setScreen(null);
    }

    public static void reset() {
        active = false;
        ghost = null;
        parts = null;
        record = null;
        analysis = null;
        session = -1;
    }

    private static void send(ReviewEndPayload p) {
        if (ClientPlayNetworking.canSend(ReviewEndPayload.TYPE)) ClientPlayNetworking.send(p);
    }

    private static void spawnGhost(ClientLevel level) {
        String name = record.oppName.length() > 16 ? record.oppName.substring(0, 16) : record.oppName;
        ghost = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), name.isEmpty() ? "Bot" : name));
        ghost.setId(GHOST_ID);
        for (int i = 0; i < SLOTS.length; i++) ghost.setItemSlot(SLOTS[i], stack(record.oppItems[i]));
        poseGhost(0);
        level.addEntity(ghost);
    }

    /** The bot's skin: its tab-list entry when it has one. */
    public static PlayerInfo ghostInfo() {
        Minecraft mc = Minecraft.getInstance();
        return oppUuid == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(oppUuid);
    }

    private static ItemStack stack(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- playback

    public static int ticks() {
        return record == null ? 0 : record.ticks.size();
    }

    /** Once per frame, before the camera is placed: moves the cursor and the bot copy. */
    public static void frame(Minecraft mc) {
        if (!active || record == null) return;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        int n = ticks();
        if (playing) {
            double before = cursor;
            cursor += dt * 20 * speed;
            if (stopAt >= 0 && before <= stopAt && cursor >= stopAt) {
                cursor = stopAt;
                playing = false;
                stopAt = -1;
            }
            if (cursor >= n - 1) {
                cursor = n - 1;
                playing = false;
            }
        }
        int whole = (int) Math.floor(cursor);
        if (whole > lastWhole && whole - lastWhole <= 3) {
            for (int i = lastWhole + 1; i <= whole; i++) tickEffects(mc, i);
        }
        lastWhole = whole;
        if (ghost != null) poseGhost(cursor);
        ReviewAnalysis.Suggestion s = analysis.suggestion(whole);
        double yawOff = s == null ? 0 : s.yawDeg(), pitchOff = s == null ? 0 : s.pitchDeg();
        double k = 1 - Math.exp(-dt / 0.12);
        coachYawOff += (yawOff - coachYawOff) * k;
        coachPitchOff += (pitchOff - coachPitchOff) * k;
    }

    private static void tickEffects(Minecraft mc, int i) {
        ReviewRecord.Tick t = record.ticks.get(i);
        if (t.self.swingTime == -1 && mc.player != null) swing(mc.player);
        if (ghost == null) return;
        if (t.opp.swingTime == -1) swing(ghost);
        if (t.opp.hurt) ghost.hurtTime = 10;
        if (i > 0) {
            ReviewRecord.Fighter a = record.ticks.get(i - 1).opp;
            float moved = (float) Math.hypot(t.opp.x - a.x, t.opp.z - a.z);
            ghost.walkAnimation.update(Math.min(moved * 4, 1), 1f, 1f);
        }
    }

    private static void swing(net.minecraft.world.entity.LivingEntity e) {
        e.swing(InteractionHand.MAIN_HAND, e.getMainHandItem().getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), false);
    }

    private static void poseGhost(double at) {
        ReviewRecord.Fighter a = fighter(at, false), b = fighter(at + 1, false);
        double f = at - Math.floor(at);
        double x = lerp(f, a.x, b.x), y = lerp(f, a.y, b.y), z = lerp(f, a.z, b.z);
        float yaw = (float) (a.yaw + ReviewAnalysis.wrap(b.yaw - a.yaw) * f), pitch = (float) lerp(f, a.pitch, b.pitch);
        ghost.setPos(x, y, z);
        ghost.xo = ghost.xOld = x;
        ghost.yo = ghost.yOld = y;
        ghost.zo = ghost.zOld = z;
        ghost.setYRot(yaw);
        ghost.yRotO = yaw;
        ghost.setXRot(pitch);
        ghost.xRotO = pitch;
        ghost.yHeadRot = ghost.yHeadRotO = yaw;
        ghost.yBodyRot = ghost.yBodyRotO = yaw;
        ghost.setShiftKeyDown(a.sneaking);
        ghost.setHealth(Math.max(0.5f, a.health));
    }

    private static ReviewRecord.Fighter fighter(double at, boolean self) {
        int i = Math.max(0, Math.min(ticks() - 1, (int) Math.floor(at)));
        ReviewRecord.Tick t = record.ticks.get(i);
        return self ? t.self : t.opp;
    }

    /** Camera position and rotation for this frame: {x, y, z, yaw, pitch}, or null when no review runs. */
    public static double[] eyes() {
        if (!active || record == null) return null;
        ReviewRecord.Fighter a = fighter(cursor, true), b = fighter(cursor + 1, true);
        double f = cursor - Math.floor(cursor);
        double eye = lerp(f, a.sneaking ? EYE_SNEAK : EYE, b.sneaking ? EYE_SNEAK : EYE);
        double yaw = a.yaw + ReviewAnalysis.wrap(b.yaw - a.yaw) * f, pitch = lerp(f, a.pitch, b.pitch);
        if (coachView) {
            yaw += coachYawOff;
            pitch = Math.max(-90, Math.min(90, pitch + coachPitchOff));
        }
        return new double[] {lerp(f, a.x, b.x), lerp(f, a.y, b.y) + eye, lerp(f, a.z, b.z), yaw, pitch};
    }

    /** Where the bot would aim now, relative to the camera, in degrees {yaw, pitch}. */
    static double[] coachOffset() {
        return coachView ? new double[] {0, 0} : new double[] {coachYawOff, coachPitchOff};
    }

    static void seek(double tick) {
        cursor = Math.max(0, Math.min(ticks() - 1, tick));
        lastWhole = (int) Math.floor(cursor);
        stopAt = -1;
    }

    private static double lerp(double f, double a, double b) {
        return a + (b - a) * f;
    }
}
