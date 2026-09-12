package dev.antibaseleak.mixin;

import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.MaskMode;
import dev.antibaseleak.mask.BedrockMask;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RENDER mode: bedrock is replaced in the world snapshot the chunk mesh is built from.
 * No block in the client world changes, so toggling the option takes effect as soon as
 * the chunks are rebuilt.
 *
 * <p>The replacement sits on the way out of getBlockState, so it also covers the
 * neighbours used for culling and ambient occlusion - there is no path left for the
 * real bedrock layout to show through.
 */
@Mixin(ChunkRendererRegion.class)
public abstract class ChunkRendererRegionMixin {

    /**
     * Recursion guard: while looking for a replacement we ask the same region about
     * neighbouring blocks and have to get the original data back. An instance field is
     * enough, because a single build thread owns each region.
     */
    @Unique
    private boolean antibaseleak$reentrant;

    /** Created once per region: inside a bedrock layer this method runs thousands of times. */
    @Unique
    private BedrockMask.Sampler antibaseleak$sampler;

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void antibaseleak$maskBedrock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (!BedrockMask.isBedrock(state) || this.antibaseleak$reentrant) return;

        AblConfig cfg = AblConfig.get();
        if (!cfg.maskBedrock || BedrockMask.effectiveMode() != MaskMode.RENDER) return;

        FeatureStatus.renderMaskHooked = true;
        if (this.antibaseleak$sampler == null) {
            ChunkRendererRegion self = (ChunkRendererRegion) (Object) this;
            BlockPos.Mutable cursor = new BlockPos.Mutable();
            this.antibaseleak$sampler = (x, y, z) -> self.getBlockState(cursor.set(x, y, z));
        }

        this.antibaseleak$reentrant = true;
        try {
            BlockState replacement = BedrockMask.replacement(
                    this.antibaseleak$sampler,
                    pos.getX(), pos.getY(), pos.getZ(),
                    BedrockMask.fallbackFor(MinecraftClient.getInstance().world, pos.getY()));
            cir.setReturnValue(replacement);
        } finally {
            this.antibaseleak$reentrant = false;
        }
    }
}
