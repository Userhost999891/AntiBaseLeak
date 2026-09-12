package dev.antibaseleak.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.OptionalInt;

/**
 * Blurring parts of the finished frame - the 1.21.9 - 1.21.11 version, on the new
 * blaze3d.
 *
 * <p>The flow: copy the whole screen into our own framebuffer, run the vanilla
 * "minecraft:blur" post effect over it (the very shader that blurs the pause menu
 * background), then put the blurred texture back on screen with a full screen blit
 * scissored down to the sensitive rectangles. No custom GLSL is needed.
 *
 * <p>The rectangles are deliberately NOT copied with {@code copyTextureToTexture}: that
 * method passes width and height to {@code glBlitFramebuffer} where OpenGL expects the
 * coordinates of the opposite corner, so it only behaves correctly when copying a whole
 * surface starting at (0,0) - which is exactly how vanilla uses it.
 */
public final class ScreenBlur {

    private static final Identifier BLUR = Identifier.ofVanilla("blur");

    /**
     * The post effect asks for intermediate framebuffers of its own.
     * ObjectAllocator.TRIVIAL would create and destroy them every frame, so we keep a
     * pool - exactly what GameRenderer does for the pause menu blur.
     */
    private static final ObjectPool POOL = new ObjectPool(3);

    private static SimpleFramebuffer scratch;

    private ScreenBlur() {
    }

    public static boolean isAvailable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !RenderSystem.isOnRenderThread()) return false;
        try {
            return client.getShaderLoader().loadPostEffect(BLUR, DefaultFramebufferSet.MAIN_ONLY) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean apply(List<int[]> rects, int strength) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        PostEffectProcessor blur = client.getShaderLoader().loadPostEffect(BLUR, DefaultFramebufferSet.MAIN_ONLY);
        if (blur == null) return false;

        int width = main.textureWidth;
        int height = main.textureHeight;
        if (width <= 0 || height <= 0) return false;
        SimpleFramebuffer target = scratch(width, height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // 1. the whole screen into our own framebuffer (a copy from (0,0), the safe case)
        encoder.copyTextureToTexture(main.getColorAttachment(), target.getColorAttachment(),
                0, 0, 0, 0, 0, width, height);

        // 2. blur it (each extra pass makes it blurrier)
        POOL.decrementLifespan();
        for (int i = 0; i < strength; i++) {
            blur.render(target, POOL);
        }

        // 3. the blurred texture goes back on screen, but only inside the rectangles
        GpuTextureView source = target.getColorAttachmentView();
        double scale = client.getWindow().getScaleFactor();
        try (RenderPass pass = encoder.createRenderPass(() -> "antibaseleak_blur",
                main.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));

            for (int[] rect : rects) {
                int x1 = clamp((int) (rect[0] * scale), 0, width);
                int x2 = clamp((int) Math.ceil(rect[2] * scale), 0, width);
                // the scissor origin is the BOTTOM left corner, the GUI origin is top left
                int y1 = clamp(height - (int) Math.ceil(rect[3] * scale), 0, height);
                int y2 = clamp(height - (int) (rect[1] * scale), 0, height);
                if (x2 <= x1 || y2 <= y1) continue;
                pass.enableScissor(x1, y1, x2 - x1, y2 - y1);
                pass.draw(0, 3);
            }
            pass.disableScissor();
        }
        return true;
    }

    public static void reset() {
        if (scratch != null) {
            scratch.delete();
            scratch = null;
        }
    }

    private static SimpleFramebuffer scratch(int width, int height) {
        if (scratch == null) {
            scratch = new SimpleFramebuffer("antibaseleak_blur", width, height, false);
        } else if (scratch.textureWidth != width || scratch.textureHeight != height) {
            scratch.resize(width, height);
        }
        return scratch;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
