package ru.rws.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RwsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String serverIp = "ru.reallyworld.me";
    public String commonPassword = "";
    public String tpaTarget = "";
    public String payReceiver = "";
    public String limitTriggerRegex = "Работы.*Достигнут лимит";
    public String balanceRegex = "Баланс:\\s*([\\d\\s]+(?:[.,]\\d+)?)";
    public String loginPromptRegex = "Авторизация.*/login";
    public int mapSlotId = 4;
    public int worldChangeTimeoutMs = 30000;
    public int maxGunpowderClicks = 1000;

    public List<AccountEntry> accounts = new ArrayList<>();

    private static RwsConfig INSTANCE;

    public static RwsConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("rws.json");
    }

    public static RwsConfig load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            RwsConfig def = new RwsConfig();
            def.save();
            INSTANCE = def;
            return def;
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            RwsConfig cfg = GSON.fromJson(json, RwsConfig.class);
            if (cfg == null) {
                cfg = new RwsConfig();
            }
            if (cfg.accounts == null) {
                cfg.accounts = new ArrayList<>();
            }
            INSTANCE = cfg;
            return cfg;
        } catch (IOException e) {
            RwsConfig def = new RwsConfig();
            INSTANCE = def;
            return def;
        }
    }

    public void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }
}
