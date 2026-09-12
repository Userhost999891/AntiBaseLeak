package dev.antibaseleak.mixin;

import dev.antibaseleak.censor.ChatCensor;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Optional censoring of coordinates in chat. The hook sits on the method that both
 * player and system messages pass through.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private Text antibaseleak$censorChat(Text message) {
        return ChatCensor.censor(message);
    }
}
