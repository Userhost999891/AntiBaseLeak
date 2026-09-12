package dev.antibaseleak.mask;

import dev.antibaseleak.AntiBaseLeak;
import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.MaskMode;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WORLD mode: replaces bedrock directly in the client side copy of the world, right
 * after a chunk is loaded.
 *
 * <p>Works with every renderer, including Sodium, which has its own pipeline and never
 * goes through ChunkRendererRegion, and as a bonus bedrock also disappears from F3 and
 * from the "what are you looking at" line.
 *
 * <p>So that it can be undone without relogging, every touched section gets a 4096 bit
 * map recording which positions used to be bedrock. That is 512 bytes per section, and
 * only for sections that actually contain bedrock.
 */
public final class WorldMasker {

    /** chunk (long) -> (sectionY -> bit map of positions that used to be bedrock) */
    private static final Map<Long, Map<Integer, long[]>> ORIGINAL = new ConcurrentHashMap<>();

    /** The world the stored bit maps belong to. */
    private static ClientWorld trackedWorld;

    private WorldMasker() {
    }

    public static boolean active() {
        AblConfig cfg = AblConfig.get();
        return cfg.maskBedrock && BedrockMask.effectiveMode() == MaskMode.WORLD;
    }

    public static boolean hasMaskedData() {
        return !ORIGINAL.isEmpty();
    }

    public static int maskedSectionCount() {
        int sum = 0;
        for (Map<Integer, long[]> m : ORIGINAL.values()) sum += m.size();
        return sum;
    }

    // ─── world events ───────────────────────────────────────────────────────

