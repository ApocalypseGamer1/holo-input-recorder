package holorecorder.review;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The review controls over the replay: win chance graph with marks, play/scrub/speed, your keys against the bot's. */
final class ReviewScreen extends Screen {
    private static final int LIGHT = 0xFFEDEDED, DARK = 0xFF3A3A3A, OUTLINE = 0xFF101010, PANEL = 0xB0000000;
    private static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFB0B0B0, COACH = 0xFF4FD8FF, ON = 0xFF81B64C, OFF = 0x60FFFFFF;
    private static final double[] SPEEDS = {0.25, 0.5, 1};
    private static final double HIT_MARK_TICKS = 6, GOT_HIT_TICKS = 10;

    private record Button(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private int graphX0, graphX1, graphY0, graphY1;
    private boolean scrubbing;

    ReviewScreen() {
        super(Component.literal("Round review"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void removed() {
        ReviewClient.stop(minecraft, true);
    }

    static int color(ReviewAnalysis.Kind k) {
        return switch (k) {
            case BLUNDER -> 0xFFCA3431;
            case MISTAKE -> 0xFFE58F2A;
            case INACCURACY -> 0xFFF7C631;
            case BEST -> 0xFF81B64C;
            case MISS -> 0xFFEE6B9E;
        };
    }

    static String symbol(ReviewAnalysis.Kind k) {
        return switch (k) {
            case BLUNDER -> "??";
            case MISTAKE -> "?";
            case INACCURACY -> "?!";
            case BEST -> "!";
            case MISS -> "x";
        };
    }

    static String label(ReviewAnalysis.Kind k) {
        return switch (k) {
            case BLUNDER -> "Blunder";
            case MISTAKE -> "Mistake";
            case INACCURACY -> "Inaccuracy";
            case BEST -> "Best";
            case MISS -> "Miss";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        ReviewRecord r = ReviewClient.record;
        ReviewAnalysis a = ReviewClient.analysis;
        if (r == null || a == null || !ReviewClient.active()) return;
        buttons.clear();
        Font f = font;
        int w = width, h = height, n = ReviewClient.ticks(), i = (int) Math.floor(ReviewClient.cursor);
        float shown = a.shown.length == 0 ? 0.5f : a.shown[Math.max(0, Math.min(a.shown.length - 1, i))];

        boolean clip = ReviewClient.inClip();
        drawGotHit(g, f, w, h);
        if (clip) {
            drawCompare(g, f, h);
            drawDodged(g, f, w, h);
        } else {
            drawBar(g, f, h, shown, r);
        }
        drawMoveNow(g, f, w, h, clip && ReviewClient.showingSwordbot);
        drawAim(g, w, h, a.suggestion(i));
        if (clip) drawClipHeader(g, f, w, r);
        else drawHeader(g, f, w, r, a);
        drawKeys(g, f, w, h, r, a, i);
        if (!clip) drawCause(g, f, w, h, a, i);
        String note = ReviewClient.clipNote();
        if (note != null) {
            int nw = f.width(note);
            g.fill(w / 2 - nw / 2 - 4, 40, w / 2 + nw / 2 + 4, 52, PANEL);
            g.centeredText(f, note, w / 2, 42, COACH);
        }

        int panelY = h - 84;
        g.fill(0, panelY, w, h, PANEL);
        graphX0 = 24;
        graphX1 = w - 24;
        graphY0 = panelY + 26;
        graphY1 = panelY + 54;
        if (clip) {
            drawTimeline(g, n);
            drawClipControls(g, f, w, h - 24, n);
        } else {
            drawGraph(g, f, a, n, mouseX, mouseY);
            drawControls(g, f, w, h - 24, n);
        }
    }

    private void drawBar(GuiGraphicsExtractor g, Font f, int guiH, float pSelf, ReviewRecord r) {
        int barH = Math.max(40, Math.round(guiH * 0.40f)), y0 = (guiH - 84 - barH) / 2, x0 = 3, x1 = 13;
        int split = y0 + Math.round(barH * (1f - pSelf));
        g.fill(x0 - 1, y0 - 1, x1 + 1, y0 + barH + 1, OUTLINE);
        g.fill(x0, y0, x1, split, DARK);
        g.fill(x0, split, x1, y0 + barH, LIGHT);
        g.fill(x0, y0 + barH / 2, x1, y0 + barH / 2 + 1, 0xFF8A8A8A);
        String pct = Math.round(pSelf * 100) + "%";
        g.text(f, pct, x1 + 3, pSelf >= 0.5f ? y0 + barH - f.lineHeight : y0, TEXT, true);
        g.text(f, f.plainSubstrByWidth(r.oppName, 60), x0, y0 - 2 - f.lineHeight, MUTED, true);
        g.text(f, "You", x0, y0 + barH + 3, TEXT, true);
    }

    /** Where the bot would aim: a ring at that direction, an X when it would click; the crosshair is where you aimed. */
    private void drawAim(GuiGraphicsExtractor g, int w, int h, ReviewAnalysis.Suggestion s) {
        int cx = w / 2, cy = h / 2;
        double hit = ReviewClient.sinceYouHit();
        int cross = hit < HIT_MARK_TICKS ? 0xFFFF4040 : 0xC0FFFFFF;
        g.fill(cx - 4, cy, cx + 5, cy + 1, cross);
        g.fill(cx, cy - 4, cx + 1, cy + 5, cross);
        if (hit < HIT_MARK_TICKS) {
            int alpha = (int) Math.round(255 * (1 - hit / HIT_MARK_TICKS)) << 24;
            for (int d = 3; d <= 6; d++) {
                g.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, alpha | 0xFFFFFF);
                g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, alpha | 0xFFFFFF);
                g.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, alpha | 0xFFFFFF);
                g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, alpha | 0xFFFFFF);
            }
        }
        if (s == null || ReviewClient.coachView) return;
        double[] off = ReviewClient.coachOffset();
        double fov = Math.toRadians(minecraft.gameRenderer.mainCamera().getFov());
        double scale = (h / 2.0) / Math.tan(fov / 2);
        int px = cx + (int) Math.round(Math.tan(Math.toRadians(Math.max(-80, Math.min(80, off[0])))) * scale);
        int py = cy + (int) Math.round(Math.tan(Math.toRadians(Math.max(-80, Math.min(80, off[1])))) * scale);
        px = Math.max(6, Math.min(w - 6, px));
        py = Math.max(6, Math.min(h - 90, py));
        if (s.attackP() >= 0.5) {
            for (int d = -5; d <= 5; d++) {
                g.fill(px + d, py + d, px + d + 1, py + d + 1, COACH);
                g.fill(px + d, py - d, px + d + 1, py - d + 1, COACH);
                g.fill(px + d + 1, py + d, px + d + 2, py + d + 1, 0x804FD8FF);
                g.fill(px + d + 1, py - d, px + d + 2, py - d + 1, 0x804FD8FF);
            }
        } else {
            g.outline(px - 5, py - 5, 11, 11, COACH);
            g.outline(px - 4, py - 4, 9, 9, 0x804FD8FF);
        }
    }

    /** The bot hit you: red edges and the hearts lost, fading out over the hurt time. */
    private void drawGotHit(GuiGraphicsExtractor g, Font f, int w, int h) {
        double since = ReviewClient.sinceYouGotHit();
        if (since >= GOT_HIT_TICKS) return;
        double k = 1 - since / GOT_HIT_TICKS;
        for (int band = 0; band < 8; band++) {
            int a = (int) Math.round(110 * k * (8 - band) / 8.0) << 24 | 0xC00000;
            int e = band * 3;
            g.fill(e, e, w - e, e + 3, a);
            g.fill(e, h - e - 3, w - e, h - e, a);
            g.fill(e, e + 3, e + 3, h - e - 3, a);
            g.fill(w - e - 3, e + 3, w - e, h - e - 3, a);
        }
        float hearts = ReviewClient.lastHitHearts();
        String text = (ReviewClient.inClip() && ReviewClient.showingSwordbot ? "Bot hit Swordbot" : "Bot hit you") + (hearts > 0 ? String.format("  -%.1f\u2764", hearts) : "");
        g.centeredText(f, text, w / 2, h / 2 - 22, (int) Math.round(255 * k) << 24 | 0xFF5050);
    }

    /** In a clip: Swordbot was not hit where you were. */
    private void drawDodged(GuiGraphicsExtractor g, Font f, int w, int h) {
        double since = ReviewClient.sinceDodged();
        if (since >= 2 * GOT_HIT_TICKS) return;
        double k = Math.min(1, 2 - since / GOT_HIT_TICKS);
        String text = "Swordbot dodged a hit you took";
        int tw = f.width(text), cx = (w - 136) / 2;   // left of the key panel
        g.fill(cx - tw / 2 - 3, 26, cx + tw / 2 + 3, 38, (int) Math.round(0xB0 * k) << 24);
        g.centeredText(f, text, cx, 28, (int) Math.round(255 * k) << 24 | 0x9BE060);
    }

    /** What caused the mark the cursor is in: shown while its moment replays. */
    private void drawCause(GuiGraphicsExtractor g, Font f, int w, int h, ReviewAnalysis a, int i) {
        ReviewAnalysis.Mark m = a.markAt(i);
        if (m == null) return;
        String title = symbol(m.kind()) + " " + label(m.kind());
        if (m.kind() != ReviewAnalysis.Kind.MISS) {
            int hi = Math.min(a.shown.length - 1, m.to());
            if (hi >= 0) title += String.format(":  win %d%% -> %d%%", Math.round(a.shown[Math.max(0, m.from())] * 100), Math.round(a.shown[hi] * 100));
        }
        if (m.forced()) title += " (hard to avoid)";
        java.util.List<String> lines = a.explain(m);
        int bw = f.width(title);
        for (String l : lines) bw = Math.max(bw, f.width(l));
        boolean clip = m.kind() != ReviewAnalysis.Kind.BEST && ReviewClient.clipsAvailable();
        String play = ReviewClient.clipPending() ? "Swordbot is playing..." : "\u25B6 Swordbot's play from here";
        if (clip) bw = Math.max(bw, f.width(play) + 8);
        int x0 = 46, y1 = h - 92, y0 = y1 - 14 - lines.size() * 10 - (clip ? 18 : 0);
        g.fill(x0 - 4, y0 - 4, x0 + bw + 4, y1, PANEL);
        g.fill(x0 - 4, y0 - 4, x0 - 2, y1, color(m.kind()));
        g.text(f, title, x0, y0, color(m.kind()), true);
        for (int k = 0; k < lines.size(); k++) g.text(f, lines.get(k), x0, y0 + 13 + k * 10, TEXT, true);
        if (clip) button(g, f, x0, y1 - 18, play, () -> ReviewClient.requestClip(clipStart(a)), ReviewClient.clipPending());
    }

    /** Where Swordbot's play starts: the start of the mark the cursor is in, else the cursor. */
    static int clipStart(ReviewAnalysis a) {
        int i = (int) Math.floor(ReviewClient.cursor);
        ReviewAnalysis.Mark m = a.markAt(i);
        return m == null || m.kind() == ReviewAnalysis.Kind.BEST ? i : Math.min(m.from(), m.index());
    }

    private void drawClipHeader(GuiGraphicsExtractor g, Font f, int w, ReviewRecord r) {
        String title = (ReviewClient.showingSwordbot ? "2/2  Swordbot's play" : "1/2  Your play") + " from " + clock(ReviewClient.clipFrom);
        String sub = ReviewClient.showingSwordbot ? "Swordbot plays in your place. " + r.oppName + " fights back live."
                : "What you did. Next: Swordbot from the same moment.";
        int tw = Math.max(f.width(title), f.width(sub));
        g.fill(w / 2 - tw / 2 - 4, 2, w / 2 + tw / 2 + 4, 14 + f.lineHeight, PANEL);
        g.centeredText(f, title, w / 2, 4, ReviewClient.showingSwordbot ? COACH : TEXT);
        g.centeredText(f, sub, w / 2, 5 + f.lineHeight, MUTED);
    }

    private void drawHeader(GuiGraphicsExtractor g, Font f, int w, ReviewRecord r, ReviewAnalysis a) {
        String result = switch (r.outcome) {
            case ReviewRecord.OUTCOME_WIN -> "Win";
            case ReviewRecord.OUTCOME_LOSS -> "Loss";
            case ReviewRecord.OUTCOME_DRAW -> "Draw";
            default -> "Unfinished";
        };
        String title = "Round review: " + result + " vs " + r.oppName;
        double acc = a.accuracy();
        if (!Double.isNaN(acc)) title += "   Accuracy " + Math.round(acc) + "%";
        int tw = f.width(title);
        g.fill(w / 2 - tw / 2 - 4, 2, w / 2 + tw / 2 + 4, 14 + f.lineHeight, PANEL);
        g.centeredText(f, title, w / 2, 4, TEXT);
        int total = 0;
        for (ReviewAnalysis.Kind k : ReviewAnalysis.Kind.values()) total += f.width(symbol(k) + " " + a.count(k)) + 8;
        int x = w / 2 - total / 2;
        int y = 5 + f.lineHeight;
        for (ReviewAnalysis.Kind k : ReviewAnalysis.Kind.values()) {
            String p = symbol(k) + " " + a.count(k);
            g.text(f, p, x, y, color(k), true);
            x += f.width(p) + 8;
        }
        if (r.coachName.isEmpty()) g.centeredText(f, "No bot data for this round: marks use the win chance only.", w / 2, y + 12, MUTED);
    }

    /** Your keys this moment against the bot's choice for the same moment, and the nearest mark's note. */
    private void drawKeys(GuiGraphicsExtractor g, Font f, int w, int h, ReviewRecord r, ReviewAnalysis a, int i) {
        int x0 = w - 132, y0 = Math.max(30, (h - 84) / 2 - 56);
        g.fill(x0 - 4, y0 - 4, w - 2, y0 + 104, PANEL);
        ReviewAnalysis.Suggestion s = a.suggestion(i);
        int next = Math.min(r.ticks.size() - 1, i + 1);
        ReviewRecord.Tick you = r.ticks.get(next);
        int k = you.keys;
        boolean fwd = (k & 1) != 0 && (k & 2) == 0, back = (k & 2) != 0 && (k & 1) == 0;
        boolean left = (k & 4) != 0 && (k & 8) == 0, right = (k & 8) != 0 && (k & 4) == 0;
        boolean clip = ReviewClient.inClip();
        boolean bot = clip && ReviewClient.showingSwordbot;
        g.text(f, bot ? "Swordbot pressed" : clip ? "You pressed" : "You", x0, y0, bot ? COACH : TEXT, true);
        boolean[] youKeys = {fwd, left, back, right, (k & 16) != 0, (k & 64) != 0, r.swingStart(next, true)};
        keyRow(g, f, x0, y0 + 11, youKeys, null, bot ? COACH : ON);
        if (clip) {
            g.text(f, f.plainSubstrByWidth(r.oppName, 124), x0, y0 + 30, MUTED, true);
            g.text(f, bot ? "fights back live" : "as in your round", x0, y0 + 41, MUTED, true);
        } else if (s == null) {
            g.text(f, "Swordbot would", x0, y0 + 30, COACH, true);
            g.text(f, "no data here", x0, y0 + 41, MUTED, true);
        } else {
            g.text(f, "Swordbot would", x0, y0 + 30, COACH, true);
            boolean[] bots = {s.forward(), s.left(), s.back(), s.right(), s.jump(), s.sprint(), s.attackP() >= 0.5};
            keyRow(g, f, x0, y0 + 41, bots, youKeys, COACH);
            String turn = turnText(s.yawDeg(), s.pitchDeg());
            g.text(f, "click " + Math.round(s.attackP() * 100) + "%  " + turn, x0, y0 + 58, MUTED, true);
        }
        ReviewRecord.Tick now = r.ticks.get(Math.max(0, i));
        if (clip) g.text(f, (bot ? "Swordbot HP " : "Your HP ") + hp(now.self.health), x0, y0 + 70, MUTED, true);
        else g.text(f, "HP you " + hp(now.self.health) + "  bot " + hp(now.opp.health), x0, y0 + 70, MUTED, true);
        double now2 = ReviewClient.rangeNow(), yours = ReviewClient.rangeAtYourHit(), bots = ReviewClient.rangeAtBotHit();
        g.text(f, String.format("Range now %.1f", now2), x0, y0 + 81, now2 <= 3.0 ? ON : MUTED, true);
        if (clip) g.text(f, "Hits " + ReviewClient.hitsSoFar(true) + "  taken " + ReviewClient.hitsSoFar(false), x0, y0 + 92, MUTED, true);
        else g.text(f, "Your hit " + range(yours) + "  bot's " + range(bots), x0, y0 + 92, MUTED, true);
    }

    private static String range(double v) {
        return Double.isNaN(v) ? "-" : String.format("%.1f", v);
    }

    private static String hp(float v) {
        return String.format("%.1f", v / 2f) + "❤";
    }

    private static String turnText(double yaw, double pitch) {
        String t = Math.abs(yaw) < 1 ? "" : (yaw > 0 ? "turn right " : "turn left ") + Math.round(Math.abs(yaw)) + "°";
        if (Math.abs(pitch) >= 1) t += (t.isEmpty() ? "" : ", ") + (pitch > 0 ? "down " : "up ") + Math.round(Math.abs(pitch)) + "°";
        return t.isEmpty() ? "hold aim" : t;
    }

    /** One key row; where {@code other} is given, keys that differ from it get a yellow frame. */
    private static void keyRow(GuiGraphicsExtractor g, Font f, int x, int y, boolean[] lit, boolean[] other, int on) {
        String[] names = {"W", "A", "S", "D", "Jump", "Run", "Hit"};
        for (int i = 0; i < names.length; i++) {
            int bw = f.width(names[i]) + 5;
            g.fill(x, y, x + bw, y + 13, lit[i] ? on : OFF);
            g.text(f, names[i], x + 3, y + 3, lit[i] ? 0xFF101010 : 0xFFE0E0E0, false);
            if (other != null && other[i] != lit[i]) g.outline(x - 1, y - 1, bw + 2, 15, 0xFFFFD84F);
            x += bw + 2;
        }
    }

    private void drawGraph(GuiGraphicsExtractor g, Font f, ReviewAnalysis a, int n, int mouseX, int mouseY) {
        int x0 = graphX0, x1 = graphX1, y0 = graphY0, y1 = graphY1, gw = x1 - x0;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, OUTLINE);
        for (int x = x0; x < x1; x++) {
            int t = n <= 1 ? 0 : (int) Math.round((x - x0) / (double) (gw - 1) * (n - 1));
            float v = a.shown.length == 0 ? 0.5f : a.shown[Math.min(a.shown.length - 1, t)];
            int split = y0 + Math.round((y1 - y0) * (1f - v));
            g.fill(x, y0, x + 1, split, DARK);
            g.fill(x, split, x + 1, y1, LIGHT);
        }
        g.fill(x0, (y0 + y1) / 2, x1, (y0 + y1) / 2 + 1, 0x808A8A8A);
        for (ReviewAnalysis.Mark m : a.marks()) {
            int mx = tickX(m.index(), n);
            g.fill(mx, y0, mx + 1, y1, color(m.kind()) & 0x90FFFFFF);
            int bw = f.width(symbol(m.kind())) + 4;
            g.fill(mx - bw / 2, y0 - 12, mx - bw / 2 + bw, y0 - 2, color(m.kind()));
            g.text(f, symbol(m.kind()), mx - bw / 2 + 2, y0 - 11, 0xFF101010, false);
            buttons.add(new Button(mx - bw / 2, y0 - 12, bw, 10, () -> playMark(m)));
        }
        int cx = tickX(ReviewClient.cursor, n);
        g.fill(cx - 1, y0 - 2, cx + 1, y1 + 2, 0xFF4FD8FF);
    }

