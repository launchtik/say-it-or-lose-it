package dev.sayit.client.hud;

import dev.sayit.config.SayItConfig;
import dev.sayit.core.WordMatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Recreates the mod UI from the reference video (see VIDEO_ANALYSIS.md section 5):
 * <ul>
 *   <li>top-centre challenge card: navy panel, gold accent bar, item icon, "SAY IT IN GERMAN", seconds
 *       left, the word in gold (green/red + underline per syllable after an attempt), shrinking timer bar,
 *       "listening…" / heard: "…" NN%</li>
 *   <li>green "UNLOCKED NN%" card, red "LOST" card, red error card</li>
 *   <li>centre "UNLOCKED" pop-up: small / medium / big-with-rays-and-orbs depending on time left</li>
 *   <li>"mic on" label left of the hotbar, plus a level meter</li>
 * </ul>
 * All state is touched on the client thread only.
 */
public final class ChallengeHud {
    // colours sampled from the video (ARGB)
    static final int PANEL = 0xE61E2433, GOLD = 0xFFF2B926, WORD_GOLD = 0xFFF5C542, HEADER = 0xFFB8BCC8;
    static final int GREEN = 0xFF5BE37D, RED = 0xFFFF5A6E, STATUS_GRAY = 0xFFA9ADB8;
    static final int OK_PANEL = 0xE61F4A35, OK_ACCENT = 0xFF4CE07A, OK_WORD = 0xFF6DF28E;
    static final int FAIL_PANEL = 0xE6401E24, FAIL_ACCENT = 0xFFE04C5A;
    static final int POP_GREEN = 0xFF5BF27A, POP_OUTLINE = 0xFF0B2B12, POP_RED = 0xFFFF4D5E, POP_RED_OUTLINE = 0xFF2B0B0E;

    public enum Tier { SMALL, MEDIUM, BIG }

    // ---- live challenge ----
    public static final class Active {
        public int id;
        public ItemStack icon = ItemStack.EMPTY;
        public String word = "";
        public String scoreAs = "";
        public String language = "de_de";
        public String languageName = "German";
        public int limitTicks = 200;
        public float elapsed;
        public boolean countingDown;
        public boolean finishSent;
        public String heard;
        public float score;
        public boolean lowConfidence;
        public boolean checking;
        public List<WordMatcher.Segment> segments;
        public String status = "";
        int lastTickSecond = -1;

        public float secondsLeft(float tickDelta) {
            float e = countingDown ? elapsed + tickDelta : elapsed;
            return Math.max(0, (limitTicks - e) / 20f);
        }
    }

    private static final class Card {
        enum Kind { SUCCESS, FAIL, ERROR }
        Kind kind;
        ItemStack icon = ItemStack.EMPTY;
        String title, line2;
        int age, life;
    }

    private static final class Popup {
        Tier tier;
        boolean fail;
        ItemStack icon;
        String word;
        int age, life;
    }

    private static final class Orb {
        float x, y, vx, vy, size;
        int age, life;
    }

    private Active active;
    private Card card;
    private Popup popup;
    private final List<Orb> orbs = new ArrayList<>();
    private final Random random = new Random();

    // mic indicator inputs, set by SayItClient each tick
    public String micText = "mic …";
    public int micColor = STATUS_GRAY;
    public float micLevel;
    public boolean micLive;

    public Active active() { return active; }

    public void begin(Active a) {
        active = a;
        card = null;
    }

    public void clearActive() { active = null; }

    public void showSuccess(ItemStack icon, String word, float score, int ticksLeft) {
        active = null;
        card = newCard(Card.Kind.SUCCESS, icon, word, "UNLOCKED  " + pct(score), 60);
        Popup p = new Popup();
        p.tier = ticksLeft >= 100 ? Tier.SMALL : ticksLeft >= 40 ? Tier.MEDIUM : Tier.BIG;
        p.icon = icon;
        p.word = word;
        p.life = p.tier == Tier.BIG ? 70 : p.tier == Tier.MEDIUM ? 60 : 50;
        popup = p;
        if (p.tier == Tier.BIG) spawnOrbs(28);
        else if (p.tier == Tier.MEDIUM) spawnOrbs(10);
    }