    public static void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        // The world change is checked here rather than in the tick: after a dimension
        // change chunks can arrive earlier and would overwrite the restore data.
        checkWorld(world);
        if (!active()) return;
        maskChunk(world, chunk);
    }

    public static void onChunkUnload(ClientWorld world, WorldChunk chunk) {
        ORIGINAL.remove(chunk.getPos().toLong());
    }

    /** A world or dimension change invalidates the bit maps and the replacement cache. */
    public static void checkWorld(ClientWorld world) {
        if (trackedWorld != world) {
            trackedWorld = world;
            ORIGINAL.clear();
            BedrockMask.clearCache();
        }
    }

    /** Leaving the world: there is nothing to restore, the data is gone anyway. */
    public static void reset() {
        trackedWorld = null;
        ORIGINAL.clear();
    }

    /** Reaction to a settings change in the F7 menu. */
    public static void onConfigChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            ORIGINAL.clear();
            return;
        }
        if (active()) {
            // The replacement settings may have changed, so start from a clean state.
            restoreAll(false);
            BedrockMask.clearCache();
            maskLoadedChunks(client.world);
        } else if (hasMaskedData()) {
            restoreAll(false);
        }
        if (client.worldRenderer != null) client.worldRenderer.reload();
    }

    // ─── masking ────────────────────────────────────────────────────────────

    public static void maskLoadedChunks(ClientWorld world) {
        checkWorld(world);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int radius = client.options.getViewDistance().getValue() + 1;
        ChunkPos center = client.player.getChunkPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunk(center.x + dx, center.z + dz);
                if (chunk instanceof EmptyChunk) continue;
                maskChunk(world, chunk);
            }
        }
    }

    /**
     * A single block sent by the server after the chunk was already masked. Called from
     * the client thread, the same as chunk loading.
     */
    public static BlockState maskIncoming(BlockPos pos, BlockState state) {
        if (!BedrockMask.isBedrock(state) || !active()) return state;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return state;

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockState replacement = BedrockMask.replacement(
                (x, y, z) -> world.getBlockState(cursor.set(x, y, z)),
                pos.getX(), pos.getY(), pos.getZ(),
                BedrockMask.fallbackFor(world, pos.getY()));

        // Recorded the same way as with a chunk, so that it can be undone.
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        int packed = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
        ORIGINAL.computeIfAbsent(chunkKey, k -> new HashMap<>())
                .computeIfAbsent(pos.getY() >> 4, k -> new long[64])[packed >>> 6] |= 1L << (packed & 63);
        return replacement;
    }

    private static void maskChunk(ClientWorld world, WorldChunk chunk) {
        long chunkKey = chunk.getPos().toLong();
        if (ORIGINAL.containsKey(chunkKey)) return;

        ChunkSection[] sections = chunk.getSectionArray();
        Map<Integer, long[]> perChunk = null;

        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(BedrockMask::isBedrock)) continue;

            int sectionY = chunk.sectionIndexToCoord(index);
            long[] bits = maskSection(world, chunk.getPos(), section, sectionY);
            if (bits == null) continue;

            if (perChunk == null) perChunk = new HashMap<>();
            perChunk.put(sectionY, bits);
        }

        if (perChunk != null) ORIGINAL.put(chunkKey, perChunk);
    }

    private static long[] maskSection(ClientWorld world, ChunkPos pos, ChunkSection section, int sectionY) {
        int originX = pos.getStartX();
        int originY = sectionY << 4;
        int originZ = pos.getStartZ();

        BlockState fallback = BedrockMask.fallbackFor(world, originY);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BedrockMask.Sampler sampler = (x, y, z) -> world.getBlockState(cursor.set(x, y, z));

        long[] bits = new long[64];
        int[] targets = new int[4096];
        int count = 0;

        // 1. collect the bedrock positions; nothing is changed yet, so that the
        //    replacement is chosen from the original terrain
        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    if (!BedrockMask.isBedrock(section.getBlockState(lx, ly, lz))) continue;
                    int packed = (ly << 8) | (lz << 4) | lx;
                    bits[packed >>> 6] |= 1L << (packed & 63);
                    targets[count++] = packed;
                }
            }
        }
        if (count == 0) return null;

        // 2. work out the replacements
        BlockState[] replacements = new BlockState[count];
        for (int i = 0; i < count; i++) {
            int packed = targets[i];
            int lx = packed & 15, lz = (packed >>> 4) & 15, ly = (packed >>> 8) & 15;
            replacements[i] = BedrockMask.replacement(sampler, originX + lx, originY + ly, originZ + lz, fallback);
        }

        // 3. only now apply them
        for (int i = 0; i < count; i++) {
            int packed = targets[i];
            section.setBlockState(packed & 15, (packed >>> 8) & 15, (packed >>> 4) & 15, replacements[i]);
        }
        return bits;
    }

    // ─── restoring ──────────────────────────────────────────────────────────

    /** Puts bedrock back where it used to be. */
    public static void restoreAll(boolean reloadRenderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            ORIGINAL.clear();
            return;
        }
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        int restored = 0;

        for (Map.Entry<Long, Map<Integer, long[]>> chunkEntry : ORIGINAL.entrySet()) {
            ChunkPos pos = new ChunkPos(chunkEntry.getKey());
            WorldChunk chunk = world.getChunk(pos.x, pos.z);
            if (chunk instanceof EmptyChunk) continue;
            ChunkSection[] sections = chunk.getSectionArray();

            for (Map.Entry<Integer, long[]> sectionEntry : chunkEntry.getValue().entrySet()) {
                int index = chunk.sectionCoordToIndex(sectionEntry.getKey());
                if (index < 0 || index >= sections.length) continue;
                ChunkSection section = sections[index];
                if (section == null) continue;

                long[] bits = sectionEntry.getValue();
                for (int packed = 0; packed < 4096; packed++) {
                    if ((bits[packed >>> 6] & (1L << (packed & 63))) == 0) continue;
                    section.setBlockState(packed & 15, (packed >>> 8) & 15, (packed >>> 4) & 15, bedrock);
                    restored++;
                }
            }
        }
        ORIGINAL.clear();
        if (restored > 0) AntiBaseLeak.LOG.info("Restored {} bedrock blocks", restored);
        if (reloadRenderer && client.worldRenderer != null) client.worldRenderer.reload();
    }
}
