package dev.antibaseleak.mixin;

import dev.antibaseleak.mask.WorldMasker;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Closes the last gap in WORLD mode: a chunk is masked once, on load, but the server
 * can still send a single block update later. Without this hook a failed attempt at
 * mining "netherrack" - really bedrock - would make the server send a correction and
 * leave one exposed bedrock block behind.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    // When capturing arguments, Mixin needs the target method full parameter list
    // (BlockPos, BlockState, int) appended after the modified value.
    @ModifyVariable(method = "handleBlockUpdate", at = @At("HEAD"), argsOnly = true)
    private BlockState antibaseleak$maskIncomingBedrock(BlockState value, BlockPos pos, BlockState state, int flags) {
        return WorldMasker.maskIncoming(pos, value);
    }
}
