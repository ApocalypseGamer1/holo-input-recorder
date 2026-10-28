package holorecorder.review;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import holorecorder.review.ReviewRecord.Fighter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Learned groups for the reviewed player's hits (move-model.json, trained on recorded player rounds by
 * .scratch/review/moves/train2.py): a small net embeds the ticks around a landed hit, the nearest centroid is its group.
 * Needs keys, so only the {@code self} side of a record. The window and channels must stay as movefeat.py computes them.
 */
public final class ReviewMoveModel {
    /** {@code sprintName} is shown when the server saw a sprint hit there; the net does not see the sprint flag. */
    public record Group(int id, String name, String sprintName, String description, boolean weak) {}

    /** One grouped hit: {@code hit} is the swing start tick index in {@code ReviewRecord.ticks}. */
    public record Hit(Group group, int hit) {}

    private static final String RESOURCE = "/holorecorder/review/move-model.json";
    private static ReviewMoveModel shared;
    private static boolean tried;

    final int before, after, fullCharge, hidden, embed, inputs;
    private final double[] mean, std, w1, b1, w2, b2, centroids;
    private final List<Group> groups = new ArrayList<>();

    ReviewMoveModel(JsonObject j) {
        before = j.get("before").getAsInt();
        after = j.get("after").getAsInt();
        fullCharge = j.get("fullCharge").getAsInt();
        hidden = j.get("hidden").getAsInt();
        embed = j.get("embed").getAsInt();
        if (j.getAsJsonArray("channels").size() != CHANNELS) throw new IllegalArgumentException("move model channels");
        mean = doubles(j, "mean");
        std = doubles(j, "std");
        w1 = doubles(j, "w1");
        b1 = doubles(j, "b1");
        w2 = doubles(j, "w2");
        b2 = doubles(j, "b2");
        centroids = doubles(j, "centroids");
        inputs = (before + after + 1) * CHANNELS + 1;
        int k = 0;
        for (var e : j.getAsJsonArray("groups")) {
            JsonObject g = e.getAsJsonObject();
            String name = g.get("name").getAsString();
            String sprint = g.has("sprintName") ? g.get("sprintName").getAsString() : name;
            groups.add(new Group(k++, name, sprint, g.get("description").getAsString(), g.get("weak").getAsBoolean()));
        }
        if (mean.length != inputs || w1.length != hidden * inputs || w2.length != embed * hidden || centroids.length != groups.size() * embed)
            throw new IllegalArgumentException("move model shapes");
    }

    /** The bundled model, or null when it cannot be read (then the review shows the rule names only). */
    public static synchronized ReviewMoveModel get() {
        if (!tried) {
            tried = true;
            try (InputStream in = ReviewMoveModel.class.getResourceAsStream(RESOURCE)) {
                if (in != null) shared = new ReviewMoveModel(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                shared = null;
            }
        }
        return shared;
    }

    public List<Group> groups() {
        return groups;
    }

    /** The reviewed player's landed hits with a full window, in tick order, each with its group. */
    public List<Hit> of(ReviewRecord r) {
        double[][] ch = channels(r);
        int n = ch.length;
        List<Hit> out = new ArrayList<>();
        int lastClick = -1;
        for (int s = 0; s < n; s++) {
            if (ch[s][HIT] > 0 && s >= before && s + after < n - 1) {
                int since = lastClick < 0 ? 99 : s - lastClick;
                out.add(new Hit(groups.get(group(window(ch, s, since))), s));
            }
            if (ch[s][CLICK] > 0) lastClick = s;
        }
        return out;
    }

    double[] window(double[][] ch, int s, int since) {
        double[] x = new double[inputs];
        int o = 0;
        for (int t = s - before; t <= s + after; t++) for (int c = 0; c < CHANNELS; c++) x[o++] = ch[t][c];
        x[o] = Math.min(since, fullCharge) / (double) fullCharge;
        return x;
    }

    double[] embed(double[] x) {
        double[] h = new double[hidden], z = new double[embed];
        for (int i = 0; i < hidden; i++) {
            double a = b1[i];
            for (int j = 0; j < inputs; j++) a += w1[i * inputs + j] * (x[j] - mean[j]) / std[j];
            h[i] = Math.tanh(a);
        }
        for (int i = 0; i < embed; i++) {
            double a = b2[i];
            for (int j = 0; j < hidden; j++) a += w2[i * hidden + j] * h[j];
            z[i] = a;
        }
        return z;
    }

    int group(double[] x) {
        double[] z = embed(x);
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < groups.size(); k++) {
            double d = 0;
            for (int i = 0; i < embed; i++) {
                double v = z[i] - centroids[k * embed + i];
                d += v * v;
            }
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    // Channels per tick: W S A D jump ground dy radial lateral dist hurt hit click (movefeat.CH without sprinting).
    static final int CHANNELS = 13, HIT = 11, CLICK = 12;

    static double[][] channels(ReviewRecord r) {
        int n = r.ticks.size();
        double[][] ch = new double[n][CHANNELS];
        for (int t = 0; t < n; t++) {
            Fighter a = r.ticks.get(t).self, o = r.ticks.get(t).opp;
            int k = t + 1 < n ? r.ticks.get(t + 1).keys : 0;
            double[] c = ch[t];
            for (int b = 0; b < 5; b++) c[b] = (k >> b) & 1;
            c[5] = a.onGround ? 1 : 0;
            double vx = 0, vz = 0, vy = 0;
            if (t > 0) {
                Fighter p = r.ticks.get(t - 1).self;
                vx = a.x - p.x;
                vz = a.z - p.z;
                vy = a.y - p.y;
            }
            double rx = o.x - a.x, rz = o.z - a.z, d = Math.hypot(rx, rz), rad = 0, lat = 0;
            if (d > 0.1) {
                rad = (vx * rx + vz * rz) / d;
                lat = (vx * rz - vz * rx) / d;
            }
            c[6] = clamp(vy / 0.5, -3, 3);
            c[7] = clamp(rad / 0.3, -3, 3);
            c[8] = clamp(lat / 0.3, -3, 3);
            c[9] = clamp(d / 3, 0, 4);
            c[10] = a.hurt ? 1 : 0;
            c[CLICK] = r.swingStart(t, true) ? 1 : 0;
        }
        for (int t = 0; t < n; t++) {
            if (!r.ticks.get(t).opp.hurt) continue;
            int s = ch[t][CLICK] > 0 ? t : t > 0 && ch[t - 1][CLICK] > 0 ? t - 1 : -1;
            if (s >= 0) ch[s][HIT] = 1;
        }
        return ch;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, (float) v));
    }

    private static double[] doubles(JsonObject j, String key) {
        JsonArray a = j.getAsJsonArray(key);
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }
}
