package dev.antibaseleak.censor;

import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.config.AblConfig;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional censoring of coordinates in chat - death messages like "you died at
 * 123 64 -900", coordinates shared by friends, /home listings. On a screenshot with
 * the chat open they leak exactly like the F3 screen does.
 *
 * <p>Off by default: the replacement turns a formatted message into plain text, so it
 * is something you enable deliberately, for example before a stream.
 */
public final class ChatCensor {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern COORD_TRIPLE =
            Pattern.compile("-?\\d{2,}(?:\\.\\d+)?(?:[ ,/]+-?\\d+(?:\\.\\d+)?){2,}");
    private static final Pattern LABELED_COORD =
            Pattern.compile("(?i)\\b[xyz]\\s*[:=]\\s*-?\\d+");

    private ChatCensor() {
    }

    public static Text censor(Text message) {
        if (message == null) return null;
        FeatureStatus.chatHooked = true;

        AblConfig cfg = AblConfig.get();
        if (!cfg.censorChat) return message;

        String flat = message.getString();
        if (!looksLikeCoords(flat)) return message;

        String masked = NUMBER.matcher(flat).replaceAll(Matcher.quoteReplacement(cfg.maskText));
        return Text.literal(masked).setStyle(message.getStyle());
    }

    public static boolean looksLikeCoords(String text) {
        return COORD_TRIPLE.matcher(text).find() || LABELED_COORD.matcher(text).find();
    }
}
