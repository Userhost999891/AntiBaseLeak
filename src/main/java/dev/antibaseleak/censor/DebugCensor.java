package dev.antibaseleak.censor;

import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.CensorStyle;
import dev.antibaseleak.render.BlurCensor;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Censoring of the debug screen (F3).
 *
 * <p>Finished lines are filtered right before they are drawn, not where they are
 * built. That way the same code covers 1.21.4, where one big method builds the list,
 * and 1.21.9+, where debug entries are modular - and it also catches lines added by
 * other mods (Sodium, Iris, clients), which often show coordinates unasked.
 */
public final class DebugCensor {

    /** What to do with the value in a line. */
    public enum Kind {
        /** Mask every number in the line (coordinates). */
        NUMBERS,
        /** Mask everything after the first colon (biome name, address). */
        VALUE,
        /** Mask namespace:path identifiers (dimension). */
        IDENTIFIER
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    /** Three or more numbers separated only by a space, comma or slash. */
    private static final Pattern COORD_TRIPLE =
            Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[ ,/]+-?\\d+(?:\\.\\d+)?){2,}");
    private static final Pattern IPV4 =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
    private static final Pattern IDENTIFIER =
            Pattern.compile("\\b[a-z0-9_.-]+:[a-z0-9_./-]+\\b");

    private DebugCensor() {
    }

    public static List<String> censor(List<String> lines, DrawContext context, boolean leftColumn) {
        if (lines == null) return null;
        FeatureStatus.debugHudHooked = true;

        AblConfig cfg = AblConfig.get();
        if (!cfg.censorDebug) return lines;

        // The blur is applied at the end of the frame, on the finished framebuffer.
        // Should it be unavailable for any reason we do NOT leave the coordinates
        // exposed - we fall back to masking them with text.
        if (cfg.censorStyle == CensorStyle.BLUR && BlurCensor.available()) {
            BlurCensor.queueLines(lines, context, leftColumn, cfg);
            return lines;
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String censored = censorLine(line, cfg);
            if (censored != null) out.add(censored);
        }
        return out;
    }

    /** Returns the censor kind for a line, or null when the line is harmless. */
    public static Kind kindOf(String line, AblConfig cfg) {
        if (line == null || line.isEmpty()) return null;
        String t = line.strip();

        if (cfg.censorPosition && startsWithAny(t, "XYZ:", "Block:", "Chunk:", "Chunk-relative:", "Section:")) {
            return Kind.NUMBERS;
        }
        if (cfg.censorTargetBlock && startsWithAny(t, "Targeted Block", "Looking at block")) {
            return Kind.NUMBERS;
        }
        if (cfg.censorTargetFluid && startsWithAny(t, "Targeted Fluid", "Looking at fluid")) {
            return Kind.NUMBERS;
        }
        if (cfg.censorTargetEntity && startsWithAny(t, "Targeted Entity", "Looking at entity")) {
            return Kind.NUMBERS;
        }
        if (cfg.censorBiome && startsWithAny(t, "Biome:")) {
            return Kind.VALUE;
        }
        if (cfg.censorDimension && (startsWithAny(t, "Dimension:") || isDimensionLine(t))) {
            return Kind.IDENTIFIER;
        }
        if (cfg.censorDifficulty && startsWithAny(t, "Local Difficulty")) {
            return Kind.NUMBERS;
        }
        if (cfg.censorServer && (startsWithAny(t, "Server:", "Server ") || IPV4.matcher(t).find())) {
            return Kind.VALUE;
        }
        if (cfg.censorOtherCoords && COORD_TRIPLE.matcher(t).find()) {
            return Kind.NUMBERS;
        }
        return null;
    }

    /**
     * Where the sensitive part of a line starts. The label ("XYZ:", "Biome:") stays
     * readable - only the value is blurred.
     */
    public static int sensitiveStart(String line) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon > 24) return 0;
        int start = colon + 1;
        while (start < line.length() && line.charAt(start) == ' ') start++;
        return start;
    }

    /** Returns the modified line, or null when the line should disappear. */
    public static String censorLine(String line, AblConfig cfg) {
        Kind kind = kindOf(line, cfg);
        if (kind == null) return line;
        if (cfg.censorStyle == CensorStyle.HIDE) return null;

        String mask = Matcher.quoteReplacement(cfg.maskText);
        return switch (kind) {
            case NUMBERS -> NUMBER.matcher(line).replaceAll(mask);
            case IDENTIFIER -> IDENTIFIER.matcher(line).replaceAll(mask);
            case VALUE -> {
                int colon = line.indexOf(':');
                yield colon < 0 ? cfg.maskText : line.substring(0, colon + 1) + " " + cfg.maskText;
            }
        };
    }

    private static boolean isDimensionLine(String line) {
        return line.contains("minecraft:overworld")
                || line.contains("minecraft:the_nether")
                || line.contains("minecraft:the_end");
    }

    private static boolean startsWithAny(String line, String... prefixes) {
        for (String prefix : prefixes) {
            if (line.startsWith(prefix)) return true;
        }
        return false;
    }
}
