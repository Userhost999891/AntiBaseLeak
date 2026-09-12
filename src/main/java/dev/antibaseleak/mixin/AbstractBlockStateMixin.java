package dev.antibaseleak.mixin;

import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.config.AblConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the model randomness that depends on block position.
 *
 * <p>Minecraft picks the model variant and rotation from a seed derived from XYZ, and
 * plants are additionally offset by a value from the same function. A pattern of stone
 * or netherrack rotations on a screenshot is therefore every bit as revealing as the
 * bedrock pattern - it only has to be matched against the generated world. A constant
 * seed makes every block pick the same variant, so there is no pattern left.
 *
 * <p>The hook sits on the shared block state method, so it also covers third party
 * renderers - Sodium and its relatives call it as well.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

    @Inject(method = "getRenderingSeed", at = @At("HEAD"), cancellable = true)
    private void antibaseleak$constantSeed(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (!AblConfig.get().disableModelRotation) return;
        FeatureStatus.modelSeedHooked = true;
        cir.setReturnValue(0L);
    }

    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
    private void antibaseleak$noModelOffset(BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        if (!AblConfig.get().disableModelOffset) return;
        FeatureStatus.modelOffsetHooked = true;
        cir.setReturnValue(Vec3d.ZERO);
    }
}
