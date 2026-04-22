package ru.rws.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import ru.rws.config.AccountEntry;
import ru.rws.config.ProxyEntry;
import ru.rws.config.RwsConfig;
import ru.rws.core.AutomationEngine;
import ru.rws.core.LogBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RwsConfigScreen {

    private RwsConfigScreen() {
    }

    public static Screen build(Screen parent) {
        RwsConfig cfg = RwsConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TranslatableText("rws.config.title"))
                .setSavingRunnable(cfg::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(new TranslatableText("rws.config.category.general"));
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.serverIp"), cfg.serverIp)
                .setDefaultValue("ru.reallyworld.me")
                .setSaveConsumer(v -> cfg.serverIp = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.commonPassword"), cfg.commonPassword)
                .setDefaultValue("")
                .setSaveConsumer(v -> cfg.commonPassword = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.tpaTarget"), cfg.tpaTarget)
                .setDefaultValue("")
                .setSaveConsumer(v -> cfg.tpaTarget = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.payReceiver"), cfg.payReceiver)
                .setDefaultValue("")
                .setSaveConsumer(v -> cfg.payReceiver = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.loginRegex"), cfg.loginPromptRegex)
                .setDefaultValue("Авторизация.*/login")
                .setSaveConsumer(v -> cfg.loginPromptRegex = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.limitRegex"), cfg.limitTriggerRegex)
                .setDefaultValue("Работы.*Достигнут лимит")
                .setSaveConsumer(v -> cfg.limitTriggerRegex = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.general.balanceRegex"), cfg.balanceRegex)
                .setDefaultValue("Баланс:\\s*([\\d\\s]+(?:[.,]\\d+)?)")
                .setSaveConsumer(v -> cfg.balanceRegex = v).build());
        general.addEntry(eb.startIntSlider(new TranslatableText("rws.config.general.worldTimeout"), cfg.worldChangeTimeoutMs, 5000, 120000)
                .setDefaultValue(30000)
                .setSaveConsumer(v -> cfg.worldChangeTimeoutMs = v).build());
        general.addEntry(eb.startIntSlider(new TranslatableText("rws.config.general.balanceTimeout"), cfg.balanceTimeoutMs, 1000, 30000)
                .setDefaultValue(5000)
                .setSaveConsumer(v -> cfg.balanceTimeoutMs = v).build());
        general.addEntry(eb.startIntSlider(new TranslatableText("rws.config.general.balanceRetries"), cfg.balanceRetries, 1, 10)
                .setDefaultValue(3)
                .setSaveConsumer(v -> cfg.balanceRetries = v).build());
        general.addEntry(eb.startIntSlider(new TranslatableText("rws.config.general.maxClicks"), cfg.maxGunpowderClicks, 1, 5000)
                .setDefaultValue(1000)
                .setSaveConsumer(v -> cfg.maxGunpowderClicks = v).build());

        if (cfg.commonProxy == null) cfg.commonProxy = new ProxyEntry();
        final ProxyEntry cp = cfg.commonProxy;
        general.addEntry(eb.startBooleanToggle(new TranslatableText("rws.config.commonProxy.enabled"), cp.enabled)
                .setTooltip(new LiteralText("Если у аккаунта прокси выключен — используется этот"))
                .setDefaultValue(false)
                .setSaveConsumer(v -> cp.enabled = v).build());
        general.addEntry(eb.startEnumSelector(new TranslatableText("rws.config.commonProxy.type"), ProxyEntry.Type.class, cp.type)
                .setDefaultValue(ProxyEntry.Type.SOCKS5)
                .setSaveConsumer(v -> cp.type = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.commonProxy.host"), cp.host)
                .setDefaultValue("")
                .setSaveConsumer(v -> cp.host = v).build());
        general.addEntry(eb.startIntField(new TranslatableText("rws.config.commonProxy.port"), cp.port)
                .setDefaultValue(1080)
                .setSaveConsumer(v -> cp.port = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.commonProxy.user"), cp.username)
                .setDefaultValue("")
                .setSaveConsumer(v -> cp.username = v).build());
        general.addEntry(eb.startStrField(new TranslatableText("rws.config.commonProxy.pass"), cp.password)
                .setDefaultValue("")
                .setSaveConsumer(v -> cp.password = v).build());

        ConfigCategory accounts = builder.getOrCreateCategory(new TranslatableText("rws.config.category.accounts"));
        accounts.addEntry(eb.startStrList(new TranslatableText("rws.config.accounts.list"), accountsToLines(cfg))
                .setTooltip(new LiteralText("Одна строка = один аккаунт.\nФормат: nick|SOCKS5|host|port|user|pass|enabled\nПароль общий (см. вкладку \"Общие\")"))
                .setSaveConsumer(lines -> applyAccounts(cfg, lines))
                .setExpanded(true)
                .build());

        ConfigCategory status = builder.getOrCreateCategory(new TranslatableText("rws.config.category.status"));
        AutomationEngine engine = AutomationEngine.get();
        status.addEntry(eb.startTextDescription(new LiteralText("§e" + new TranslatableText("rws.status.state").getString() + ": §f" + engine.getState())).build());
        status.addEntry(eb.startTextDescription(new LiteralText("§e" + new TranslatableText("rws.status.account").getString() + ": §f"
                + (engine.getCurrentAccountIndex() < 0 ? "-" : (engine.getCurrentAccountIndex() + 1) + " / " + engine.getCurrentAccountName()))).build());
        status.addEntry(eb.startTextDescription(new LiteralText("§e" + new TranslatableText("rws.status.step").getString() + ": §f"
                + engine.getCurrentStep() + " - " + engine.getCurrentStepDesc())).build());
        status.addEntry(eb.startTextDescription(new LiteralText("§7--- " + new TranslatableText("rws.status.log").getString() + " ---")).build());
        for (String line : LogBuffer.get().reversed()) {
            status.addEntry(eb.startTextDescription(new LiteralText(line)).build());
        }
        status.addEntry(eb.startStrField(new TranslatableText("rws.status.clear"), "")
                .setTooltip(new LiteralText("Введите 'clear' и сохраните"))
                .setSaveConsumer(v -> {
                    if ("clear".equalsIgnoreCase(v)) {
                        LogBuffer.get().clear();
                    }
                }).build());

        return builder.build();
    }

    private static List<String> accountsToLines(RwsConfig cfg) {
        return cfg.accounts.stream().map(a -> {
            ProxyEntry p = a.proxy != null ? a.proxy : new ProxyEntry();
            return String.join("|",
                    safe(a.nickname),
                    p.type == null ? "SOCKS5" : p.type.name(),
                    safe(p.host),
                    String.valueOf(p.port),
                    safe(p.username),
                    safe(p.password),
                    String.valueOf(p.enabled));
        }).collect(Collectors.toList());
    }

    private static void applyAccounts(RwsConfig cfg, List<String> lines) {
        List<AccountEntry> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            AccountEntry a = new AccountEntry();
            a.proxy = new ProxyEntry();
            if (parts.length >= 1) a.nickname = parts[0].trim();
            if (parts.length >= 2) {
                try { a.proxy.type = ProxyEntry.Type.valueOf(parts[1].trim().toUpperCase()); } catch (Throwable ignored) { a.proxy.type = ProxyEntry.Type.SOCKS5; }
            }
            if (parts.length >= 3) a.proxy.host = parts[2].trim();
            if (parts.length >= 4) {
                try { a.proxy.port = Integer.parseInt(parts[3].trim()); } catch (Throwable ignored) {}
            }
            if (parts.length >= 5) a.proxy.username = parts[4];
            if (parts.length >= 6) a.proxy.password = parts[5];
            if (parts.length >= 7) a.proxy.enabled = Boolean.parseBoolean(parts[6].trim());
            result.add(a);
        }
        cfg.accounts = result;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("|", "/");
    }
}
