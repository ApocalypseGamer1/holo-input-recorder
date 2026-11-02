package holorecorder.doc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.UUID;

/**
 * The guide's moving pictures, drawn by id into their box: {@code duel} (a fight clip), {@code chest} (a round going into
 * the chest), {@code sim} (rounds simulated in training), {@code holo} (the player's own skin as a hologram). Any other id
 * draws nothing. {@code ms} is the time since the picture was shown, so it starts from its beginning.
 */
final class DocAnims {
    private static final int BG = 0xFF0B0E13, CYAN = 0x5FE8FF, RED = 0xFF6B6B, GREEN = 0x5FD38A;
    // the clip: DUEL_FRAMES frames of DUEL_W x DUEL_H in rows of DUEL_COLS, played at DUEL_FPS
    static final int DUEL_FRAMES = 40, DUEL_COLS = 8, DUEL_W = 256, DUEL_H = 144, DUEL_FPS = 10;
    private static final Identifier DUEL = Identifier.fromNamespaceAndPath("holo_input_recorder", "textures/gui/guide/duel.png");
    private static final UUID RIVAL = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final ItemStack CHEST = new ItemStack(Items.CHEST), SWORD = new ItemStack(Items.DIAMOND_SWORD);
    private static final ItemStack[] SAVED = {new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.TOTEM_OF_UNDYING)};
    private static Boolean clip;

    private DocAnims() {
    }

    static void draw(GuiGraphicsExtractor g, Font font, String id, int x, int y, int w, int h, long ms) {
        switch (id) {
            case "duel" -> duel(g, x, y, w, h, ms);
            case "chest" -> chest(g, font, x, y, w, h, ms);
            case "sim" -> sim(g, font, x, y, w, h, ms);
            case "holo" -> holo(g, x, y, w, h, ms);
            default -> {
            }
        }
    }

    private static int alpha(double a, int rgb) {
        return ((int) Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | rgb;
    }

    private static void item(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float sx, float sy) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(sx, sy);
        g.item(stack, -8, -8);
        g.pose().popMatrix();
    }

    /** A round flies down the arrow into the chest, which bounces; "+1" rises. A new item each time. */
    private static void chest(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, long ms) {
        g.fill(x, y, x + w, y + h, BG);
        double period = 1700, p = (ms % period) / period;
        int loop = (int) (ms / period);
        float s = Math.max(1, Math.min(w / 3f, h * 0.55f) / 16f);
        float cx = x + w / 2f, cy = y + h - 8 * s - 3;
        int bob = (int) Math.round(Math.sin(ms / 170.0) * 2);
        int stem = Math.max(4, Math.round(4.5f * s)), tip = (int) (cy - 8 * s) - 1 + bob;
        if (p < 0.6) {
            double f = p / 0.6;
            f *= f;
            float is = s * 0.5f, top = y + 8 * is + 2;
            float iy = (float) (top + (cy - 4 * s - top) * f);
            item(g, SAVED[loop % SAVED.length], cx, iy, (float) (is * (1 - 0.3 * f)), (float) (is * (1 - 0.3 * f)));
        }
        arrow(g, (int) cx, y + 2 + bob, tip, stem, alpha(0.9, CYAN));
        double sq = p >= 0.6 && p < 0.85 ? Math.sin((p - 0.6) / 0.25 * Math.PI) * 0.14 : 0;
        item(g, CHEST, cx, cy + (float) (8 * s * sq), (float) (s * (1 + sq)), (float) (s * (1 - sq)));
        if (p >= 0.6 && p < 0.97) {
            double f = (p - 0.6) / 0.37;
            g.text(font, "+1", (int) (cx + 8 * s + 3), (int) (cy - 8 * s - f * 12), alpha(1 - f, GREEN), true);
        }
    }

    /** A down arrow drawn as a 1-pixel outline, so what falls inside it stays visible. */
    private static void arrow(GuiGraphicsExtractor g, int cx, int top, int tip, int stem, int argb) {
        int head = stem, headTop = tip - head, half = stem / 2;
        if (headTop > top) {
            g.fill(cx - half, top, cx + half, top + 1, argb);
            g.fill(cx - half, top, cx - half + 1, headTop, argb);
            g.fill(cx + half - 1, top, cx + half, headTop, argb);
        }
        g.fill(cx - head, headTop, cx - half + 1, headTop + 1, argb);
        g.fill(cx + half - 1, headTop, cx + head, headTop + 1, argb);
        for (int i = 0; i < head; i++) {
            int w = head - i;
            g.fill(cx - w, headTop + i, cx - w + 1, headTop + i + 1, argb);
            g.fill(cx + w - 1, headTop + i, cx + w, headTop + i + 1, argb);
        }
    }

    /** Many small arenas at once, two fighters in each; a cell's edge flashes green or red when its round ends. */
    private static void sim(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, long ms) {
        g.fill(x, y, x + w, y + h, BG);
        String count = String.format(Locale.ROOT, "%,d", 1_204_551 + ms * 3);
        g.text(font, count, x + (w - font.width(count)) / 2, y + 3, alpha(1, CYAN), true);
        int gy = y + font.lineHeight + 6, gh = y + h - 3 - gy;
        if (gh < 8) return;
        int cols = Math.max(2, (w - 4) / 30), rows = Math.max(1, gh / 22), cw = (w - 4) / cols, ch = gh / rows;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c, x0 = x + 2 + c * cw, y0 = gy + r * ch;
                long t = ms + i * 331L;
                int len = 1800 + (i * 7919) % 1600;
                long round = t / len;
                double tl = t % len;
                int edge = tl > len - 260 ? ((i + round) % 3 == 0 ? 0xFF000000 | RED : 0xFF000000 | GREEN) : 0xFF2A313C;
                g.fill(x0 + 1, y0 + 1, x0 + cw - 1, y0 + ch - 1, edge);
                g.fill(x0 + 2, y0 + 2, x0 + cw - 2, y0 + ch - 2, 0xFF141920);
                double mx = x0 + cw / 2.0, my = y0 + ch / 2.0, rx = (cw - 8) / 2.0, ry = (ch - 8) / 2.0;
                double a = tl / 260.0 + i;
                int ax = (int) (mx + rx * Math.cos(a) * 0.8), ay = (int) (my + ry * Math.sin(a * 1.3) * 0.8);
                int bx = (int) (mx - rx * Math.cos(a * 0.9 + 1) * 0.8), by = (int) (my - ry * Math.sin(a * 1.1) * 0.8);
                if (Math.abs(ax - bx) + Math.abs(ay - by) < 5) g.fill((ax + bx) / 2 - 2, (ay + by) / 2 - 2, (ax + bx) / 2 + 2, (ay + by) / 2 + 2, 0xFFFFFFFF);
                g.fill(ax - 1, ay - 1, ax + 1, ay + 1, 0xFF000000 | CYAN);
                g.fill(bx - 1, by - 1, bx + 1, by + 1, 0xFF000000 | RED);
            }
        }
    }

    private static PlayerSkin skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getSkin() : DefaultPlayerSkin.getDefaultSkin();
    }

    /**
     * The skin seen from the front, 16 x 32 skin pixels of {@code s} screen pixels each, tinted {@code argb}; {@code glow}
     * (ARGB, 0 for none) is filled behind every part first and again, lighter, over it: a dark skin then reads as light.
     */
    private static void doll(GuiGraphicsExtractor g, PlayerSkin skin, float x, float y, float s, int argb, int glow) {
        Identifier tex = skin.body().texturePath();
        int arm = skin.model() == PlayerModelType.SLIM ? 3 : 4;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        int[][] parts = {   // u, v, w, h, x, y in skin pixels: base layer, then the outer layer over it
                {8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8}, {44, 20, arm, 12, 4 - arm, 8}, {36, 52, arm, 12, 12, 8},
                {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20},
                {40, 8, 8, 8, 4, 0}, {20, 36, 8, 12, 4, 8}, {44, 36, arm, 12, 4 - arm, 8}, {52, 52, arm, 12, 12, 8},
                {4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20}};
        if (glow != 0) for (int k = 0; k < 6; k++) g.fill(parts[k][4], parts[k][5], parts[k][4] + parts[k][2], parts[k][5] + parts[k][3], glow);
        for (int[] p : parts) g.blit(RenderPipelines.GUI_TEXTURED, tex, p[4], p[5], p[0], p[1], p[2], p[3], p[2], p[3], 64, 64, argb);
        int over = (glow >>> 25) << 24 | glow & 0xFFFFFF;   // half the glow's alpha
        if (glow != 0) for (int k = 0; k < 6; k++) g.fill(parts[k][4], parts[k][5], parts[k][4] + parts[k][2], parts[k][5] + parts[k][3], over);
        g.pose().popMatrix();
    }

    /** The player's own skin as a flickering cyan hologram over a projector pad, with scan lines running down it. */
    private static void holo(GuiGraphicsExtractor g, int x, int y, int w, int h, long ms) {
        g.fill(x, y, x + w, y + h, 0xFF06090D);
        float s = Math.max(1, (h - 16) / 32f);
        int dw = Math.round(16 * s), dh = Math.round(32 * s);
        int cx = x + w / 2, padY = y + h - 7;
        int bob = (int) Math.round(Math.sin(ms / 420.0) * 2);
        int dx = cx - dw / 2, dy = padY - 3 - dh + bob;
        g.fillGradient(cx - dw / 2 - 4, dy - 4, cx + dw / 2 + 4, padY, alpha(0, CYAN), alpha(0.28, CYAN));
        for (int i = 0; i < 3; i++) {
            int half = dw / 2 + 8 - i * 3;
            g.fill(cx - half, padY + i * 2, cx + half, padY + i * 2 + 2, alpha(0.9 - i * 0.25, i == 0 ? CYAN : 0x2A8FA0));
        }
        boolean glitch = (ms / 90) % 29 == 0;
        double flicker = 0.72 + 0.12 * Math.sin(ms / 55.0) + 0.06 * Math.sin(ms / 13.0) - (glitch ? 0.3 : 0);
        doll(g, skin(), dx + (glitch ? 2 * s : 0), dy, s, alpha(flicker, 0x55DDFF), alpha(flicker * 0.55, CYAN));
        for (int yy = dy; yy < dy + dh; yy += 3) g.fill(dx - 2, yy, dx + dw + 2, yy + 1, 0x7006090D);
        int scan = dy + (int) ((ms / 18) % (dh + 20)) - 10;
        if (scan > dy && scan < dy + dh) g.fill(dx - 3, scan, dx + dw + 3, scan + 1, alpha(0.55, CYAN));
    }

    private static boolean hasClip() {
        if (clip == null) {
            try {
                clip = Minecraft.getInstance().getResourceManager().getResource(DUEL).isPresent();
            } catch (RuntimeException e) {
                clip = false;
            }
        }
        return clip;
    }

    /** The recorded fight clip; without it, two players trading hits. A thin bar shows the clip's progress. */
    private static void duel(GuiGraphicsExtractor g, int x, int y, int w, int h, long ms) {
        g.fill(x, y, x + w, y + h, BG);
        if (hasClip()) {
            int frame = (int) ((ms * DUEL_FPS / 1000) % DUEL_FRAMES), rows = (DUEL_FRAMES + DUEL_COLS - 1) / DUEL_COLS;
            g.blit(RenderPipelines.GUI_TEXTURED, DUEL, x, y, (frame % DUEL_COLS) * DUEL_W, (frame / DUEL_COLS) * DUEL_H, w, h, DUEL_W, DUEL_H,
                    DUEL_COLS * DUEL_W, rows * DUEL_H, 0xFFFFFFFF);
            g.fill(x, y + h - 1, x + (int) ((long) w * (frame + 1) / DUEL_FRAMES), y + h, alpha(0.8, CYAN));
            return;
        }
        int s = Math.max(1, (h - 12) / 34), floor = y + h - 6;
        g.fill(x, floor, x + w, floor + 1, 0xFF2A313C);
        double period = 1300, p = (ms % period) / period;
        double hit = p > 0.45 && p < 0.6 ? 1 : 0, back = p > 0.45 ? Math.sin(Math.min(1, (p - 0.45) / 0.4) * Math.PI) : 0;
        int gap = (int) (w * 0.12), meX = x + w / 2 - gap - 16 * s, rivalX = (int) (x + w / 2 + gap + back * 6 * s);
        int lunge = (int) (Math.sin(Math.min(1, p / 0.5) * Math.PI) * 5 * s);
        doll(g, skin(), meX + lunge, floor - 32 * s, s, 0xFFFFFFFF, 0);
        doll(g, DefaultPlayerSkin.get(RIVAL), rivalX, floor - 32 * s, s, hit > 0 ? 0xFFFF8080 : 0xFFFFFFFF, 0);
        float swing = (float) (p < 0.5 ? -0.6 + p * 2.4 : 0.6);
        g.pose().pushMatrix();
        g.pose().translate(meX + lunge + 15 * s, floor - 20 * s);
        g.pose().rotate(swing);
        g.pose().scale(s * 0.8f, s * 0.8f);
        g.item(SWORD, -2, -14);
        g.pose().popMatrix();
    }
}
