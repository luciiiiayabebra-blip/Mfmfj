package ru.rws.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.rws.events.ChatListener;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void rws$onAddMessage(Text message, CallbackInfo ci) {
        try {
            String plain = message.getString();
            ChatListener.dispatch(plain);
        } catch (Throwable ignored) {
        }
    }
}
