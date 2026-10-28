package holorecorder.review;

import com.mojang.authlib.GameProfile;
import holorecorder.protocol.ReviewClipPayload;
import holorecorder.protocol.ReviewClipRequestPayload;
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
import java.util.List;
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
    /** "Swordbot's play": while a clip of the coach in your place plays, the round waits here (null when no clip plays). */
    private static ReviewRecord round;
    private static ReviewAnalysis roundAnalysis;
    private static double roundCursor;
    /** The same stretch twice: your play (from the round) and Swordbot's; the clip shows yours first, then Swordbot's. */
    private static ReviewRecord yours, swordbot;
    static boolean showingSwordbot;
    private static List<ReviewMoves.Move> moves = List.of();
    static int clipFrom = -1;
    private static String clipNote;
    private static long clipAsked, clipNoteAt;

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
        clearClip();
        oppUuid = parseUuid(record.oppUuid);
        prepare();
        spawnGhost(mc.level);
        mc.gui.setScreen(new ReviewScreen());
    }

    /** Ends the review; {@code tellServer} when the player closed it (the server then puts them back). */
    public static void stop(Minecraft mc, boolean tellServer) {
        if (!active) return;
        active = false;
        if (round != null) {
            record = round;
            analysis = roundAnalysis;
        }
        clearClip();
        if (tellServer) send(new ReviewEndPayload(session));
        if (ghost != null && mc.level != null) mc.level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        ghost = null;
        if (mc.player != null) mc.player.hurtTime = 0;
        if (mc.gui.screen() instanceof ReviewScreen) mc.gui.setScreen(null);
    }

    public static void reset() {
        active = false;
        ghost = null;
        parts = null;
        record = null;
        analysis = null;
        session = -1;
        clearClip();
    }

    private static void clearClip() {
        round = null;
        roundAnalysis = null;
        yours = swordbot = null;
        showingSwordbot = false;
        clipFrom = -1;
        clipNote = null;
        clipAsked = 0;
    }

    static boolean inClip() {
        return round != null;
    }

    /** The server can show Swordbot's play from a moment of this round. */
    static boolean clipsAvailable() {
        return session >= 0 && !inClip() && ClientPlayNetworking.canSend(ReviewClipRequestPayload.TYPE);
    }

    static boolean clipPending() {
        return clipAsked != 0;
    }

    /** What to tell the player about the last request, or null. */
    static String clipNote() {
        if (clipPending()) return "Swordbot is playing it...";
        return clipNote != null && System.nanoTime() - clipNoteAt < 6_000_000_000L ? clipNote : null;
    }

    private static void note(String text) {
        clipNote = text;
        clipNoteAt = System.nanoTime();
    }

    static void requestClip(int tick) {
        if (!clipsAvailable() || clipPending()) return;
        clipAsked = System.nanoTime();
        clipNote = null;
        ClientPlayNetworking.send(new ReviewClipRequestPayload(session, Math.max(0, tick)));
    }

    public static void receiveClip(ReviewClipPayload p) {
        if (!active || p.session() != session || !clipPending()) return;
        clipAsked = 0;
        if (p.status() != ReviewClipPayload.OK) {
            note(clipError(p.status()));
            return;
        }
        ReviewRecord clip;
        try {
            clip = ReviewRecord.fromBytes(p.data());
        } catch (IOException e) {
            LOG.warn("holorec: review clip not readable: {}", e.toString());
            note("Swordbot's play could not be opened. Update the Holoplayer mod.");
            return;
        }
        if (clip.ticks.size() < 2) {
            note("Swordbot's play stopped at once. Try another moment.");
            return;
        }
        playClip(clip, p.tick());
    }

    static String clipError(int status) {
        return switch (status) {
            case ReviewClipPayload.BUSY -> "Swordbot is still busy. Try again.";
            case ReviewClipPayload.NO_BOT -> "The bot is gone, so Swordbot cannot play here.";
            case ReviewClipPayload.TOO_LATE -> "Too near the end of the round. Pick an earlier moment.";
            case ReviewClipPayload.FAILED -> "Swordbot's play stopped. Try again.";
            default -> "Swordbot's play is not available for this round.";
        };
    }

    /** Plays {@code clip} (Swordbot in your place from round tick {@code from}) in slow motion; {@link #endClip} goes back. */
    static void playClip(ReviewRecord clip, int from) {
        if (!inClip()) {
            round = record;
            roundAnalysis = analysis;
            roundCursor = cursor;
        }
        clipFrom = from;
        swordbot = clip;
        yours = slice(round, from, clip.ticks.size());
        show(false);
        seek(0);
        lastWhole = -1;
        speed = 0.5;
        playing = true;
        coachView = false;
    }

    /** Ticks {@code from .. from + count - 1} of a round as a record of their own (no coach data). */
    static ReviewRecord slice(ReviewRecord r, int from, int count) {
        ReviewRecord s = new ReviewRecord();
        s.oppName = r.oppName;
        s.oppUuid = r.oppUuid;
        s.selfItems = r.selfItems;
        s.oppItems = r.oppItems;
        s.ticks.addAll(r.ticks.subList(Math.min(from, r.ticks.size()), Math.min(r.ticks.size(), from + count)));
        return s;
    }

    /** In a clip: show Swordbot's play or yours at the same moment. */
    static void show(boolean swordbotSide) {
        if (!inClip()) return;
        showingSwordbot = swordbotSide;
        record = swordbotSide ? swordbot : yours;
        analysis = ReviewAnalysis.of(record);
        prepare();
        cursor = Math.max(0, Math.min(ticks() - 1, cursor));
        lastWhole = (int) Math.floor(cursor);
    }

    /** One side's numbers over the whole clip: {hits landed, hits taken, hearts lost}. */
    static double[] tally(boolean swordbotSide) {
        ReviewRecord r = swordbotSide ? swordbot : yours;
        double landed = 0, taken = 0, lost = 0;
        for (int i = 0; i < r.ticks.size(); i++) {
            ReviewRecord.Tick t = r.ticks.get(i);
            if (t.opp.hurt) landed++;
            if (t.self.hurt) taken++;
            if (i > 0) lost += Math.max(0, r.ticks.get(i - 1).self.health - t.self.health) / 2.0;
        }
        return new double[] {landed, taken, lost};
    }

    static List<ReviewMoves.Move> movesOf(boolean swordbotSide) {
        return ReviewMoves.of(swordbotSide ? swordbot : yours, true);
    }

    /** The move the shown fighter is making at the cursor (shown a little past its end), or null. Strafes are left out. */
    static ReviewMoves.Move moveNow() {
        int c = (int) Math.floor(cursor);
        ReviewMoves.Move best = null;
        for (ReviewMoves.Move m : moves) {
            if (m.kind() == ReviewMoves.Kind.STRAFE_LEFT || m.kind() == ReviewMoves.Kind.STRAFE_RIGHT) continue;
            if (c >= m.start() && c <= m.end() + 8 && (best == null || m.start() >= best.start())) best = m;
        }
        return best;
    }

    static String moveName(ReviewMoves.Kind k) {
        return switch (k) {
            case CRIT -> "Crit";
            case SPRINT_HIT -> "Sprint hit";
            case JUMP_HIT -> "Jump hit";
            case WEAK_HIT -> "Weak hit (not charged)";
            case JUMP_RESET -> "Jump reset";
            case W_TAP -> "W-tap";
            case S_TAP -> "S-tap";
            case STRAFE_LEFT -> "Strafe left";
            case STRAFE_RIGHT -> "Strafe right";
        };
    }

    /** In a clip: ticks (up to 20) since a hit you took in the round that Swordbot did not take at that moment (large when none). */
    static double sinceDodged() {
        if (round == null || !showingSwordbot) return 1e9;
        int c = (int) Math.floor(cursor);
        for (int k = c; k >= Math.max(0, c - 19); k--) {
            int j = clipFrom + k;
            if (j >= round.ticks.size() || !round.ticks.get(j).self.hurt) continue;
            boolean took = false;
            for (int q = Math.max(0, k - 1); q <= Math.min(ticks() - 1, k + 1); q++) took |= record.ticks.get(q).self.hurt;
            if (!took) return cursor - k;
        }
        return 1e9;
    }

    /** Hits up to the cursor: on the bot ({@code onBot}) or on you (in a clip, on Swordbot). */
    static int hitsSoFar(boolean onBot) {
        int c = 0, end = Math.min(ticks() - 1, (int) Math.floor(cursor));
        for (int i = 0; i <= end; i++) if (onBot ? record.ticks.get(i).opp.hurt : record.ticks.get(i).self.hurt) c++;
        return c;
    }

    static void endClip() {
        if (!inClip()) return;
        record = round;
        analysis = roundAnalysis;
        round = null;
        roundAnalysis = null;
        yours = swordbot = null;
        showingSwordbot = false;
        prepare();
        seek(roundCursor);
        playing = false;
    }

    private static void send(ReviewEndPayload p) {
        if (ClientPlayNetworking.canSend(ReviewEndPayload.TYPE)) ClientPlayNetworking.send(p);
    }

    private static void spawnGhost(ClientLevel level) {
        String name = record.oppName.length() > 16 ? record.oppName.substring(0, 16) : record.oppName;
        ghost = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), name.isEmpty() ? "Bot" : name));
        ghost.setId(GHOST_ID);
        for (int i = 0; i < SLOTS.length; i++) ghost.setItemSlot(SLOTS[i], stack(record.oppItems[i]));
        poseGhost(0, 1f);
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

    private static final int SWING_TICKS = 6, HURT_TICKS = 10;
    /** Bot copy motion worked out once from the recording, so any frame (scrub, slow motion) poses it the same way. */
    private static float[] walkPos, walkSpeed, body;
    private static int[] selfSwing, oppSwing, selfHurt, oppHurt;
    static boolean stopAtMarks = true;

    public static int ticks() {
        return record == null ? 0 : record.ticks.size();
    }

    private static void prepare() {
        int n = ticks();
        moves = ReviewMoves.of(record, true);
        walkPos = new float[n];
        walkSpeed = new float[n];
        body = new float[n];
        selfSwing = new int[n];
        oppSwing = new int[n];
        selfHurt = new int[n];
        oppHurt = new int[n];
        float speed = 0, pos = 0, bodyYaw = record.ticks.get(0).opp.yaw;
        int ss = -1000, os = -1000, sh = -1000, oh = -1000;
        for (int i = 0; i < n; i++) {
            ReviewRecord.Tick t = record.ticks.get(i);
            double moved = 0, dx = 0, dz = 0;
            if (i > 0) {
                ReviewRecord.Fighter a = record.ticks.get(i - 1).opp;
                dx = t.opp.x - a.x;
                dz = t.opp.z - a.z;
                moved = Math.hypot(dx, dz);
            }
            // Vanilla walk animation: speed eases toward 4x the distance moved, the limb phase adds it up.
            speed += (Math.min(1f, (float) moved * 4f) - speed) * 0.4f;
            pos += speed;
            walkSpeed[i] = speed;
            walkPos[i] = pos;
            if (moved > 0.03) {
                float dir = (float) Math.toDegrees(Math.atan2(-dx, dz));
                if (Math.abs(ReviewAnalysis.wrap(dir - t.opp.yaw)) > 100) dir += 180;
                bodyYaw += (float) ReviewAnalysis.wrap(dir - bodyYaw) * 0.3f;
            }
            float off = (float) ReviewAnalysis.wrap(t.opp.yaw - bodyYaw);
            if (Math.abs(off) > 50) bodyYaw = t.opp.yaw - Math.signum(off) * 50;
            body[i] = bodyYaw;
            if (record.swingStart(i, true)) ss = i;
            if (record.swingStart(i, false)) os = i;
            if (t.self.hurt) sh = i;
            if (t.opp.hurt) oh = i;
            selfSwing[i] = ss;
            oppSwing[i] = os;
            selfHurt[i] = sh;
            oppHurt[i] = oh;
        }
    }

    /** Once per frame, before the camera is placed: moves the cursor and poses the bot copy. */
    public static void frame(Minecraft mc, float partial) {
        if (!active || record == null) return;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (clipPending() && now - clipAsked > 10_000_000_000L) {
            clipAsked = 0;
            note("No answer from the server. Try again.");
        }
        int n = ticks();
        if (playing) {
            double before = cursor;
            boolean slow = stopAtMarks && inMarkWindow(cursor);
            cursor += dt * 20 * (slow ? Math.min(speed, 0.5) : speed);
            if (stopAt >= 0 && before <= stopAt && cursor >= stopAt) {
                cursor = stopAt;
                playing = false;
                stopAt = -1;
            }
            if (stopAtMarks && playing) {
                for (ReviewAnalysis.Mark m : analysis.marks()) {
                    if (before < m.index() && cursor >= m.index()) {
                        cursor = m.index();
                        playing = false;
                        break;
                    }
                }
            }
            if (cursor >= n - 1 && inClip() && !showingSwordbot) {
                show(true);
                cursor = 0;
                lastWhole = -1;
                n = ticks();
            } else if (cursor >= n - 1) {
                cursor = n - 1;
                playing = false;
            }
        }
        int whole = (int) Math.floor(cursor);
        if (whole > lastWhole && whole - lastWhole <= 3) {
            for (int i = lastWhole + 1; i <= whole; i++) {
                ReviewRecord.Tick t = record.ticks.get(i);
                if (record.swingStart(i, true) && mc.player != null) swing(mc.player);
                if (record.swingStart(i, false) && ghost != null) swing(ghost);
            }
        }
        lastWhole = whole;
        if (ghost != null) {
            poseGhost(cursor, partial);
            ghost.hurtTime = hurtLeft(oppHurt);
            ghost.hurtDuration = HURT_TICKS;
        }
        if (mc.player != null) mc.player.hurtTime = 0;
        ReviewAnalysis.Suggestion s = analysis.suggestion(whole);
        double yawOff = s == null ? 0 : s.yawDeg(), pitchOff = s == null ? 0 : s.pitchDeg();
        double k = 1 - Math.exp(-dt / 0.12);
        coachYawOff += (yawOff - coachYawOff) * k;
        coachPitchOff += (pitchOff - coachPitchOff) * k;
    }

    private static boolean inMarkWindow(double at) {
        for (ReviewAnalysis.Mark m : analysis.marks()) if (at >= m.from() - 20 && at <= m.to()) return true;
        return false;
    }

    private static int hurtLeft(int[] last) {
        double since = since(last);
        return since < HURT_TICKS ? (int) Math.ceil(HURT_TICKS - since) : 0;
    }

    /** Ticks since the last event in {@code last} at the cursor (large when none). */
    private static double since(int[] last) {
        if (last == null) return 1e9;
        int i = Math.max(0, Math.min(last.length - 1, (int) Math.floor(cursor)));
        return last[i] < 0 ? 1e9 : cursor - last[i];
    }

    static double sinceYouHit() {
        return since(oppHurt);
    }

    static double sinceYouGotHit() {
        return since(selfHurt);
    }

    /** Hearts the player lost in the last hit at the cursor. */
    static float lastHitHearts() {
        if (selfHurt == null) return 0;
        int i = selfHurt[Math.max(0, Math.min(selfHurt.length - 1, (int) Math.floor(cursor)))];
        if (i <= 0) return 0;
        return Math.max(0, record.ticks.get(i - 1).self.health - record.ticks.get(Math.min(ticks() - 1, i + 1)).self.health) / 2f;
    }

    /** Arm swing progress 0..1 for the player or the bot copy at the cursor; NaN for any other entity. */
    public static float swingProgress(Object entity) {
        if (!active || selfSwing == null) return Float.NaN;
        int[] last = entity == ghost ? oppSwing : entity == Minecraft.getInstance().player ? selfSwing : null;
        if (last == null) return Float.NaN;
        double since = since(last);
        return since < SWING_TICKS ? (float) (since / SWING_TICKS) : 0f;
    }

    private static void swing(net.minecraft.world.entity.LivingEntity e) {
        e.swing(InteractionHand.MAIN_HAND, e.getMainHandItem().getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), false);
    }

    private static void poseGhost(double at, float partial) {
        ReviewRecord.Fighter a = fighter(at, false), b = fighter(at + 1, false);
        int i = Math.max(0, Math.min(ticks() - 1, (int) Math.floor(at))), j = Math.min(ticks() - 1, i + 1);
        double f = at - Math.floor(at);
        double x = lerp(f, a.x, b.x), y = lerp(f, a.y, b.y), z = lerp(f, a.z, b.z);
        float yaw = (float) (a.yaw + ReviewAnalysis.wrap(b.yaw - a.yaw) * f), pitch = (float) lerp(f, a.pitch, b.pitch);
        float bodyYaw = (float) (body[i] + ReviewAnalysis.wrap(body[j] - body[i]) * f);
        ghost.setPos(x, y, z);
        ghost.xo = ghost.xOld = x;
        ghost.yo = ghost.yOld = y;
        ghost.zo = ghost.zOld = z;
        ghost.setYRot(bodyYaw);
        ghost.yRotO = bodyYaw;
        ghost.setXRot(pitch);
        ghost.xRotO = pitch;
        ghost.yHeadRot = ghost.yHeadRotO = yaw;
        ghost.yBodyRot = ghost.yBodyRotO = bodyYaw;
        ghost.setShiftKeyDown(a.sneaking);
        ghost.setHealth(Math.max(0.5f, a.health));
        float speed = (float) lerp(f, walkSpeed[i], walkSpeed[j]), pos = (float) lerp(f, walkPos[i], walkPos[j]);
        // The renderer reads position - speed * (1 - partial); add that back so the legs sit exactly at the cursor.
        holorecorder.mixin.WalkAnimationAccessor walk = (holorecorder.mixin.WalkAnimationAccessor) ghost.walkAnimation;
        walk.holo$setSpeedOld(speed);
        walk.holo$setSpeed(speed);
        walk.holo$setPosition(pos + speed * (1 - partial));
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

    /** Reach distance: attacker's eyes to the nearest point of the target's body box, in blocks. */
    static double reach(ReviewRecord.Fighter from, ReviewRecord.Fighter to) {
        double ex = from.x, ey = from.y + (from.sneaking ? EYE_SNEAK : EYE), ez = from.z;
        double dx = Math.max(Math.abs(ex - to.x) - 0.3, 0), dz = Math.max(Math.abs(ez - to.z) - 0.3, 0);
        double dy = ey < to.y ? to.y - ey : ey > to.y + 1.8 ? ey - to.y - 1.8 : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double rangeNow() {
        ReviewRecord.Tick t = record.ticks.get(Math.max(0, Math.min(ticks() - 1, (int) Math.floor(cursor))));
        return reach(t.self, t.opp);
    }

    /** Range when your last hit (or the bot's last hit on you) landed, NaN before the first one. */
    static double rangeAtYourHit() {
        return rangeAt(oppHurt, true);
    }

    static double rangeAtBotHit() {
        return rangeAt(selfHurt, false);
    }

    private static double rangeAt(int[] last, boolean you) {
        if (last == null) return Double.NaN;
        int i = last[Math.max(0, Math.min(last.length - 1, (int) Math.floor(cursor)))];
        if (i < 0) return Double.NaN;
        ReviewRecord.Tick t = record.ticks.get(Math.max(0, i - 1));
        return you ? reach(t.self, t.opp) : reach(t.opp, t.self);
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
