package dev.antibaseleak.config;

/**
 * How an F3 line is censored.
 *
 * <p>MASK - replaces the value with masking characters.
 * <p>HIDE - drops the whole line.
 * <p>BLUR - keeps the line and blurs it on screen with a shader: the
 *           "minecraft:blur" post effect, the same one behind the pause menu.
 */
public enum CensorStyle {
    MASK,
    HIDE,
    BLUR
}
