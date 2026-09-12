package dev.antibaseleak;

import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.CoverMode;
import dev.antibaseleak.mask.BedrockMask;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A self test of the masking algorithm, run with {@code -Dantibaseleak.selftest=true}.
 *
 * <p>It checks the logic against the real game classes, with the registries already
 * loaded, so after a Minecraft update it is immediately clear whether picking the
 * replacement block still works - without entering a world and eyeballing the Nether
 * ceiling.
 */
public final class SelfTest {

    private static int failures;

    private SelfTest() {
    }

    public static void run() {
        failures = 0;
        AblConfig snapshot = AblConfig.get();
        CoverMode originalMode = snapshot.coverMode;
        String originalFixed = snapshot.fixedBlock;

        testMixinTargets();
        testSuitable();
        testNetherRoof();
        testOverworldBottom();
        testSolidBedrock();
        testSectionAndFixed();

        snapshot.coverMode = originalMode;
        snapshot.fixedBlock = originalFixed;
        BedrockMask.clearCache();

        if (failures == 0) {
            AntiBaseLeak.LOG.info("[selftest] all checks passed");
        } else {
            AntiBaseLeak.LOG.error("[selftest] failed checks: {}", failures);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            AntiBaseLeak.LOG.info("[selftest] ok   {} - {}", name, detail);
        } else {
            AntiBaseLeak.LOG.error("[selftest] FAIL {} - {}", name, detail);
            failures++;
        }
    }

