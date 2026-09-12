package dev.antibaseleak.config;

/**
 * Where bedrock gets replaced.
 *
 * <p>RENDER - only in the data handed to the chunk builder (vanilla renderer).
 *             Nothing in the client world changes and the switch is instant, but it
 *             does NOT work with Sodium/Embeddium, which have their own pipeline.
 * <p>WORLD  - replaces blocks in the client side copy of the world right after a
 *             chunk is loaded. Works with every renderer (Sodium, Iris, Nvidium) and
 *             also removes bedrock from the F3 "Targeted Block" line.
 * <p>AUTO   - WORLD when a Sodium-like renderer is detected, RENDER otherwise.
 */
public enum MaskMode {
    AUTO,
    RENDER,
    WORLD
}
