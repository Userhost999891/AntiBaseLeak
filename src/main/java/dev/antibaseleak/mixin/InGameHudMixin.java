package dev.antibaseleak.mixin;

import dev.antibaseleak.gui.JoinToast;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the mod notification on top of everything else in the HUD. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void antibaseleak$renderToast(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        JoinToast.render(context);
    }
}
