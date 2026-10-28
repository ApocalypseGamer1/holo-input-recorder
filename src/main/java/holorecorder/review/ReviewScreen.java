package holorecorder.review;

import com.mojang.blaze3d.platform.InputConstants;
import holorecorder.Tr;
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
        super(Component.literal(Tr.t("Round review")));
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
            case BLUNDER -> Tr.t("Blunder");
            case MISTAKE -> Tr.t("Mistake");
            case INACCURACY -> Tr.t("Inaccuracy");
            case BEST -> Tr.t("Best");
            case MISS -> Tr.t("Miss");
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
        g.text(f, Tr.t("You"), x0, y0 + barH + 3, TEXT, true);
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
        String text = (ReviewClient.inClip() && ReviewClient.showingSwordbot ? Tr.t("Bot hit {0}", ReviewClient.botName()) : Tr.t("Bot hit you")) + (hearts > 0 ? String.format("  -%.1f\u2764", hearts) : "");
        g.centeredText(f, text, w / 2, h / 2 - 22, (int) Math.round(255 * k) << 24 | 0xFF5050);
    }

    /** In a clip: Swordbot was not hit where you were. */
    private void drawDodged(GuiGraphicsExtractor g, Font f, int w, int h) {
        double since = ReviewClient.sinceDodged();
        if (since >= 2 * GOT_HIT_TICKS) return;
        double k = Math.min(1, 2 - since / GOT_HIT_TICKS);
        String text = Tr.t("{0} dodged a hit you took", ReviewClient.botName());
        int tw = f.width(text), cx = (w - 136) / 2;   // left of the key panel
        g.fill(cx - tw / 2 - 3, 26, cx + tw / 2 + 3, 38, (int) Math.round(0xB0 * k) << 24);
        g.centeredText(f, text, cx, 28, (int) Math.round(255 * k) << 24 | 0x9BE060);
    }

    /** What caused the mark the cursor is in: shown while its moment replays. */
    private void drawCause(GuiGraphicsExtractor g, Font f, int w, int h, ReviewAnalysis a, int i) {
        ReviewAnalysis.Mark m = a.markAt(i);
        if (m == null) return;
        String title = symbol(m.kind()) + " " + label(m.kind()) + " " + clock(m.start()) + "-" + clock(m.end());
        if (m.kind() != ReviewAnalysis.Kind.MISS) {
            int hi = Math.min(a.shown.length - 1, m.to());
            if (hi >= 0) title += Tr.t(":  win {0}% -> {1}%", Math.round(a.shown[Math.max(0, m.from())] * 100), Math.round(a.shown[hi] * 100));
        }
        if (m.forced()) title += Tr.t(" (hard to avoid)");
        boolean clip = m.kind() != ReviewAnalysis.Kind.BEST && ReviewClient.clipsAvailable();
        int start = clipStart(a);
        // Both sides over the mark's stretch: your round against Swordbot's clip from its start.
        java.util.List<String> moves = new ArrayList<>();
        boolean spear = ReviewClient.record.spear();
        // Spearmace: the round's bot is the Spearmace bot too, so its clip line says whose seat it plays.
        String coachTag = spear ? Tr.t("In your place") : ReviewClient.botName();
        ReviewRecord sb = ReviewClient.clipAt(start);
        int len = m.end() - start + 1;
        if (spear) {
            int end = m.end() + 1;
            moves.add(Tr.t("You: {0}", sequence(within(ReviewClient.spearChips(ReviewClient.record, true), start, end), Tr.t("no moves"))));
            moves.add("Bot: " + sequence(within(ReviewClient.spearChips(ReviewClient.record, false), start, end), Tr.t("no moves")));
        } else if (m.kind() != ReviewAnalysis.Kind.BEST) {
            // Your round cut like the clip, so the same ticks get the same move names on both lines.
            int cut = sb != null ? sb.ticks.size() : ReviewClient.CLIP_TICKS;
            moves.add(Tr.t("You: {0}", sequence(within(ReviewClient.chips(ReviewClient.slice(ReviewClient.record, start, cut)), 0, len))));
        }
        if (m.kind() != ReviewAnalysis.Kind.BEST) {
            if (sb != null) moves.add(coachTag + ": " + sequence(within(ReviewClient.chips(sb), 0, len), spear ? Tr.t("no moves") : Tr.t("no hits")));
            else if (clip && !ReviewClient.noClipAt(start)) {
                moves.add(ReviewClient.fetching(start) ? Tr.t("{0}: playing it out...", coachTag) : coachTag + ": ...");
                ReviewClient.prefetch(start);
            }
        }
        java.util.List<String> lines = new ArrayList<>(a.explain(m, moves));
        int coachLine = -1;
        for (int k = 0; k < lines.size(); k++) if (lines.get(k).startsWith(coachTag + ":")) coachLine = k;
        int x0 = 46, max = w - 144 - x0;
        for (int k = 0; k < lines.size(); k++) {
            if (f.width(lines.get(k)) > max) lines.set(k, f.plainSubstrByWidth(lines.get(k), max - f.width("...")) + "...");
        }
        int bw = f.width(title);
        for (String l : lines) bw = Math.max(bw, f.width(l));
        String play = ReviewClient.clipPending() ? Tr.t("{0} is playing...", ReviewClient.botName()) : "\u25B6 " + Tr.t("{0}'s play from here", ReviewClient.botName());
        if (clip) bw = Math.max(bw, f.width(play) + 8);
        int y1 = h - 92, y0 = y1 - 14 - lines.size() * 10 - (clip ? 18 : 0);
        g.fill(x0 - 4, y0 - 4, x0 + bw + 4, y1, PANEL);
        g.fill(x0 - 4, y0 - 4, x0 - 2, y1, color(m.kind()));
        g.text(f, title, x0, y0, color(m.kind()), true);
        for (int k = 0; k < lines.size(); k++) g.text(f, lines.get(k), x0, y0 + 13 + k * 10, k == coachLine ? COACH : TEXT, true);
        if (clip) button(g, f, x0, y1 - 18, play, () -> ReviewClient.requestClip(clipStart(a)), ReviewClient.clipPending());
    }

    /** Where Swordbot's play starts: the start of the mark's stretch the cursor is in, else the cursor. */
    static int clipStart(ReviewAnalysis a) {
        int i = (int) Math.floor(ReviewClient.cursor);
        ReviewAnalysis.Mark m = a.markAt(i);
        return m == null || m.kind() == ReviewAnalysis.Kind.BEST ? i : m.start();
    }

    private void drawClipHeader(GuiGraphicsExtractor g, Font f, int w, ReviewRecord r) {
        String title = ReviewClient.showingSwordbot ? Tr.t("2/2  {0}'s play from {1}", ReviewClient.botName(), clock(ReviewClient.clipFrom)) : Tr.t("1/2  Your play from {0}", clock(ReviewClient.clipFrom));
        String sub = ReviewClient.showingSwordbot ? (r.spear() ? Tr.t("The bot plays in your place and fights itself back live.") : Tr.t("{0} plays in your place. {1} fights back live.", ReviewClient.botName(), r.oppName))
                : Tr.t("What you did. Next: {0} from the same moment.", ReviewClient.botName());
        int tw = Math.max(f.width(title), f.width(sub));
        g.fill(w / 2 - tw / 2 - 4, 2, w / 2 + tw / 2 + 4, 14 + f.lineHeight, PANEL);
        g.centeredText(f, title, w / 2, 4, ReviewClient.showingSwordbot ? COACH : TEXT);
        g.centeredText(f, sub, w / 2, 5 + f.lineHeight, MUTED);
    }

    private void drawHeader(GuiGraphicsExtractor g, Font f, int w, ReviewRecord r, ReviewAnalysis a) {
        String result = switch (r.outcome) {
            case ReviewRecord.OUTCOME_WIN -> Tr.t("Win");
            case ReviewRecord.OUTCOME_LOSS -> Tr.t("Loss");
            case ReviewRecord.OUTCOME_DRAW -> Tr.t("Draw");
            default -> Tr.t("Unfinished");
        };
        String title = Tr.t("Round review: {0} vs {1}", result, r.oppName);
        double acc = a.accuracy();
        if (!Double.isNaN(acc)) title += "   " + Tr.t("Accuracy {0}%", Math.round(acc));
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
        if (r.coachName.isEmpty() && !r.spear()) g.centeredText(f, Tr.t("No bot data for this round: marks use the win chance only."), w / 2, y + 12, MUTED);
    }

    /** Your keys this moment against the bot's choice for the same moment, and the nearest mark's note. */
    private void drawKeys(GuiGraphicsExtractor g, Font f, int w, int h, ReviewRecord r, ReviewAnalysis a, int i) {
        if (r.spear()) {
            drawSpear(g, f, w, h, r, i);
            return;
        }
        int x0 = w - 2 - Math.max(130, keyRowWidth(f)), y0 = Math.max(30, (h - 84) / 2 - 56);
        g.fill(x0 - 4, y0 - 4, w - 2, y0 + 104, PANEL);
        ReviewAnalysis.Suggestion s = a.suggestion(i);
        int next = Math.min(r.ticks.size() - 1, i + 1);
        ReviewRecord.Tick you = r.ticks.get(next);
        int k = you.keys;
        boolean fwd = (k & 1) != 0 && (k & 2) == 0, back = (k & 2) != 0 && (k & 1) == 0;
        boolean left = (k & 4) != 0 && (k & 8) == 0, right = (k & 8) != 0 && (k & 4) == 0;
        boolean clip = ReviewClient.inClip();
        boolean bot = clip && ReviewClient.showingSwordbot;
        g.text(f, bot ? Tr.t("{0} pressed", ReviewClient.botName()) : clip ? Tr.t("You pressed") : Tr.t("You"), x0, y0, bot ? COACH : TEXT, true);
        boolean[] youKeys = {fwd, left, back, right, (k & 16) != 0, (k & 64) != 0, r.swingStart(next, true)};
        keyRow(g, f, x0, y0 + 11, youKeys, null, bot ? COACH : ON);
        if (clip) {
            g.text(f, f.plainSubstrByWidth(r.oppName, 124), x0, y0 + 30, MUTED, true);
            g.text(f, bot ? Tr.t("fights back live") : Tr.t("as in your round"), x0, y0 + 41, MUTED, true);
        } else if (s == null) {
            g.text(f, Tr.t("{0} would", ReviewClient.botName()), x0, y0 + 30, COACH, true);
            g.text(f, Tr.t("no data here"), x0, y0 + 41, MUTED, true);
        } else {
            g.text(f, Tr.t("{0} would", ReviewClient.botName()), x0, y0 + 30, COACH, true);
            boolean[] bots = {s.forward(), s.left(), s.back(), s.right(), s.jump(), s.sprint(), s.attackP() >= 0.5};
            keyRow(g, f, x0, y0 + 41, bots, youKeys, COACH);
            String turn = turnText(s.yawDeg(), s.pitchDeg());
            g.text(f, Tr.t("click {0}%  {1}", Math.round(s.attackP() * 100), turn), x0, y0 + 58, MUTED, true);
        }
        ReviewRecord.Tick now = r.ticks.get(Math.max(0, i));
        if (clip) g.text(f, bot ? Tr.t("{0} HP {1}", ReviewClient.botName(), hp(now.self.health)) : Tr.t("Your HP {0}", hp(now.self.health)), x0, y0 + 70, MUTED, true);
        else g.text(f, Tr.t("HP you {0}  bot {1}", hp(now.self.health), hp(now.opp.health)), x0, y0 + 70, MUTED, true);
        double now2 = ReviewClient.rangeNow(), yours = ReviewClient.rangeAtYourHit(), bots = ReviewClient.rangeAtBotHit();
        g.text(f, Tr.t("Range now {0}", String.format("%.1f", now2)), x0, y0 + 81, now2 <= 3.0 ? ON : MUTED, true);
        if (clip) g.text(f, Tr.t("Hits {0}  taken {1}", ReviewClient.hitsSoFar(true), ReviewClient.hitsSoFar(false)), x0, y0 + 92, MUTED, true);
        else g.text(f, Tr.t("Your hit {0}  bot's {1}", range(yours), range(bots)), x0, y0 + 92, MUTED, true);
    }

    /** Spearmace: what each fighter holds and has left at this moment (no coach in this mode). */
    private void drawSpear(GuiGraphicsExtractor g, Font f, int w, int h, ReviewRecord r, int i) {
        int x0 = w - 132, y0 = Math.max(30, (h - 84) / 2 - 62);
        g.fill(x0 - 4, y0 - 4, w - 2, y0 + 124, PANEL);
        ReviewRecord.Tick t = r.ticks.get(Math.max(0, Math.min(r.ticks.size() - 1, i)));
        int y = spearSide(g, f, x0, y0, Tr.t("You"), TEXT, t.self, t.selfSpear);
        y = spearSide(g, f, x0, y + 4, f.plainSubstrByWidth(r.oppName, 124), MUTED, t.opp, t.oppSpear);
        double now = ReviewClient.rangeNow();
        g.text(f, Tr.t("Range now {0}", String.format("%.1f", now)), x0, y + 4, now <= 3.0 ? ON : MUTED, true);
    }

    private static int spearSide(GuiGraphicsExtractor g, Font f, int x, int y, String who, int colour, ReviewRecord.Fighter p, ReviewRecord.Spear s) {
        g.text(f, who + "  " + hp(p.health) + (s.absorption > 0 ? " +" + hp(s.absorption) : ""), x, y, colour, true);
        String state = s.gliding ? Tr.t("gliding") : s.use == 4 ? Tr.t("shield up") : s.use != 0 ? Tr.t("using {0}", itemName(s.use).toLowerCase(java.util.Locale.ROOT))
                : s.fallDistance >= 1.5f ? Tr.t("falling {0}", String.format("%.0f", s.fallDistance)) : "";
        g.text(f, itemName(s.main) + (state.isEmpty() ? "" : ", " + state), x, y + 11, ON, true);
        g.text(f, Tr.t("Totems {0}  Pearls {1}", s.totems, s.pearls), x, y + 22, MUTED, true);
        g.text(f, Tr.t("Charges {0}  Apples {1}", s.charges, s.apples), x, y + 33, MUTED, true);
        String buffs = (s.strength ? Tr.t("Strength") + " " : "") + (s.speed ? Tr.t("Speed") + " " : "") + (s.pearlCooldown > 0 ? Tr.t("pearl wait {0}s", Math.round(s.pearlCooldown / 2.0) / 10.0) : "");
        g.text(f, buffs.isEmpty() ? " " : buffs.trim(), x, y + 44, MUTED, true);
        return y + 55;
    }

    /** SpearItems kind numbers as the server records them. */
    static String itemName(int kind) {
        return switch (kind) {
            case 0 -> Tr.t("Empty hand");
            case 1 -> Tr.t("Sword");
            case 2 -> Tr.t("Axe");
            case 3 -> Tr.t("Mace");
            case 4 -> Tr.t("Shield");
            case 5 -> Tr.t("Wind charge");
            case 6 -> Tr.t("Pearl");
            case 7 -> Tr.t("Totem");
            case 8 -> Tr.t("Golden apple");
            case 9 -> Tr.t("Notch apple");
            case 10 -> Tr.t("Elytra");
            case 12 -> Tr.t("Spear");
            case 13 -> Tr.t("Strength potion");
            case 14 -> Tr.t("Speed potion");
            case 24 -> Tr.t("Food");
            default -> Tr.t("Other item");
        };
    }

    private static String range(double v) {
        return Double.isNaN(v) ? "-" : String.format("%.1f", v);
    }

    private static String hp(float v) {
        return String.format("%.1f", v / 2f) + "❤";
    }

    private static String turnText(double yaw, double pitch) {
        String t = Math.abs(yaw) < 1 ? "" : yaw > 0 ? Tr.t("turn right {0}°", Math.round(Math.abs(yaw))) : Tr.t("turn left {0}°", Math.round(Math.abs(yaw)));
        if (Math.abs(pitch) >= 1) t += (t.isEmpty() ? "" : ", ") + (pitch > 0 ? Tr.t("down {0}°", Math.round(Math.abs(pitch))) : Tr.t("up {0}°", Math.round(Math.abs(pitch))));
        return t.isEmpty() ? Tr.t("hold aim") : t;
    }

    private static String[] keyNames() {
        return new String[] {"W", "A", "S", "D", Tr.t("Jump"), Tr.t("Run"), Tr.t("Hit")};
    }

    /** The key row is as wide as its labels need (Spanish ones are longer); the panel grows to fit it. */
    private static int keyRowWidth(Font f) {
        int w = -2;
        for (String n : keyNames()) w += f.width(n) + 7;
        return w;
    }

    /** One key row; where {@code other} is given, keys that differ from it get a yellow frame. */
    private static void keyRow(GuiGraphicsExtractor g, Font f, int x, int y, boolean[] lit, boolean[] other, int on) {
        String[] names = keyNames();
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
            int sx = tickX(m.start(), n), ex = Math.max(sx + 1, tickX(m.end(), n)), mx = (sx + ex) / 2;
            g.fill(sx, y0, ex, y1, color(m.kind()) & 0x50FFFFFF);
            g.fill(sx, y0, ex, y0 + 2, color(m.kind()));
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

    /** The move the shown fighter makes now, under the crosshair (in a clip: under the compare box). */
    private void drawMoveNow(GuiGraphicsExtractor g, Font f, int w, int h, boolean bot) {
        ReviewClient.Chip m = ReviewClient.moveNow();
        if (m == null) return;
        String text = bot ? ReviewClient.botName() + ": " + m.name() : Tr.t("You: {0}", m.name());
        int tw = Math.max(f.width(text), f.width(m.detail())), y = ReviewClient.inClip() ? h - 88 : h / 2 + 12, y1 = m.detail().isEmpty() ? y + 10 : y + 20;
        g.fill(w / 2 - tw / 2 - 3, y - 2, w / 2 + tw / 2 + 3, y1, PANEL);
        g.centeredText(f, text, w / 2, y, m.weak() ? 0xFFF7C631 : bot ? COACH : ON);
        if (!m.detail().isEmpty()) g.centeredText(f, m.detail(), w / 2, y + 10, MUTED);
    }

    /** Your play against Swordbot's over the same stretch. */
    private void drawCompare(GuiGraphicsExtractor g, Font f, int h) {
        double[] you = ReviewClient.tally(false), bot = ReviewClient.tally(true);
        String[][] rows = {
            {Tr.t("Hits landed"), String.valueOf((int) you[0]), String.valueOf((int) bot[0])},
            {Tr.t("Hits taken"), String.valueOf((int) you[1]), String.valueOf((int) bot[1])},
            {Tr.t("Hearts lost"), String.format("%.1f", you[2]), String.format("%.1f", bot[2])},
        };
        String youMoves = Tr.t("You: {0}", sequence(ReviewClient.movesOf(false))), botMoves = ReviewClient.botName() + ": " + sequence(ReviewClient.movesOf(true));
        int label = f.width(Tr.t("Same moment"));
        for (String[] r : rows) label = Math.max(label, f.width(r[0]));
        int x0 = 46, colA = x0 + Math.max(70, label + 6), colB = colA + 40, y1 = h - 92, lines = rows.length + 3;
        // Stops short of the right-hand key panel; longer move lines end in "...".
        int room = width - 2 - Math.max(130, keyRowWidth(f)) - 12 - x0;
        int bw = Math.min(Math.max(colB - x0 + f.width(ReviewClient.botName()), Math.max(f.width(youMoves), f.width(botMoves))), room);
        bw = Math.max(bw, Math.min(150, room));
        youMoves = fit(f, youMoves, bw);
        botMoves = fit(f, botMoves, bw);
        int y0 = y1 - 4 - lines * 10;
        g.fill(x0 - 4, y0 - 4, x0 + bw + 4, y1, PANEL);
        g.text(f, Tr.t("Same moment"), x0, y0, MUTED, true);
        g.text(f, Tr.t("You"), colA, y0, ReviewClient.showingSwordbot ? MUTED : TEXT, true);
        g.text(f, ReviewClient.botName(), colB, y0, ReviewClient.showingSwordbot ? COACH : MUTED, true);
        for (int k = 0; k < rows.length; k++) {
            int y = y0 + 11 + k * 10;
            g.text(f, rows[k][0], x0, y, TEXT, true);
            g.text(f, rows[k][1], colA, y, TEXT, true);
            g.text(f, rows[k][2], colB, y, COACH, true);
        }
        g.text(f, youMoves, x0, y0 + 13 + rows.length * 10, TEXT, true);
        g.text(f, botMoves, x0, y0 + 23 + rows.length * 10, COACH, true);
    }

    private static String fit(Font f, String text, int width) {
        return f.width(text) <= width ? text : f.plainSubstrByWidth(text, width - f.width("...")) + "...";
    }

    /** "Sprint hit + W-tap x2, Crit": the most used moves by count (3 at most). */
    private static List<ReviewClient.Chip> within(List<ReviewClient.Chip> moves, int from, int to) {
        List<ReviewClient.Chip> out = new ArrayList<>();
        for (ReviewClient.Chip c : moves) if (c.start() >= from && c.start() < to) out.add(c);
        return out;
    }

    private static String sequence(List<ReviewClient.Chip> moves) {
        return sequence(moves, Tr.t("no hits"));
    }

    /** Moves in time order, repeats in a row counted ("Crit x3"), at most 3 then "..."; {@code none} when empty. */
    private static String sequence(List<ReviewClient.Chip> moves, String none) {
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (ReviewClient.Chip m : moves) {
            String name = m.name().replaceAll("\\s*\\([^)]*\\)$", "");
            if (!names.isEmpty() && names.get(names.size() - 1).equals(name)) counts.set(counts.size() - 1, counts.get(counts.size() - 1) + 1);
            else {
                names.add(name);
                counts.add(1);
            }
        }
        List<String> parts = new ArrayList<>();
        for (int k = 0; k < Math.min(3, names.size()); k++) parts.add(counts.get(k) > 1 ? names.get(k) + " x" + counts.get(k) : names.get(k));
        if (names.size() > 3) parts.add("...");
        return parts.isEmpty() ? none : String.join(", ", parts);
    }

    private void drawClipControls(GuiGraphicsExtractor g, Font f, int w, int y, int n) {
        int x = graphX0, closeX = w - 24 - f.width(Tr.t("Close")) - 8;
        // Small screens: short labels, then no speed buttons, so nothing runs under Close.
        String[] full = {Tr.t("Back to your round"), Tr.t("Your play"), Tr.t("{0}'s play", ReviewClient.botName()), Tr.t("Start")}, small = {Tr.t("Back"), Tr.t("You"), ReviewClient.record.spear() ? "Bot" : "Swordbot", Tr.t("Start")};
        int speedsW = 0;
        for (double s : SPEEDS) speedsW += f.width(speedLabel(s)) + 11;
        boolean shortLabels = x + rowWidth(f, full) + 24 + speedsW > closeX - 4;
        String[] l = shortLabels ? small : full;
        boolean speeds = x + rowWidth(f, l) + 24 + speedsW <= closeX - 4;
        x = button(g, f, x, y, l[0], ReviewClient::endClip);
        x += 8;
        x = button(g, f, x, y, l[1], () -> ReviewClient.show(false), !ReviewClient.showingSwordbot);
        x = button(g, f, x, y, l[2], () -> ReviewClient.show(true), ReviewClient.showingSwordbot);
        x += 8;
        x = button(g, f, x, y, l[3], () -> ReviewClient.seek(0));
        x = button(g, f, x, y, ReviewClient.playing ? Tr.t("Pause") : Tr.t("Play"), ReviewScreen::togglePlay);
        x += 8;
        if (speeds) {
            for (double s : SPEEDS) x = button(g, f, x, y, speedLabel(s), () -> ReviewClient.speed = s, ReviewClient.speed == s);
        }
        button(g, f, closeX, y, Tr.t("Close"), this::onClose);
        String time = clock(ReviewClient.clipFrom + ReviewClient.cursor) + "   " + clock(ReviewClient.cursor) + " / " + clock(Math.max(0, n - 1));
        g.text(f, time, graphX1 - f.width(time), graphY0 - 25, TEXT, true);
        String help = Tr.t("Tab switch  Esc back");
        if (x + 16 + f.width(help) < closeX - 8) g.text(f, help, x + 16, y + 4, MUTED, true);
    }

    private static String speedLabel(double s) {
        return (s == 1 ? "1" : s == 0.5 ? "0.5" : "0.25") + "x";
    }

    /** The clip row's buttons before the speeds: the labels plus Pause. */
    private static int rowWidth(Font f, String[] labels) {
        int sum = Math.max(f.width(Tr.t("Pause")), f.width(Tr.t("Play"))) + 11;
        for (String t : labels) sum += f.width(t) + 11;
        return sum;
    }

    private int tickX(double tick, int n) {
        return graphX0 + (int) Math.round(n <= 1 ? 0 : tick / (n - 1) * (graphX1 - graphX0 - 1));
    }

    private static String clock(double ticks) {
        double s = ticks / 20.0;
        return String.format("%d:%04.1f", (int) (s / 60), s % 60);
    }

    private void drawControls(GuiGraphicsExtractor g, Font f, int w, int y, int n) {
        int closeX = w - 24 - f.width(Tr.t("Close")) - 8;
        boolean aim = !ReviewClient.record.coachName.isEmpty();
        String start = Tr.t("Start"), play = ReviewClient.playing ? Tr.t("Pause") : Tr.t("Play"), aimText = Tr.t("Bot aim"), marks = Tr.t("Stop at marks");
        int need = graphX0 + controlsWidth(f, start, Math.max(f.width(Tr.t("Pause")), f.width(Tr.t("Play"))), aim ? aimText : null, marks);
        if (need > closeX - 4) {
            // Narrow window (or longer Spanish labels): the short forms.
            start = "|<";
            play = ReviewClient.playing ? "||" : "▶";
            aimText = Tr.t("Aim");
            marks = Tr.t("Marks");
        }
        int x = graphX0;
        x = button(g, f, x, y, start, () -> ReviewClient.seek(0));
        x = button(g, f, x, y, "<", () -> stepMark(-1));
        x = button(g, f, x, y, play, ReviewScreen::togglePlay);
        x = button(g, f, x, y, ">", () -> stepMark(1));
        x += 8;
        for (double s : SPEEDS) {
            String label = (s == 1 ? "1" : s == 0.5 ? "0.5" : "0.25") + "x";
            x = button(g, f, x, y, label, () -> ReviewClient.speed = s, ReviewClient.speed == s);
        }
        x += 8;
        if (aim) x = button(g, f, x, y, aimText, () -> ReviewClient.coachView = !ReviewClient.coachView, ReviewClient.coachView);
        x = button(g, f, x, y, marks, () -> ReviewClient.stopAtMarks = !ReviewClient.stopAtMarks, ReviewClient.stopAtMarks);
        button(g, f, closeX, y, Tr.t("Close"), this::onClose);
        String time = clock(ReviewClient.cursor) + " / " + clock(Math.max(0, n - 1));
        g.text(f, time, graphX1 - f.width(time), graphY0 - 25, TEXT, true);
        String help = Tr.t("Space play  ←→ step  ↑↓ marks") + (ReviewClient.record.coachName.isEmpty() ? "" : "  " + Tr.t("B bot view")) + (ReviewClient.clipsAvailable() ? "  " + Tr.t("P {0}'s play", ReviewClient.botName()) : "");
        if (x + 16 + f.width(help) < closeX - 8) g.text(f, help, x + 16, y + 4, MUTED, true);
    }

    /** Width of the main control row up to its last toggle, as {@link #button} lays it out. */
    private static int controlsWidth(Font f, String start, int playW, String aim, String marks) {
        int w = f.width(start) + f.width("<") + playW + f.width(">") + 4 * 11 + 8 + 8;
        for (double s : SPEEDS) w += f.width(speedLabel(s)) + 11;
        if (aim != null) w += f.width(aim) + 11;
        return w + f.width(marks) + 11;
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

    /** Replay the mark's whole stretch in slow motion, holding at its end. */
    private static void playMark(ReviewAnalysis.Mark m) {
        ReviewClient.seek(m.start());
        ReviewClient.speed = 0.5;
        ReviewClient.playing = true;
        ReviewClient.stopAt = m.end();
    }

    private static void stepMark(int dir) {
        List<ReviewAnalysis.Mark> marks = ReviewClient.analysis.marks();
        int at = (int) Math.round(ReviewClient.cursor);
        ReviewAnalysis.Mark pick = null;
        for (ReviewAnalysis.Mark m : marks) {
            if (dir > 0 && m.start() > at && (pick == null || m.start() < pick.start())) pick = m;
            if (dir < 0 && m.start() < at - 2 && (pick == null || m.start() > pick.start())) pick = m;
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
            case InputConstants.KEY_B -> ReviewClient.coachView = !ReviewClient.coachView && !ReviewClient.record.coachName.isEmpty();
            case InputConstants.KEY_P -> ReviewClient.requestClip(clipStart(ReviewClient.analysis));
            default -> {
                return super.keyPressed(e);
            }
        }
        return true;
    }
}
