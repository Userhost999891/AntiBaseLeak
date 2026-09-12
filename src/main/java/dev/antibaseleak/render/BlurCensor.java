package dev.antibaseleak.render;

import dev.antibaseleak.AntiBaseLeak;
import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.censor.DebugCensor;
import dev.antibaseleak.config.AblConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The renderer independent half of the blur censor.
 *
 * <p>While the F3 screen is drawn we collect the rectangles (in GUI coordinates) that
 * have to be blurred, and the blur itself happens at the end of the frame, on the
 * finished framebuffer. It has to work this way because GUI drawing is deferred since
 * 1.21.9 - when the text is "drawn" it is not in the framebuffer yet.
 *
 * <p>A welcome side effect is that the blur lands in the framebuffer before it is
 * presented, so a screenshot (F2) and OBS capture it just the same.
 */
public final class BlurCensor {

    private static final int MAX_RECTS = 64;
    private static final List<int[]> RECTS = new ArrayList<>();

    /** Set once the blur fails: from then on we fall back to masking with text. */
    private static boolean broken;

    /**
     * Development probe (-Dantibaseleak.blurtest=true): blurs a fixed rectangle in the
     * top left corner so the effect can be checked without opening F3.
     */
    private static final boolean PROBE = Boolean.getBoolean("antibaseleak.blurtest");

    private BlurCensor() {
    }

    /** Whether the blur can be expected to work in this frame at all. */
    public static boolean available() {
        if (broken) return false;
        return ScreenBlur.isAvailable();
    }

    /**
     * Collects the rectangles to blur for one F3 column.
     *
     * <p>The line layout has been stable in the game for years - a 2 px margin, 9 px
     * line height, the right column aligned to the right edge - so we compute the
     * positions exactly the way vanilla does.
     */
    public static void queueLines(List<String> lines, DrawContext context, boolean leftColumn, AblConfig cfg) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) return;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            if (DebugCensor.kindOf(line, cfg) == null) continue;

            int width = textRenderer.getWidth(line);
            int x = leftColumn ? 2 : context.getScaledWindowWidth() - 2 - width;
            int y = 2 + i * 9;
            int start = x + textRenderer.getWidth(line.substring(0, DebugCensor.sensitiveStart(line)));

            add(start - 1, y - 1, x + width + 1, y + 9);
        }
    }

    public static void add(int x1, int y1, int x2, int y2) {
        if (RECTS.size() >= MAX_RECTS || x2 <= x1 || y2 <= y1) return;
        RECTS.add(new int[]{x1, y1, x2, y2});
    }

    public static boolean hasQueued() {
        return !RECTS.isEmpty();
    }

    /** Called at the end of the frame, once the GUI is in the framebuffer. */
    public static void applyQueued() {
        if (PROBE) add(90, 25, 330, 70);
        if (RECTS.isEmpty()) return;
        List<int[]> rects = new ArrayList<>(RECTS);
        RECTS.clear();

        try {
            if (ScreenBlur.apply(rects, AblConfig.get().blurStrength)) {
                FeatureStatus.blurHooked = true;
            }
        } catch (Throwable t) {
            // A silent failure would leave the coordinates on screen, so report it and
            // go back to masking them with text.
            broken = true;
            AntiBaseLeak.LOG.error("Blur failed, falling back to text masking", t);
        }

    }

    public static boolean isBroken() {
        return broken;
    }
}