    /** Swordbot's play has no win chance: a plain time line. */
    private void drawTimeline(GuiGraphicsExtractor g, int n) {
        int y0 = graphY0 + 8, y1 = graphY1 - 8;
        g.fill(graphX0 - 1, y0 - 1, graphX1 + 1, y1 + 1, OUTLINE);
        g.fill(graphX0, y0, graphX1, y1, DARK);
        int cx = tickX(ReviewClient.cursor, n);
        g.fill(graphX0, y0, cx, y1, 0xFF2A6F86);
        g.fill(cx - 1, y0 - 4, cx + 1, y1 + 4, COACH);
    }

    /** The move the shown fighter makes now, under the crosshair. */
    private void drawMoveNow(GuiGraphicsExtractor g, Font f, int w, int h, boolean bot) {
        ReviewMoves.Move m = ReviewClient.moveNow();
        if (m == null) return;
        String text = (bot ? "Swordbot: " : "You: ") + ReviewClient.moveName(m.kind());
        int tw = f.width(text), y = h / 2 + 12;
        g.fill(w / 2 - tw / 2 - 3, y - 2, w / 2 + tw / 2 + 3, y + 10, PANEL);
        g.centeredText(f, text, w / 2, y, m.kind() == ReviewMoves.Kind.WEAK_HIT ? 0xFFF7C631 : bot ? COACH : ON);
    }

