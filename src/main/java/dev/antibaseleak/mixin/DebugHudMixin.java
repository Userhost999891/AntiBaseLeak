package dev.antibaseleak.mixin;

import dev.antibaseleak.censor.DebugCensor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * F3 censoring.
 *
 * <p>The hook sits on the common exit: the method that draws the finished list of
 * lines. It is the only place EVERY line passes through - the left and the right
 * column, vanilla entries and those added by other mods.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    // Besides the list itself we take the context and which column this is; without
    // them there is no way to work out where a line lands on screen, which the blur
    // censor style needs.
    @ModifyVariable(method = "drawText", at = @At("HEAD"), argsOnly = true)
    private List<String> antibaseleak$censorDebugLines(List<String> value, DrawContext context,
                                                       List<String> lines, boolean leftColumn) {
        return DebugCensor.censor(value, context, leftColumn);
    }
}
