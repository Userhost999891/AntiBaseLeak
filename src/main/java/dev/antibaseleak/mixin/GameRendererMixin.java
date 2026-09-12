package dev.antibaseleak.mixin;

import dev.antibaseleak.render.BlurCensor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The moment to blur: the end of frame rendering, when the world and the whole GUI
 * are already in the framebuffer but it has not been presented yet.
 *
 * <p>Earlier is impossible - since 1.21.9 GUI drawing is deferred, so when the F3 text
 * is "drawn" it is not in the framebuffer yet. Later makes no sense either, because
 * screenshots and screen capture take exactly this framebuffer.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void antibaseleak$applyBlur(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        BlurCensor.applyQueued();
    }
}