    private static String id(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    /**
     * Forces the classes the mixins target to load. The transformation happens at class
     * load time, so a failed injection blows up right here instead of only once the
     * player enters a world.
     */
    private static void testMixinTargets() {
        String[] targets = {
                "net.minecraft.client.world.ClientWorld",
                "net.minecraft.client.render.chunk.ChunkRendererRegion",
                "net.minecraft.client.gui.hud.DebugHud",
                "net.minecraft.client.gui.hud.ChatHud",
                "net.minecraft.client.gui.hud.InGameHud",
                "net.minecraft.client.render.GameRenderer",
                "net.minecraft.block.AbstractBlock"
        };
        for (String target : targets) {
            try {
                Class.forName(target, false, SelfTest.class.getClassLoader());
                check("mixin: " + target, true, "class loaded with the mixin applied");
            } catch (Throwable t) {
                check("mixin: " + target, false, String.valueOf(t));
            }
        }
    }

    private static void testSuitable() {
        check("stone", BedrockMask.suitable(Blocks.STONE.getDefaultState()), "candidate");
        check("netherrack", BedrockMask.suitable(Blocks.NETHERRACK.getDefaultState()), "candidate");
        check("air", !BedrockMask.suitable(Blocks.AIR.getDefaultState()), "rejected");
        check("bedrock", !BedrockMask.suitable(Blocks.BEDROCK.getDefaultState()), "rejected");
        check("water", !BedrockMask.suitable(Blocks.WATER.getDefaultState()), "rejected");
        check("glass", !BedrockMask.suitable(Blocks.GLASS.getDefaultState()), "rejected (transparent)");
        check("leaves", !BedrockMask.suitable(Blocks.OAK_LEAVES.getDefaultState()), "rejected");
        check("chest", !BedrockMask.suitable(Blocks.CHEST.getDefaultState()), "rejected (block entity)");
        check("stairs", !BedrockMask.suitable(Blocks.STONE_STAIRS.getDefaultState()), "rejected (not a full cube)");
        check("grass", !BedrockMask.suitable(Blocks.SHORT_GRASS.getDefaultState()), "rejected");
    }

    /** Nether ceiling: netherrack up to y=122, random bedrock 123-126, solid bedrock at 127, void above. */
    private static void testNetherRoof() {
        Map<Long, Boolean> pattern = new HashMap<>();
        BedrockMask.Sampler sampler = (x, y, z) -> {
            if (y > 127 || y < 0) return Blocks.AIR.getDefaultState();
            if (y == 127) return Blocks.BEDROCK.getDefaultState();
            if (y >= 123) {
                long key = (((long) x) << 40) ^ (((long) y) << 20) ^ z;
                boolean bedrock = pattern.computeIfAbsent(key, k -> new Random(k).nextInt(4) < 3);
                return bedrock ? Blocks.BEDROCK.getDefaultState() : Blocks.NETHERRACK.getDefaultState();
            }
            return Blocks.NETHERRACK.getDefaultState();
        };

        BlockState fallback = Blocks.NETHERRACK.getDefaultState();
        Map<String, Integer> results = new HashMap<>();
        int bedrockSeen = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 123; y <= 127; y++) {
                    if (!sampler.get(x, y, z).isOf(Blocks.BEDROCK)) continue;
                    bedrockSeen++;
                    results.merge(id(BedrockMask.replacement(sampler, x, y, z, fallback)), 1, Integer::sum);
                }
            }
        }
        check("nether ceiling: sample", bedrockSeen > 100, bedrockSeen + " bedrock blocks");
        check("nether ceiling: no bedrock left", !results.containsKey("minecraft:bedrock"), results.toString());
        check("nether ceiling: uniform netherrack",
                results.size() == 1 && results.containsKey("minecraft:netherrack"), results.toString());
    }

    /** Overworld floor: deepslate above, bedrock -64..-60, void below. */
    private static void testOverworldBottom() {
        BedrockMask.Sampler sampler = (x, y, z) -> {
            if (y < -64) return Blocks.AIR.getDefaultState();
            if (y <= -60) return Blocks.BEDROCK.getDefaultState();
            return Blocks.DEEPSLATE.getDefaultState();
        };
        BlockState fallback = Blocks.DEEPSLATE.getDefaultState();
        Map<String, Integer> results = new HashMap<>();
        for (int y = -64; y <= -60; y++) {
            for (int x = 0; x < 16; x++) {
                results.merge(id(BedrockMask.replacement(sampler, x, y, 0, fallback)), 1, Integer::sum);
            }
        }
        check("world floor: uniform deepslate",
                results.size() == 1 && results.containsKey("minecraft:deepslate"), results.toString());
    }

    /** A section filled with nothing but bedrock - the fallback block has to kick in. */
    private static void testSolidBedrock() {
        BedrockMask.Sampler sampler = (x, y, z) -> Blocks.BEDROCK.getDefaultState();
        BlockState result = BedrockMask.replacement(sampler, 5, 5, 5, Blocks.NETHERRACK.getDefaultState());
        check("solid section: fallback", result.isOf(Blocks.NETHERRACK), id(result));
    }

    private static void testSectionAndFixed() {
        BedrockMask.clearCache();
        // Section: one quarter basalt, three quarters netherrack, a bedrock column in the middle.
        BedrockMask.Sampler sampler = (x, y, z) -> {
            if (x == 8 && z == 8) return Blocks.BEDROCK.getDefaultState();
            return z < 4 ? Blocks.BASALT.getDefaultState() : Blocks.NETHERRACK.getDefaultState();
        };
        BlockState fallback = Blocks.STONE.getDefaultState();
        AblConfig cfg = AblConfig.get();

        cfg.coverMode = CoverMode.SECTION;
        BlockState a = BedrockMask.replacement(sampler, 8, 5, 8, fallback);
        BlockState b = BedrockMask.replacement(sampler, 8, 9, 8, fallback);
        check("SECTION: most common block", a.isOf(Blocks.NETHERRACK), id(a));
        check("SECTION: uniform within a section", a == b, id(a) + " / " + id(b));

        cfg.coverMode = CoverMode.FIXED;
        cfg.fixedBlock = "minecraft:obsidian";
        BlockState c = BedrockMask.replacement(sampler, 8, 5, 8, fallback);
        check("FIXED: the chosen block", c.isOf(Blocks.OBSIDIAN), id(c));

        cfg.fixedBlock = "somemod:no_such_block";
        BlockState d = BedrockMask.replacement(sampler, 8, 5, 8, fallback);
        check("FIXED: bad id falls back", d.isOf(Blocks.STONE), id(d));
    }
}
