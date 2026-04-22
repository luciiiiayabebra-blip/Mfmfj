package ru.rws;

import net.fabricmc.api.ClientModInitializer;
import ru.rws.config.RwsConfig;
import ru.rws.core.LogBuffer;
import ru.rws.keybind.RwsKeyBindings;

public class RwsMod implements ClientModInitializer {

    public static final String MOD_ID = "rws";

    @Override
    public void onInitializeClient() {
        RwsConfig.load();
        RwsKeyBindings.register();
        LogBuffer.get().info("RWS инициализирован");
    }
}