    /** Your play against Swordbot's over the same stretch. */
    private void drawCompare(GuiGraphicsExtractor g, Font f, int h) {
        double[] you = ReviewClient.tally(false), bot = ReviewClient.tally(true);
        String[][] rows = {
            {"Hits landed", String.valueOf((int) you[0]), String.valueOf((int) bot[0])},
            {"Hits taken", String.valueOf((int) you[1]), String.valueOf((int) bot[1])},
            {"Hearts lost", String.format("%.1f", you[2]), String.format("%.1f", bot[2])},
        };
        String youMoves = "You: " + summary(ReviewClient.movesOf(false)), botMoves = "Swordbot: " + summary(ReviewClient.movesOf(true));
        int x0 = 46, colA = x0 + 70, colB = x0 + 110, y1 = h - 92, lines = rows.length + 3;
        int bw = Math.max(150, Math.max(f.width(youMoves), f.width(botMoves)));
        int y0 = y1 - 4 - lines * 10;
        g.fill(x0 - 4, y0 - 4, x0 + bw + 4, y1, PANEL);
        g.text(f, "Same moment", x0, y0, MUTED, true);
        g.text(f, "You", colA, y0, ReviewClient.showingSwordbot ? MUTED : TEXT, true);
        g.text(f, "Swordbot", colB, y0, ReviewClient.showingSwordbot ? COACH : MUTED, true);
        for (int k = 0; k < rows.length; k++) {
            int y = y0 + 11 + k * 10;
            g.text(f, rows[k][0], x0, y, TEXT, true);
            g.text(f, rows[k][1], colA, y, TEXT, true);
            g.text(f, rows[k][2], colB, y, COACH, true);
        }
        g.text(f, youMoves, x0, y0 + 13 + rows.length * 10, TEXT, true);
        g.text(f, botMoves, x0, y0 + 23 + rows.length * 10, COACH, true);
    }

