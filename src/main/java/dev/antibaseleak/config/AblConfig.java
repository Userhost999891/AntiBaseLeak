package dev.antibaseleak.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.antibaseleak.AntiBaseLeak;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod settings. Kept in a single object that is swapped as a whole when the file
 * is loaded, which makes a read from a render thread an ordinary field read - no
 * synchronisation and no null checks.
 */
public final class AblConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile AblConfig instance = new AblConfig();

    // ─── Bedrock masking ────────────────────────────────────────────────────
    public boolean maskBedrock = true;
    public MaskMode maskMode = MaskMode.AUTO;
    public CoverMode coverMode = CoverMode.NEAREST;
    /** How far NEAREST looks for the closest block (1-4). */
    public int searchRadius = 3;
    /** Block used by FIXED mode. */
    public String fixedBlock = "minecraft:stone";

    // ─── Model randomness (leaks coordinates just like bedrock) ─────────────
    public boolean disableModelRotation = true;
    public boolean disableModelOffset = true;

    // ─── F3 censoring ───────────────────────────────────────────────────────
    public boolean censorDebug = true;
    public boolean censorPosition = true;
    public boolean censorTargetBlock = true;
    public boolean censorTargetFluid = true;
    public boolean censorTargetEntity = true;
    public boolean censorBiome = true;
    public boolean censorDimension = true;
    public boolean censorServer = true;
    public boolean censorDifficulty = false;
    public boolean censorOtherCoords = true;
    public CensorStyle censorStyle = CensorStyle.MASK;
    public String maskText = "###";
    /** Number of blur passes (1-4) - the more, the stronger the blur. */
    public int blurStrength = 2;

    // ─── Chat censoring ─────────────────────────────────────────────────────
    public boolean censorChat = false;

    // ─── Interface ──────────────────────────────────────────────────────────
    /** Notification with the menu key hint, shown after joining a world. */
    public boolean showJoinToast = true;

    public static AblConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(AntiBaseLeak.MOD_ID + ".json");
    }

    public static void load() {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            AblConfig loaded = GSON.fromJson(reader, AblConfig.class);
            if (loaded != null) {
                loaded.sanitize();
                instance = loaded;
            }
        } catch (Exception e) {
            AntiBaseLeak.LOG.warn("Could not read the config, using defaults", e);
        }
    }

    public static void save() {
        AblConfig cfg = instance;
        cfg.sanitize();
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            AntiBaseLeak.LOG.warn("Could not write the config", e);
        }
    }

    public static void resetToDefaults() {
        instance = new AblConfig();
        save();
    }

    private void sanitize() {
        if (maskMode == null) maskMode = MaskMode.AUTO;
        if (coverMode == null) coverMode = CoverMode.NEAREST;
        if (censorStyle == null) censorStyle = CensorStyle.MASK;
        if (fixedBlock == null || fixedBlock.isBlank()) fixedBlock = "minecraft:stone";
        if (maskText == null || maskText.isEmpty()) maskText = "###";
        searchRadius = Math.max(1, Math.min(4, searchRadius));
        blurStrength = Math.max(1, Math.min(4, blurStrength));
    }
}
