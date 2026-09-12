package dev.antibaseleak.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.Pool;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * Blurring parts of the finished frame - the 1.21.2 - 1.21.4 version, on plain OpenGL.
 *
 * <p>The flow: copy the whole screen into our own framebuffer, run the vanilla
 * "minecraft:blur" post effect over it (the very shader that blurs the pause menu
 * background), then copy the blurred rectangles back onto the screen. No custom GLSL
 * is needed and the blur is a real one, computed on the GPU.
 */
public final class ScreenBlur {

    private static final Identifier BLUR = Identifier.ofVanilla("blur");
    private static final float RADIUS = 8.0F;

    /**
     * The post effect asks for intermediate framebuffers of its own.
     * ObjectAllocator.TRIVIAL would create and destroy them every frame, so we keep a
     * pool - exactly what GameRenderer does for the pause menu blur.
     */
    private static final Pool POOL = new Pool(3);

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

        // 1. the whole screen into our own framebuffer
        blit(main.fbo, target.fbo, 0, 0, width, height, 0, 0, width, height);

        // 2. blur it (each extra pass makes it blurrier)
        POOL.decrementLifespan();
        for (int i = 0; i < strength; i++) {
            blur.setUniforms("Radius", RADIUS);
            blur.render(target, POOL);
        }

        // 3. only the sensitive rectangles go back onto the screen
        double scale = client.getWindow().getScaleFactor();
        for (int[] rect : rects) {
            int x1 = clamp((int) (rect[0] * scale), 0, width);
            int x2 = clamp((int) Math.ceil(rect[2] * scale), 0, width);
            // the framebuffer origin is bottom left, the GUI origin is top left
            int y1 = clamp(height - (int) Math.ceil(rect[3] * scale), 0, height);
            int y2 = clamp(height - (int) (rect[1] * scale), 0, height);
            if (x2 <= x1 || y2 <= y1) continue;
            blit(target.fbo, main.fbo, x1, y1, x2, y2, x1, y1, x2, y2);
        }

        main.beginWrite(true);
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
            scratch = new SimpleFramebuffer(width, height, false);
        } else if (scratch.textureWidth != width || scratch.textureHeight != height) {
            scratch.resize(width, height);
        }
        return scratch;
    }

    private static void blit(int srcFbo, int dstFbo,
                             int sx1, int sy1, int sx2, int sy2,
                             int dx1, int dy1, int dx2, int dy2) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
