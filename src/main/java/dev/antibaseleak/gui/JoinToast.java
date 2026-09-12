package dev.antibaseleak.gui;

import dev.antibaseleak.AntiBaseLeakClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A panel that slides in from the right edge after joining a world.
 *
 * <p>The animation is driven by the system clock rather than by ticks, so how smooth
 * it looks does not depend on whether the game runs at 20 or 240 FPS, and it does not
 * slow down when the server lags.
 */
public final class JoinToast {

    private static final long SLIDE_IN_MS = 320L;
    private static final long HOLD_MS = 4200L;
    private static final long SLIDE_OUT_MS = 420L;

    private static final int MARGIN = 8;
    private static final int HEIGHT = 32;
    private static final int ACCENT_WIDTH = 3;
    private static final int PADDING = 8;

    private static final int COLOR_ACCENT = 0x55FF55;
    private static final int COLOR_TITLE = 0x8AB4FF;
    private static final int COLOR_HINT = 0xE6E6E6;

    private static long startTime = -1L;

    private JoinToast() {
    }

    public static void show() {
        startTime = System.currentTimeMillis();
    }

    public static void hide() {
        startTime = -1L;
    }

    public static void render(DrawContext context) {
        if (startTime < 0L) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        long total = SLIDE_IN_MS + HOLD_MS + SLIDE_OUT_MS;
        if (elapsed >= total) {
            startTime = -1L;
            return;
        }

        // 0 = hidden past the screen edge, 1 = fully slid out
        float slide;
        if (elapsed < SLIDE_IN_MS) {
            slide = easeOut((float) elapsed / SLIDE_IN_MS);
        } else if (elapsed < SLIDE_IN_MS + HOLD_MS) {
            slide = 1.0F;
        } else {
            slide = 1.0F - easeIn((float) (elapsed - SLIDE_IN_MS - HOLD_MS) / SLIDE_OUT_MS);
        }

        Text title = Text.literal("AntiBaseLeak");
        Text hint = hintText();
        int contentWidth = Math.max(textRenderer.getWidth(title), textRenderer.getWidth(hint));
        int width = ACCENT_WIDTH + PADDING + contentWidth + PADDING;

        int screenWidth = context.getScaledWindowWidth();
        // At slide = 0 the panel sits just past the edge, at 1 it is in place.
        int x = screenWidth - Math.round(slide * (width + MARGIN));
        int y = MARGIN;

        // Shadow, softly gradiented background, accent bar and border
        context.fill(x + 2, y + 2, x + width + 2, y + HEIGHT + 2, alpha(0x000000, slide * 0.35F));
        context.fillGradient(x, y, x + width, y + HEIGHT, alpha(0x14141A, slide * 0.94F), alpha(0x1E1E28, slide * 0.94F));
        context.fill(x, y, x + ACCENT_WIDTH, y + HEIGHT, alpha(COLOR_ACCENT, slide));
        context.fill(x + ACCENT_WIDTH, y, x + width, y + 1, alpha(0xFFFFFF, slide * 0.20F));
        context.fill(x + ACCENT_WIDTH, y + HEIGHT - 1, x + width, y + HEIGHT, alpha(0xFFFFFF, slide * 0.10F));

        int textX = x + ACCENT_WIDTH + PADDING;
        context.drawTextWithShadow(textRenderer, title, textX, y + 7, alpha(COLOR_TITLE, slide));
        context.drawTextWithShadow(textRenderer, hint, textX, y + 19, alpha(COLOR_HINT, slide));

        // Bar counting down the time left before it hides
        if (elapsed > SLIDE_IN_MS && elapsed < SLIDE_IN_MS + HOLD_MS) {
            float left = 1.0F - (float) (elapsed - SLIDE_IN_MS) / HOLD_MS;
            int barWidth = Math.round((width - ACCENT_WIDTH) * left);
            context.fill(x + ACCENT_WIDTH, y + HEIGHT - 1, x + ACCENT_WIDTH + barWidth, y + HEIGHT,
                    alpha(COLOR_ACCENT, slide * 0.65F));
        }
    }

    private static Text hintText() {
        KeyBinding key = AntiBaseLeakClient.menuKey();
        Text keyName = key == null || key.isUnbound()
                ? Text.translatable("antibaseleak.toast.unbound")
                : key.getBoundKeyLocalizedText();
        return Text.translatable("antibaseleak.toast.hint",
                Text.literal("[").append(keyName).append("]").formatted(Formatting.YELLOW));
    }

    private static int alpha(int rgb, float factor) {
        int a = Math.round(255 * Math.max(0.0F, Math.min(1.0F, factor)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static float easeIn(float t) {
        return t * t * t;
    }
}
