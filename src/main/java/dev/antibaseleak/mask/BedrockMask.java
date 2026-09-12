package dev.antibaseleak.mask;

import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.MaskMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks the block that bedrock is replaced with.
 *
 * <p>The goal: after the replacement NO trace of the bedrock pattern may remain on
 * screen, because that pattern is a deterministic function of the coordinates - one
 * screenshot of the Nether ceiling is enough to work out where a base is. That is why
 * the replacement is chosen purely from the surrounding terrain, which gives nothing
 * away on its own, and never from the shape of the bedrock itself.
 */
public final class BedrockMask {

    /** Reads a block state in world coordinates. */
    @FunctionalInterface
    public interface Sampler {
        BlockState get(int x, int y, int z);
    }

    private static final int MAX_RADIUS = 4;

    /** Offsets sorted by increasing distance (a flat x,y,z list). */
    private static final int[] OFFSETS;
    /** Squared distance of the i-th offset (a parallel array). */
    private static final int[] OFFSET_DIST2;

    /** The most common ordinary block in a 16^3 section - computed once per section. */
    private static final Map<Long, BlockState> SECTION_COVER = new ConcurrentHashMap<>();

    private static volatile String cachedFixedId;
    private static volatile BlockState cachedFixedState;
    private static volatile Boolean customRenderer;

    static {
        List<int[]> list = new ArrayList<>();
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            for (int dy = -MAX_RADIUS; dy <= MAX_RADIUS; dy++) {
                for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    list.add(new int[]{dx, dy, dz, dx * dx + dy * dy + dz * dz});
                }
            }
        }
        // A stable, deterministic order: closest first, and on a tie the block below
        // wins, because on ceilings we look at the underside of the bedrock layer.
        list.sort((a, b) -> {
            if (a[3] != b[3]) return Integer.compare(a[3], b[3]);
            if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
            return Integer.compare(a[2], b[2]);
        });
        OFFSETS = new int[list.size() * 3];
        OFFSET_DIST2 = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            int[] o = list.get(i);
            OFFSETS[i * 3] = o[0];
            OFFSETS[i * 3 + 1] = o[1];
            OFFSETS[i * 3 + 2] = o[2];
            OFFSET_DIST2[i] = o[3];
        }
    }

    private BedrockMask() {
    }

    public static void clearCache() {
        SECTION_COVER.clear();
    }

    public static boolean isBedrock(BlockState state) {
        return state != null && state.isOf(Blocks.BEDROCK);
    }

    /** The effective mode: resolves AUTO to RENDER or WORLD based on installed mods. */
    public static MaskMode effectiveMode() {
        MaskMode mode = AblConfig.get().maskMode;
        if (mode != MaskMode.AUTO) return mode;
        return hasCustomRenderer() ? MaskMode.WORLD : MaskMode.RENDER;
    }

    /**
     * Sodium and its relatives build chunks with their own code and never touch
     * ChunkRendererRegion, so with them the masking has to go through world data.
     */
    public static boolean hasCustomRenderer() {
        Boolean cached = customRenderer;
        if (cached != null) return cached;
        FabricLoader loader = FabricLoader.getInstance();
        boolean found = loader.isModLoaded("sodium")
                || loader.isModLoaded("embeddium")
                || loader.isModLoaded("rubidium")
                || loader.isModLoaded("nvidium")
                || loader.isModLoaded("vulkanmod");
        customRenderer = found;
        return found;
    }

    /** Fallback block for when nothing usable is nearby, e.g. a solid bedrock layer. */
    public static BlockState fallbackFor(World world, int y) {
        if (world != null) {
            if (world.getRegistryKey() == World.NETHER) return Blocks.NETHERRACK.getDefaultState();
            if (world.getRegistryKey() == World.END) return Blocks.END_STONE.getDefaultState();
        }
        return y < 0 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    /**
     * Returns the block that bedrock should be shown as in a given spot. Never throws -
     * on any trouble it returns the fallback.
     */
    public static BlockState replacement(Sampler sampler, int x, int y, int z, BlockState fallback) {
        AblConfig cfg = AblConfig.get();
        try {
            return switch (cfg.coverMode) {
                case FIXED -> fixed(cfg, fallback);
                case SECTION -> sectionCover(sampler, x, y, z, fallback);
                case NEAREST -> nearest(sampler, cfg, x, y, z, fallback);
            };
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static BlockState fixed(AblConfig cfg, BlockState fallback) {
        String id = cfg.fixedBlock;
        if (!id.equals(cachedFixedId)) {
            BlockState resolved = null;
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) {
                Block block = Registries.BLOCK.get(parsed);
                if (block != null && block != Blocks.AIR) resolved = block.getDefaultState();
            }
            cachedFixedState = resolved;
            cachedFixedId = id;
        }
        BlockState state = cachedFixedState;
        return state != null ? state : fallback;
    }

    private static BlockState nearest(Sampler sampler, AblConfig cfg, int x, int y, int z, BlockState fallback) {
        int maxDist2 = cfg.searchRadius * cfg.searchRadius;
        for (int i = 0; i < OFFSET_DIST2.length; i++) {
            if (OFFSET_DIST2[i] > maxDist2) break;
            BlockState state = sampler.get(x + OFFSETS[i * 3], y + OFFSETS[i * 3 + 1], z + OFFSETS[i * 3 + 2]);
            if (suitable(state)) return state;
        }
        // Nothing nearby, so take the most common block of the whole section.
        return sectionCover(sampler, x, y, z, fallback);
    }

    private static BlockState sectionCover(Sampler sampler, int x, int y, int z, BlockState fallback) {
        long key = ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4);
        BlockState cached = SECTION_COVER.get(key);
        if (cached != null) return cached;

        BlockState computed = scanSection(sampler, (x >> 4) << 4, (y >> 4) << 4, (z >> 4) << 4, fallback);
        SECTION_COVER.put(key, computed);
        return computed;
    }

    private static BlockState scanSection(Sampler sampler, int originX, int originY, int originZ, BlockState fallback) {
        Map<BlockState, int[]> counts = new HashMap<>();
        for (int dy = 0; dy < 16; dy++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    BlockState state = sampler.get(originX + dx, originY + dy, originZ + dz);
                    if (!suitable(state)) continue;
                    counts.computeIfAbsent(state, s -> new int[1])[0]++;
                }
            }
        }
        BlockState best = null;
        int bestCount = 0;
        for (Map.Entry<BlockState, int[]> e : counts.entrySet()) {
            int c = e.getValue()[0];
            // Ties are broken by block name: the result has to be repeatable between
            // chunk rebuilds, otherwise the textures would flicker.
            if (c > bestCount || (c == bestCount && best != null && idOf(e.getKey()).compareTo(idOf(best)) < 0)) {
                best = e.getKey();
                bestCount = c;
            }
        }
        return best != null ? best : fallback;
    }

    private static String idOf(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    /**
     * A block only qualifies as a replacement when it is a full, opaque cube with no
     * block entity - so it changes neither the lighting nor the silhouette of the
     * terrain, and there is no seeing through it.
     */
    public static boolean suitable(BlockState state) {
        return state != null
                && !state.isAir()
                && !state.isOf(Blocks.BEDROCK)
                && !state.hasBlockEntity()
                && state.getRenderType() == BlockRenderType.MODEL
                && state.isOpaqueFullCube()
                && state.getFluidState().isEmpty();
    }
}
