package dev.antibaseleak;

/**
 * Whether each hook has actually fired.
 *
 * <p>The mod is meant to prevent a base leak, so a silent failure - a mixin that
 * stopped matching after a game update, say - would be the worst possible outcome.
 * That is why every hook records its first use here and the F7 menu shows it.
 *
 * <p>Plain static fields, no volatile: writes come from render threads and reads only
 * from the GUI, so a delayed update does not matter, while a memory barrier inside
 * the block rendering loop would cost FPS.
 */
public final class FeatureStatus {

    public static boolean debugHudHooked;
    public static boolean renderMaskHooked;
    public static boolean modelSeedHooked;
    public static boolean modelOffsetHooked;
    public static boolean chatHooked;
    public static boolean blurHooked;

    private FeatureStatus() {
    }
}
