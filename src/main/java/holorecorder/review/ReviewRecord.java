package holorecorder.review;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One sword round as the reviewed player lived it: both fighters every server tick, the win chance from the player's side,
 * the coach model's action distribution at the player's state and the player's own keys. The server writes the same
 * bytes (holoserver.review.ReviewRecord); change both together and bump {@link #VERSION}.
 *
 * <p>Gzip of big-endian: "HRV1", int version, header (see {@link #write}), int ticks, then one {@link Tick} per server tick.
 */
public final class ReviewRecord {
    public static final int VERSION = 1;
    public static final int OUTCOME_LOSS = 0, OUTCOME_WIN = 1, OUTCOME_DRAW = 2, OUTCOME_CUT = 3;
    /** {@link Fighter#swingTime} when the arm is not swinging. */
    public static final byte NOT_SWINGING = 127;

    public String roundId = "", mode = "sword", arenaId = "";
    public long startMs;
    public String selfName = "", selfUuid = "", selfSkin = "", selfSkinSig = "";
    public String oppName = "", oppUuid = "", oppSkin = "", oppSkinSig = "";
    public int outcome = OUTCOME_CUT;
    public String coachName = "", coachSha = "";
    /** Coach action branches (sword: move 9, jump, sprint, attack, yaw, pitch, sneak) and its turn buckets in degrees. */
    public int[] branches = new int[0];
    public float[] yawBuckets = new float[0], pitchBuckets = new float[0];
    /** Main hand, off hand, feet, legs, chest, head item ids ("" = empty), as the round started. */
    public String[] selfItems = empty(), oppItems = empty();
    public final List<Tick> ticks = new ArrayList<>();

    public static final class Fighter {
        public double x, y, z;
        public float yaw, pitch, health;
        public boolean onGround, sprinting, sneaking;
        /** Hit this tick (hurtTime just set to 10). */
        public boolean hurt;
        /** The swing counter as the observation reads it: -1 the tick a swing starts, up to 6; {@link #NOT_SWINGING} otherwise. */
        public byte swingTime = NOT_SWINGING;

        void write(DataOutputStream out) throws IOException {
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yaw);
            out.writeFloat(pitch);
            out.writeFloat(health);
            out.writeByte((onGround ? 1 : 0) | (sprinting ? 2 : 0) | (sneaking ? 4 : 0) | (hurt ? 8 : 0));
            out.writeByte(swingTime);
        }

        static Fighter read(DataInputStream in) throws IOException {
            Fighter f = new Fighter();
            f.x = in.readDouble();
            f.y = in.readDouble();
            f.z = in.readDouble();
            f.yaw = in.readFloat();
            f.pitch = in.readFloat();
            f.health = in.readFloat();
            int flags = in.readUnsignedByte();
            f.onGround = (flags & 1) != 0;
            f.sprinting = (flags & 2) != 0;
            f.sneaking = (flags & 4) != 0;
            f.hurt = (flags & 8) != 0;
            f.swingTime = in.readByte();
            return f;
        }
    }

    public static final class Tick {
        public int tick;
        public Fighter self = new Fighter(), opp = new Fighter();
        /** Raw model chance that the reviewed player wins; NaN when the bar had no value this tick. */
        public float pSelf = Float.NaN;
        /** Coach probabilities per branch, in branch order (each 0..255 = 0..1), or null when the coach did not run. */
        public byte[] coach;
        /** The player's keys this tick: forward, backward, left, right, jump, shift, sprint as bits 0..6. */
        public int keys;
    }

    private static String[] empty() {
        return new String[] {"", "", "", "", "", ""};
    }

    /** A swing starts at tick {@code i}: swinging now, and not before or the swing restarted (live players show -1, bots 0). */
    public boolean swingStart(int i, boolean self) {
        Fighter now = self ? ticks.get(i).self : ticks.get(i).opp;
        if (now.swingTime == NOT_SWINGING) return false;
        if (i == 0) return true;
        Fighter before = self ? ticks.get(i - 1).self : ticks.get(i - 1).opp;
        return before.swingTime == NOT_SWINGING || now.swingTime < before.swingTime;
    }

    public int coachWidth() {
        int n = 0;
        for (int b : branches) n += b;
        return n;
    }

    /** Probability of {@code index} in branch {@code branch} at a tick (0 when the coach did not run). */
    public double coachP(Tick t, int branch, int index) {
        if (t.coach == null) return 0;
        int off = 0;
        for (int b = 0; b < branch; b++) off += branches[b];
        return (t.coach[off + index] & 0xFF) / 255.0;
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            write(out);
        }
        return bytes.toByteArray();
    }

    private void write(DataOutputStream out) throws IOException {
        out.writeBytes("HRV1");
        out.writeInt(VERSION);
        for (String s : new String[] {roundId, mode, arenaId}) out.writeUTF(s);
        out.writeLong(startMs);
        for (String s : new String[] {selfName, selfUuid, selfSkin, selfSkinSig, oppName, oppUuid, oppSkin, oppSkinSig}) out.writeUTF(s);
        out.writeByte(outcome);
        out.writeUTF(coachName);
        out.writeUTF(coachSha);
        out.writeByte(branches.length);
        for (int b : branches) out.writeShort(b);
        out.writeShort(yawBuckets.length);
        for (float v : yawBuckets) out.writeFloat(v);
        out.writeShort(pitchBuckets.length);
        for (float v : pitchBuckets) out.writeFloat(v);
        for (String s : selfItems) out.writeUTF(s);
        for (String s : oppItems) out.writeUTF(s);
        int width = coachWidth();
        out.writeInt(ticks.size());
        for (Tick t : ticks) {
            out.writeInt(t.tick);
            t.self.write(out);
            t.opp.write(out);
            out.writeFloat(t.pSelf);
            out.writeByte(t.keys);
            boolean coach = t.coach != null && t.coach.length == width;
            out.writeBoolean(coach);
            if (coach) out.write(t.coach);
        }
    }

    public static ReviewRecord fromBytes(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"HRV1".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) throw new IOException("not a review record");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("review record version " + version + ", this build reads " + VERSION);
            ReviewRecord r = new ReviewRecord();
            r.roundId = in.readUTF();
            r.mode = in.readUTF();
            r.arenaId = in.readUTF();
            r.startMs = in.readLong();
            r.selfName = in.readUTF();
            r.selfUuid = in.readUTF();
            r.selfSkin = in.readUTF();
            r.selfSkinSig = in.readUTF();
            r.oppName = in.readUTF();
            r.oppUuid = in.readUTF();
            r.oppSkin = in.readUTF();
            r.oppSkinSig = in.readUTF();
            r.outcome = in.readUnsignedByte();
            r.coachName = in.readUTF();
            r.coachSha = in.readUTF();
            r.branches = new int[in.readUnsignedByte()];
            for (int i = 0; i < r.branches.length; i++) r.branches[i] = in.readUnsignedShort();
            r.yawBuckets = new float[in.readUnsignedShort()];
            for (int i = 0; i < r.yawBuckets.length; i++) r.yawBuckets[i] = in.readFloat();
            r.pitchBuckets = new float[in.readUnsignedShort()];
            for (int i = 0; i < r.pitchBuckets.length; i++) r.pitchBuckets[i] = in.readFloat();
            for (int i = 0; i < 6; i++) r.selfItems[i] = in.readUTF();
            for (int i = 0; i < 6; i++) r.oppItems[i] = in.readUTF();
            int width = r.coachWidth();
            int n = in.readInt();
            if (n < 0 || n > 20 * 60 * 30) throw new IOException("bad tick count " + n);
            for (int i = 0; i < n; i++) {
                Tick t = new Tick();
                t.tick = in.readInt();
                t.self = Fighter.read(in);
                t.opp = Fighter.read(in);
                t.pSelf = in.readFloat();
                t.keys = in.readUnsignedByte();
                if (in.readBoolean()) {
                    t.coach = new byte[width];
                    in.readFully(t.coach);
                }
                r.ticks.add(t);
            }
            return r;
        }
    }
}
