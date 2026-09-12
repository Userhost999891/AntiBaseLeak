package dev.antibaseleak.config;

/**
 * What bedrock is replaced with.
 *
 * <p>NEAREST - texture of the closest ordinary block (the most natural looking result).
 * <p>SECTION - one block, the most common in the whole 16x16x16 section (no structure left).
 * <p>FIXED   - always the same, explicitly chosen block (maximum uniformity).
 */
public enum CoverMode {
    NEAREST,
    SECTION,
    FIXED
}