    public void showFail(ItemStack icon, String word, int removed) {
        active = null;
        card = newCard(Card.Kind.FAIL, icon, word, removed > 0 ? "LOST  " + removed + " deleted" : "LOST", 70);
        Popup p = new Popup();
        p.tier = Tier.MEDIUM;
        p.fail = true;
        p.icon = icon;
        p.word = removed > 0 ? word + "  ×" + removed : word;
        p.life = 50;
        popup = p;
    }

    public void showError(ItemStack icon, String message) {
        active = null;
        card = newCard(Card.Kind.ERROR, icon, "SAY IT", message, 90);
    }

    private static Card newCard(Card.Kind kind, ItemStack icon, String title, String line2, int life) {
        Card c = new Card();
        c.kind = kind;
        c.icon = icon == null ? ItemStack.EMPTY : icon;
        c.title = title;
        c.line2 = line2;
        c.life = life;
        return c;
    }

    static String pct(float score) { return Math.round(score * 100) + "%"; }

    // ------------------------------------------------------------------ tick

    /** @return seconds-left value that just crossed an integer in the last 3 s (for the tick sound), or -1 */
    public int tick(boolean paused) {
        int beep = -1;
        if (active != null && active.countingDown && !paused) {
            active.elapsed = Math.min(active.limitTicks, active.elapsed + 1);
            int s = (int) Math.ceil((active.limitTicks - active.elapsed) / 20f);
            if (s != active.lastTickSecond) {
                if (s <= 3 && s >= 1 && active.lastTickSecond != -1) beep = s;
                active.lastTickSecond = s;
            }
        }
        if (card != null && ++card.age > card.life + 10) card = null;
        if (popup != null && ++popup.age > popup.life) popup = null;
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            o.x += o.vx; o.y += o.vy; o.vy -= 0.02f; o.vx *= 0.97f;
            if (++o.age > o.life) it.remove();
        }
        return beep;
    }

    private void spawnOrbs(int n) {
        for (int i = 0; i < n; i++) {
            Orb o = new Orb();
            double a = random.nextDouble() * Math.PI * 2;
            float sp = 0.6f + random.nextFloat() * 1.8f;
            o.x = 0; o.y = 0;
            o.vx = (float) Math.cos(a) * sp * 1.6f;
            o.vy = (float) Math.sin(a) * sp - 0.8f;
            o.size = 2 + random.nextInt(3);
            o.life = 30 + random.nextInt(30);
            orbs.add(o);
        }
    }

    // ------------------------------------------------------------------ render

    public void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        if (popup != null) renderPopup(ctx, tr, sw, sh, tickDelta);
        if (active != null) renderChallenge(ctx, tr, sw, tickDelta);
        else if (card != null) renderCard(ctx, tr, sw, tickDelta);
        if (SayItConfig.get().showMicIndicator) renderMic(ctx, tr, sw, sh);
    }

    private static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private void drawScaled(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color, boolean shadow) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawText(tr, s, 0, 0, color, shadow);
        m.popMatrix();
    }

    private void drawItemScaled(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawItem(stack, 0, 0);
        m.popMatrix();
    }

    private void renderChallenge(DrawContext ctx, TextRenderer tr, int sw, float tickDelta) {
        Active a = active;
        String header = "SAY IT IN " + a.languageName.toUpperCase(java.util.Locale.ROOT);
        float secs = a.secondsLeft(tickDelta);
        boolean urgent = a.countingDown && secs <= 3f;
        int wordW = tr.getWidth(a.word) * 2;
        int w = Math.min(sw - 8, Math.max(200, 46 + Math.max(wordW, tr.getWidth(header) + 30) + 10));
        int h = 56;
        int x = (sw - w) / 2, y = 4;

        ctx.fill(x, y, x + w, y + h, PANEL);
        ctx.fill(x, y, x + 3, y + h, GOLD);
        drawItemScaled(ctx, a.icon, x + 8, y + 12, 2f);

        int tx = x + 46;
        ctx.drawText(tr, header, tx, y + 5, HEADER, false);
        String timer = a.countingDown ? (int) Math.ceil(secs) + "s" : "";
        ctx.drawText(tr, timer, x + w - 8 - tr.getWidth(timer), y + 5, urgent ? RED : HEADER, false);

        // the word, coloured per syllable after an attempt
        ctx.enableScissor(x, y, x + w - 4, y + h);
        float wx = tx, wy = y + 16;
        if (a.segments == null) {
            drawScaled(ctx, tr, a.word, wx, wy, 2f, WORD_GOLD, true);
        } else {
            for (WordMatcher.Segment s : a.segments) {
                int col = s.text().isBlank() ? WORD_GOLD : s.correct() ? GREEN : RED;
                drawScaled(ctx, tr, s.text(), wx, wy, 2f, col, true);
                int sw2 = tr.getWidth(s.text()) * 2;
                if (!s.correct() && !s.text().isBlank()) ctx.fill((int) wx, (int) wy + 18, (int) wx + sw2, (int) wy + 19, RED);
                wx += sw2;
            }
        }
        ctx.disableScissor();

        // timer bar
        int barX = tx, barY = y + 37, barMax = w - 46 - 10;
        float frac = a.countingDown ? secs / (a.limitTicks / 20f) : 1f;
        int barW = Math.max(0, Math.round(barMax * frac));
        ctx.fill(barX, barY, barX + barW, barY + 2, urgent ? RED : GOLD);
        if (urgent && barW > 6) ctx.fill(barX + barW - 6, barY, barX + barW, barY + 2, 0xFFFF2A3A);

        // status line
        int sy = y + 44;
        if (a.heard != null) {
            String p = pct(a.score);
            String q = a.lowConfidence ? "heard (unsure): \"" : "heard: \"";
            int maxW = w - 46 - 14 - tr.getWidth(p);
            String txt = tr.trimToWidth(q + a.heard, maxW - tr.getWidth("\"")) + "\"";
            ctx.drawText(tr, txt, tx, sy, RED, false);
            ctx.drawText(tr, p, x + w - 8 - tr.getWidth(p), sy, RED, false);
        } else {
            ctx.drawText(tr, a.status.isEmpty() ? "listening…" : a.status, tx, sy, STATUS_GRAY, false);
        }
    }

    private void renderCard(DrawContext ctx, TextRenderer tr, int sw, float tickDelta) {
        Card c = card;
        float t = c.age + tickDelta;
        float a = t < 4 ? t / 4f : t > c.life ? 1f - (t - c.life) / 10f : 1f;
        if (a <= 0.03f) return;
        int panel = c.kind == Card.Kind.SUCCESS ? OK_PANEL : FAIL_PANEL;
        int accent = c.kind == Card.Kind.SUCCESS ? OK_ACCENT : FAIL_ACCENT;
        int wordCol = c.kind == Card.Kind.SUCCESS ? OK_WORD : RED;
        if (c.kind == Card.Kind.ERROR) {
            int w = Math.min(sw - 8, Math.max(180, tr.getWidth(c.line2) + 50));
            int x = (sw - w) / 2, y = 4, h = 32;
            ctx.fill(x, y, x + w, y + h, alpha(panel, a));
            ctx.fill(x, y, x + 3, y + h, alpha(accent, a));
            drawItemScaled(ctx, c.icon, x + 8, y + 8, 1f);
            ctx.drawText(tr, "SAY IT - ITEM KEPT", x + 30, y + 6, alpha(HEADER, a), false);
            ctx.drawText(tr, tr.trimToWidth(c.line2, w - 36), x + 30, y + 18, alpha(RED, a), false);
            return;
        }
        int titleW = tr.getWidth(c.title) * 2;
        int w = Math.min(sw - 8, Math.max(130, titleW + 30));
        int h = 38, x = (sw - w) / 2, y = 4;
        ctx.fill(x, y, x + w, y + h, alpha(panel, a));
        ctx.fill(x, y, x + 3, y + h, alpha(accent, a));
        drawScaled(ctx, tr, c.title, x + (w - titleW) / 2f + 1, y + 4, 2f, alpha(wordCol, a), true);
        if (c.kind == Card.Kind.FAIL) ctx.fill(x + (w - titleW) / 2 + 1, y + 12, x + (w + titleW) / 2 + 1, y + 13, alpha(RED, a));
        int l2 = tr.getWidth(c.line2);
        ctx.drawText(tr, c.line2, x + (w - l2) / 2, y + 26, alpha(0xFFFFFFFF, a), false);
    }

    private void renderPopup(DrawContext ctx, TextRenderer tr, int sw, int sh, float tickDelta) {
        Popup p = popup;
        float t = p.age + tickDelta;
        float a = t < 3 ? t / 3f : t > p.life - 10 ? Math.max(0, (p.life - t) / 10f) : 1f;
        if (a <= 0.03f) return;
        float base = p.tier == Tier.SMALL ? 1.5f : p.tier == Tier.MEDIUM ? 2.2f : 3.6f;
        float pop = t < 5 ? 1f + 0.5f * (1f - t / 5f) : 1f; // overshoot, then settle
        float scale = base * pop;
        String label = p.fail ? "LOST!" : p.tier == Tier.BIG ? "UNLOCKED!" : "UNLOCKED";
        float cx = sw / 2f, cy = sh / 2f - 22 - (p.tier == Tier.BIG ? 6 : 0);
        Matrix3x2fStack m = ctx.getMatrices();

        if (p.tier == Tier.BIG && !p.fail) {
            int rays = 14;
            for (int i = 0; i < rays; i++) {
                m.pushMatrix();
                m.translate(cx, cy + 4);
                m.rotate((float) (i * Math.PI * 2 / rays + t * 0.01f));
                int len = (int) (i % 2 == 0 ? sw * 0.6f : sw * 0.4f);
                ctx.fill(0, -4, len, 4, alpha(0x30A8FFB8, a));
                m.popMatrix();
            }
            for (Orb o : orbs) {
                float oa = a * (1f - (float) o.age / o.life);
                int ox = (int) (cx + o.x * 3), oy = (int) (cy + o.y * 3);
                int s = (int) o.size;
                ctx.fill(ox, oy, ox + s, oy + s, alpha(0xFF8CFF6A, oa));
                ctx.fill(ox + 1, oy + 1, ox + s - 1, oy + s - 1, alpha(0xFFE2FF9A, oa));
            }
        } else if (!orbs.isEmpty()) {
            for (Orb o : orbs) {
                float oa = a * (1f - (float) o.age / o.life);
                int ox = (int) (cx + o.x * 2), oy = (int) (cy + o.y * 2);
                ctx.fill(ox, oy, ox + 2, oy + 2, alpha(0xFF8CFF6A, oa));
            }
        }

        int textW = tr.getWidth(label);
        float iconSize = 16 * Math.max(1f, scale * 0.5f);
        float totalW = textW * scale + iconSize + 4;
        float left = cx - totalW / 2f;
        drawItemScaled(ctx, p.icon, left, cy + (9 * scale - iconSize) / 2f, iconSize / 16f);

        int main = alpha(p.fail ? POP_RED : POP_GREEN, a), outline = alpha(p.fail ? POP_RED_OUTLINE : POP_OUTLINE, a);
        m.pushMatrix();
        m.translate(left + iconSize + 4, cy);
        m.scale(scale, scale);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) ctx.drawText(tr, label, dx, dy, outline, false);
        ctx.drawText(tr, label, 0, 0, main, false);
        m.popMatrix();

        if (p.tier != Tier.SMALL) {
            int ww = tr.getWidth(p.word);
            ctx.drawText(tr, p.word, (int) (cx - ww / 2f), (int) (cy + 9 * scale + 3), alpha(0xFFFFFFFF, a), true);
        }
    }

    private void renderMic(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        int hotbarLeft = sw / 2 - 91;
        int tw = tr.getWidth(micText);
        int x2 = hotbarLeft - 3, x1 = x2 - tw - 12, y1 = sh - 18, y2 = sh - 5;
        if (x1 < 2) return;
        ctx.fill(x1, y1, x2, y2, 0x90101418);
        // level meter (vertical, left)
        int mh = y2 - y1 - 4;
        int lv = micLive ? Math.round(mh * Math.min(1f, micLevel)) : 0;
        ctx.fill(x1 + 2, y1 + 2, x1 + 5, y2 - 2, 0x60FFFFFF);
        if (lv > 0) ctx.fill(x1 + 2, y2 - 2 - lv, x1 + 5, y2 - 2, micLevel > 0.85f ? 0xFFFFB13B : 0xFF5BE37D);
        ctx.drawText(tr, micText, x1 + 8, y1 + 3, micColor, false);
    }
}
