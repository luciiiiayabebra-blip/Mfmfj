package ru.rws.keybind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.rws.core.AutomationEngine;
import ru.rws.gui.RwsConfigScreen;

public final class RwsKeyBindings {

    public static KeyBinding TOGGLE;
    public static KeyBinding OPEN_CONFIG;

    private RwsKeyBindings() {
    }

    public static void register() {
        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rws.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.rws"
        ));
        OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rws.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.rws"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.wasPressed()) {
                AutomationEngine.get().toggle();
            }
            while (OPEN_CONFIG.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.openScreen(RwsConfigScreen.build(mc.currentScreen));
            }
        });
    }
}