    /** "Sprint hit x2, W-tap": hit and tap moves by count, strafes as one word. */
    private static String summary(List<ReviewMoves.Move> moves) {
        java.util.Map<ReviewMoves.Kind, Integer> count = new java.util.EnumMap<>(ReviewMoves.Kind.class);
        for (ReviewMoves.Move m : moves) count.merge(m.kind(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        for (var e : count.entrySet()) {
            String name = ReviewClient.moveName(e.getKey());
            parts.add(e.getValue() > 1 ? name + " x" + e.getValue() : name);
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private void drawClipControls(GuiGraphicsExtractor g, Font f, int w, int y, int n) {
        int x = graphX0;
        x = button(g, f, x, y, "Back to your round", ReviewClient::endClip);
        x += 8;
        x = button(g, f, x, y, "Your play", () -> ReviewClient.show(false), !ReviewClient.showingSwordbot);
        x = button(g, f, x, y, "Swordbot's play", () -> ReviewClient.show(true), ReviewClient.showingSwordbot);
        x += 8;
        x = button(g, f, x, y, "Start", () -> ReviewClient.seek(0));
        x = button(g, f, x, y, ReviewClient.playing ? "Pause" : "Play", ReviewScreen::togglePlay);
        x += 8;
        for (double s : SPEEDS) {
            String label = (s == 1 ? "1" : s == 0.5 ? "0.5" : "0.25") + "x";
            x = button(g, f, x, y, label, () -> ReviewClient.speed = s, ReviewClient.speed == s);
        }
        int closeX = w - 24 - f.width("Close") - 8;
        button(g, f, closeX, y, "Close", this::onClose);
        String time = clock(ReviewClient.clipFrom + ReviewClient.cursor) + "   " + clock(ReviewClient.cursor) + " / " + clock(Math.max(0, n - 1));
        g.text(f, time, graphX1 - f.width(time), graphY0 - 25, TEXT, true);
        String help = "Tab switch  Esc back";
        if (x + 16 + f.width(help) < closeX - 8) g.text(f, help, x + 16, y + 4, MUTED, true);
    }

    private int tickX(double tick, int n) {
        return graphX0 + (int) Math.round(n <= 1 ? 0 : tick / (n - 1) * (graphX1 - graphX0 - 1));
    }

    private static String clock(double ticks) {
        double s = ticks / 20.0;
        return String.format("%d:%04.1f", (int) (s / 60), s % 60);
    }

    private void drawControls(GuiGraphicsExtractor g, Font f, int w, int y, int n) {
        int x = graphX0;
        x = button(g, f, x, y, "Start", () -> ReviewClient.seek(0));
        x = button(g, f, x, y, "<", () -> stepMark(-1));
        x = button(g, f, x, y, ReviewClient.playing ? "Pause" : "Play", ReviewScreen::togglePlay);
        x = button(g, f, x, y, ">", () -> stepMark(1));
        x += 8;
        for (double s : SPEEDS) {
            String label = (s == 1 ? "1" : s == 0.5 ? "0.5" : "0.25") + "x";
            x = button(g, f, x, y, label, () -> ReviewClient.speed = s, ReviewClient.speed == s);
        }
        x += 8;
        x = button(g, f, x, y, "Bot aim", () -> ReviewClient.coachView = !ReviewClient.coachView, ReviewClient.coachView);
        x = button(g, f, x, y, "Stop at marks", () -> ReviewClient.stopAtMarks = !ReviewClient.stopAtMarks, ReviewClient.stopAtMarks);
        int closeX = w - 24 - f.width("Close") - 8;
        button(g, f, closeX, y, "Close", this::onClose);
        String time = clock(ReviewClient.cursor) + " / " + clock(Math.max(0, n - 1));
        g.text(f, time, graphX1 - f.width(time), graphY0 - 25, TEXT, true);
        String help = "Space play  ←→ step  ↑↓ marks  B bot view" + (ReviewClient.clipsAvailable() ? "  P Swordbot's play" : "");
        if (x + 16 + f.width(help) < closeX - 8) g.text(f, help, x + 16, y + 4, MUTED, true);
    }

    private int button(GuiGraphicsExtractor g, Font f, int x, int y, String label, Runnable action) {
        return button(g, f, x, y, label, action, false);
    }

    private int button(GuiGraphicsExtractor g, Font f, int x, int y, String label, Runnable action, boolean selected) {
        int bw = f.width(label) + 8, bh = 16;
        g.fill(x, y, x + bw, y + bh, selected ? 0xFF4FD8FF : 0xFF505050);
        g.outline(x, y, bw, bh, 0xFF202020);
        g.text(f, label, x + 4, y + 4, selected ? 0xFF101010 : TEXT, false);
        buttons.add(new Button(x, y, bw, bh, action));
        return x + bw + 3;
    }

    private static void togglePlay() {
        if (!ReviewClient.playing && ReviewClient.cursor >= ReviewClient.ticks() - 1) ReviewClient.seek(0);
        ReviewClient.playing = !ReviewClient.playing;
        ReviewClient.stopAt = -1;
    }

    /** Replay the whole moment from a second before it, in slow motion, holding at the key tick. */
    private static void playMark(ReviewAnalysis.Mark m) {
        ReviewClient.seek(Math.max(0, Math.min(m.from(), m.index()) - 20));
        ReviewClient.speed = 0.5;
        ReviewClient.playing = true;
        ReviewClient.stopAt = m.index();
    }

    private static void stepMark(int dir) {
        List<ReviewAnalysis.Mark> marks = ReviewClient.analysis.marks();
        int at = (int) Math.round(ReviewClient.cursor);
        ReviewAnalysis.Mark pick = null;
        for (ReviewAnalysis.Mark m : marks) {
            if (dir > 0 && m.index() > at && (pick == null || m.index() < pick.index())) pick = m;
            if (dir < 0 && m.index() < at - 2 && (pick == null || m.index() > pick.index())) pick = m;
        }
        if (pick != null) playMark(pick);
    }

    private void scrubTo(double mx) {
        int n = ReviewClient.ticks();
        double f = (mx - graphX0) / (double) Math.max(1, graphX1 - graphX0 - 1);
        ReviewClient.seek(Math.max(0, Math.min(1, f)) * (n - 1));
        ReviewClient.playing = false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != 0) return super.mouseClicked(e, doubleClick);
        for (Button b : List.copyOf(buttons)) {
            if (b.hit(e.x(), e.y())) {
                b.action.run();
                return true;
            }
        }
        if (e.x() >= graphX0 && e.x() < graphX1 && e.y() >= graphY0 - 2 && e.y() <= graphY1 + 2) {
            scrubbing = true;
            scrubTo(e.x());
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (scrubbing) {
            scrubTo(e.x());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        scrubbing = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        ReviewClient.playing = false;
        ReviewClient.seek(Math.round(ReviewClient.cursor) - Math.signum(v) * (hasShift() ? 20 : 1));
        return true;
    }

    private static boolean hasShift() {
        return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int step = hasShift() ? 20 : 1;
        if (ReviewClient.inClip() && e.key() == InputConstants.KEY_ESCAPE) {
            ReviewClient.endClip();
            return true;
        }
        if (ReviewClient.inClip() && e.key() == InputConstants.KEY_TAB) {
            ReviewClient.show(!ReviewClient.showingSwordbot);
            return true;
        }
        switch (e.key()) {
            case InputConstants.KEY_SPACE -> togglePlay();
            case InputConstants.KEY_RIGHT -> {
                ReviewClient.playing = false;
                ReviewClient.seek(Math.round(ReviewClient.cursor) + step);
            }
            case InputConstants.KEY_LEFT -> {
                ReviewClient.playing = false;
                ReviewClient.seek(Math.round(ReviewClient.cursor) - step);
            }
            case InputConstants.KEY_UP -> stepMark(1);
            case InputConstants.KEY_DOWN -> stepMark(-1);
            case InputConstants.KEY_B -> ReviewClient.coachView = !ReviewClient.coachView;
            case InputConstants.KEY_P -> ReviewClient.requestClip(clipStart(ReviewClient.analysis));
            default -> {
                return super.keyPressed(e);
            }
        }
        return true;
    }
}
