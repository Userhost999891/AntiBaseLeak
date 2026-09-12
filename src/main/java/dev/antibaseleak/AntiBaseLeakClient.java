package dev.antibaseleak;

import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.MaskMode;
import dev.antibaseleak.gui.AblConfigScreen;
import dev.antibaseleak.gui.JoinToast;
import dev.antibaseleak.mask.BedrockMask;
import dev.antibaseleak.mask.WorldMasker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AntiBaseLeakClient implements ClientModInitializer {

    public static final String MENU_KEY = "key.antibaseleak.menu";

    private static KeyBinding menuKey;
    private static ClientWorld lastWorld;

    @Override
    public void onInitializeClient() {
        AblConfig.load();

        //? if >=1.21.9 {
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(MENU_KEY, GLFW.GLFW_KEY_F7, KeyBinding.Category.MISC));
        //?} else {
        /*menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(MENU_KEY, GLFW.GLFW_KEY_F7, "key.categories.misc"));
        *///?}

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // A world or dimension change invalidates the cache: the same section
            // coordinates mean something entirely different in the Nether.
            if (client.world != lastWorld) {
                lastWorld = client.world;
                WorldMasker.checkWorld(client.world);
            }
            while (menuKey.wasPressed()) {
                client.setScreen(new AblConfigScreen(client.currentScreen));
            }
        });

        ClientChunkEvents.CHUNK_LOAD.register(WorldMasker::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(WorldMasker::onChunkUnload);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            AblConfig cfg = AblConfig.get();
            if (cfg.showJoinToast) JoinToast.show();
            if (cfg.maskBedrock && BedrockMask.effectiveMode() == MaskMode.RENDER && BedrockMask.hasCustomRenderer()) {
                // Sodium bypasses ChunkRendererRegion, so in this configuration
                // bedrock would not be masked at all - worth saying out loud.
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(Text.translatable("antibaseleak.warn.sodium"), false);
                    }
                });
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            JoinToast.hide();
            WorldMasker.reset();
            BedrockMask.clearCache();
        });

        if (Boolean.getBoolean("antibaseleak.selftest")) SelfTest.run();

        AntiBaseLeak.LOG.info("AntiBaseLeak loaded (masking mode: {})", BedrockMask.effectiveMode());
    }

    /** The menu key - the notification shows its current binding. */
    public static KeyBinding menuKey() {
        return menuKey;
    }
}
