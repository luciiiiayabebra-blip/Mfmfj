package ru.rws.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import ru.rws.config.AccountEntry;
import ru.rws.config.RwsConfig;
import ru.rws.events.ChatListener;
import ru.rws.events.WorldChangeListener;
import ru.rws.net.ProxyConnector;
import ru.rws.net.SessionSwitcher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutomationEngine {

    private static final AutomationEngine INSTANCE = new AutomationEngine();

    private volatile AutomationState state = AutomationState.IDLE;
    private volatile int currentAccountIndex = -1;
    private volatile String currentAccountName = "";
    private volatile int currentStep = 0;
    private volatile String currentStepDesc = "";

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;

    private Thread worker;

    private AutomationEngine() {
    }

    public static AutomationEngine get() {
        return INSTANCE;
    }

    public AutomationState getState() { return state; }
    public int getCurrentAccountIndex() { return currentAccountIndex; }
    public String getCurrentAccountName() { return currentAccountName; }
    public int getCurrentStep() { return currentStep; }
    public String getCurrentStepDesc() { return currentStepDesc; }

    public synchronized void toggle() {
        if (state == AutomationState.IDLE || state == AutomationState.ERROR) {
            start();
        } else if (state == AutomationState.RUNNING) {
            pause();
        } else if (state == AutomationState.PAUSED) {
            resume();
        }
    }

    public synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        paused = false;
        state = AutomationState.RUNNING;
        LogBuffer.get().info("Запуск автоматизации");
        worker = new Thread(this::run, "RWS-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void pause() {
        paused = true;
        state = AutomationState.PAUSED;
        LogBuffer.get().info("Пауза");
    }

    public synchronized void resume() {
        paused = false;
        state = AutomationState.RUNNING;
        LogBuffer.get().info("Возобновление");
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    private void checkPause() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) {
                pauseLock.wait();
            }
        }
    }

    private void run() {
        try {
            RwsConfig cfg = RwsConfig.get();
            for (int i = 0; i < cfg.accounts.size(); i++) {
                currentAccountIndex = i;
                AccountEntry acc = cfg.accounts.get(i);
                currentAccountName = acc.nickname;
                LogBuffer.get().info("=== Аккаунт [" + (i + 1) + "/" + cfg.accounts.size() + "]: " + acc.nickname + " ===");
                try {
                    processAccount(acc, cfg);
                } catch (InterruptedException ie) {
                    throw ie;
                } catch (Throwable t) {
                    LogBuffer.get().error("Ошибка на аккаунте " + acc.nickname + ": " + t.getMessage());
                }
            }
            state = AutomationState.IDLE;
            LogBuffer.get().info("Все аккаунты обработаны. Стоп.");
        } catch (InterruptedException e) {
            LogBuffer.get().warn("Поток прерван");
            state = AutomationState.IDLE;
        } catch (Throwable t) {
            LogBuffer.get().error("Фатальная ошибка: " + t.getMessage());
            state = AutomationState.ERROR;
        } finally {
            currentAccountIndex = -1;
            currentAccountName = "";
            currentStep = 0;
            currentStepDesc = "";
        }
    }

    private void setStep(int step, String desc) {
        currentStep = step;
        currentStepDesc = desc;
        LogBuffer.get().info("Шаг " + step + ": " + desc);
    }

    private void processAccount(AccountEntry acc, RwsConfig cfg) throws Exception {
        setStep(1, "Дисконнект");
        checkPause();
        disconnectIfOnline();
        sleep(1000);

        setStep(2, "Смена ника -> " + acc.nickname);
        checkPause();
        SessionSwitcher.changeNickname(acc.nickname);

        setStep(3, "Подключение к " + cfg.serverIp);
        checkPause();
        ProxyConnector.setProxy(acc.proxy);
        connectToServer(cfg.serverIp);

        setStep(5, "Ожидание /login или компаса в хотбаре");
        checkPause();
        LoginResult lr = awaitLoginOrCompass(cfg.loginPromptRegex, cfg.worldChangeTimeoutMs);
        if (lr == LoginResult.TIMEOUT) {
            LogBuffer.get().warn("Нет ни запроса /login, ни компаса -> пропуск");
            return;
        }
        if (lr == LoginResult.LOGIN_PROMPT) {
            sleep(500);
            sendChat("/login " + cfg.commonPassword);
            LogBuffer.get().info("Ожидание компаса после /login");
            if (!awaitCompassInHotbar(cfg.worldChangeTimeoutMs)) {
                LogBuffer.get().warn("Компас не появился после /login -> пропуск");
                return;
            }
        } else {
            LogBuffer.get().info("Компас уже в хотбаре — /login пропущен");
        }

        setStep(7, "Взять компас и ПКМ");
        checkPause();
        if (!useCompass()) {
            LogBuffer.get().warn("Не удалось использовать компас -> пропуск");
            return;
        }

        setStep(8, "Клик по печке в GUI");
        checkPause();
        if (!awaitHandledScreen(5000)) {
            LogBuffer.get().warn("GUI после компаса не открылся");
            return;
        }
        if (!clickFirstItem(Items.FURNACE)) {
            LogBuffer.get().warn("Печка не найдена");
            return;
        }

        setStep(9, "Пауза 1 сек");
        sleep(1000);

        setStep(10, "Клик по слоту #" + cfg.mapSlotId + " до смены мира");
        checkPause();
        if (!awaitHandledScreen(5000)) {
            LogBuffer.get().warn("GUI с картой миров не открылся");
            return;
        }
        if (!clickSlotUntilWorldChange(cfg.mapSlotId, cfg.worldChangeTimeoutMs)) {
            LogBuffer.get().warn("Смена мира после кликов не произошла -> пропуск");
            return;
        }

        setStep(12, "/tpa " + cfg.tpaTarget);
        checkPause();
        sleep(1500);
        if (cfg.tpaTarget != null && !cfg.tpaTarget.isEmpty()) {
            sendChat("/tpa " + cfg.tpaTarget);
        }

        setStep(13, "Пауза 5 сек");
        sleep(5000);

        setStep(14, "/sellfish");
        checkPause();
        sendChat("/sellfish");

        setStep(15, "Пауза 1 сек");
        sleep(1000);

        setStep(16, "Клики по пороху до лимита");
        checkPause();
        clickGunpowderUntilLimit(cfg);

        setStep(17, "Пауза 1 сек");
        sleep(1000);

        setStep(18, "Закрыть GUI (E)");
        checkPause();
        closeHandledScreen();
        sleep(500);

        setStep(19, "/balance");
        checkPause();
        long amount = queryBalance(cfg);

        setStep(20, "Пауза 1 сек (обработка баланса)");
        sleep(1000);

        if (amount <= 0) {
            LogBuffer.get().warn("Баланс не получен или 0 -> пропуск /pay");
        } else {
            setStep(21, "/pay " + cfg.payReceiver + " " + amount);
            checkPause();
            if (cfg.payReceiver != null && !cfg.payReceiver.isEmpty()) {
                sendChat("/pay " + cfg.payReceiver + " " + amount);
                sleep(1500);
            }
        }

        setStep(22, "Дисконнект и переход к следующему");
        checkPause();
        disconnectIfOnline();
        sleep(1500);
    }

    private void sleep(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            checkPause();
            Thread.sleep(Math.min(100, end - System.currentTimeMillis()));
        }
    }

    private void runOnClient(Runnable r) {
        MinecraftClient.getInstance().execute(r);
    }

    private <T> T callOnClient(java.util.concurrent.Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        runOnClient(() -> {
            try {
                f.complete(c.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            throw new RuntimeException("client task timeout");
        }
    }

    private void disconnectIfOnline() {
        runOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                mc.world.disconnect();
                mc.disconnect();
                mc.openScreen(new TitleScreen());
            }
        });
    }

    private void connectToServer(String ip) {
        runOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerInfo info = new ServerInfo("RWS", ip, false);
            try {
                mc.openScreen(new ConnectScreen(new TitleScreen(), mc, info));
            } catch (Throwable t) {
                LogBuffer.get().error("Ошибка подключения: " + t.getMessage());
            }
        });
    }

    private boolean awaitWorldChange(int timeoutMs) throws InterruptedException {
        final CompletableFuture<Void> fut = new CompletableFuture<>();
        WorldChangeListener.Listener listener = () -> fut.complete(null);
        WorldChangeListener.register(listener);
        try {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                checkPause();
                if (fut.isDone()) {
                    return true;
                }
                try {
                    fut.get(200, TimeUnit.MILLISECONDS);
                    return true;
                } catch (TimeoutException ignored) {
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        } finally {
            WorldChangeListener.unregister(listener);
        }
    }

    private enum LoginResult { LOGIN_PROMPT, COMPASS_ALREADY, TIMEOUT }

    private LoginResult awaitLoginOrCompass(String regex, int timeoutMs) throws InterruptedException {
        if (hasCompassInHotbar()) return LoginResult.COMPASS_ALREADY;

        Pattern tmp;
        try {
            tmp = Pattern.compile(regex);
        } catch (Throwable t) {
            LogBuffer.get().error("Неверный regex /login: " + regex);
            tmp = null;
        }
        final Pattern pattern = tmp;
        final CompletableFuture<Void> loginFut = new CompletableFuture<>();
        Consumer<String> listener = msg -> {
            if (pattern != null && pattern.matcher(msg).find()) {
                loginFut.complete(null);
            }
        };
        ChatListener.register(listener);
        try {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                checkPause();
                if (hasCompassInHotbar()) return LoginResult.COMPASS_ALREADY;
                if (loginFut.isDone()) return LoginResult.LOGIN_PROMPT;
                Thread.sleep(100);
            }
            return LoginResult.TIMEOUT;
        } finally {
            ChatListener.unregister(listener);
        }
    }

    private boolean hasCompassInHotbar() {
        try {
            Boolean has = callOnClient(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                ClientPlayerEntity pl = mc.player;
                if (pl == null) return false;
                for (int i = 0; i < 9; i++) {
                    if (pl.inventory.getStack(i).getItem() == Items.COMPASS) {
                        return true;
                    }
                }
                return false;
            });
            return Boolean.TRUE.equals(has);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean awaitChatMatch(String regex, int timeoutMs) throws InterruptedException {
        final Pattern p;
        try {
            p = Pattern.compile(regex);
        } catch (Throwable t) {
            LogBuffer.get().error("Неверный regex: " + regex);
            return false;
        }
        final CompletableFuture<Void> fut = new CompletableFuture<>();
        Consumer<String> listener = msg -> {
            if (p.matcher(msg).find()) {
                fut.complete(null);
            }
        };
        ChatListener.register(listener);
        try {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                checkPause();
                if (fut.isDone()) return true;
                try {
                    fut.get(200, TimeUnit.MILLISECONDS);
                    return true;
                } catch (TimeoutException ignored) {
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        } finally {
            ChatListener.unregister(listener);
        }
    }

    private boolean awaitCompassInHotbar(int timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            checkPause();
            if (hasCompassInHotbar()) return true;
            Thread.sleep(100);
        }
        return false;
    }

    private boolean clickSlotUntilWorldChange(int slotId, int timeoutMs) throws Exception {
        final CompletableFuture<Void> fut = new CompletableFuture<>();
        WorldChangeListener.Listener listener = () -> fut.complete(null);
        WorldChangeListener.register(listener);
        try {
            long end = System.currentTimeMillis() + timeoutMs;
            int attempts = 0;
            int noGuiStreak = 0;
            final int maxAttempts = 40;
            final int maxNoGuiStreak = 15;
            while (System.currentTimeMillis() < end && !fut.isDone()
                    && attempts < maxAttempts && noGuiStreak < maxNoGuiStreak) {
                checkPause();
                Boolean ok = callOnClient(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    ClientPlayerEntity p = mc.player;
                    if (p == null || mc.interactionManager == null) return false;
                    Screen s = mc.currentScreen;
                    if (!(s instanceof HandledScreen)) return false;
                    HandledScreen<?> hs = (HandledScreen<?>) s;
                    ScreenHandler handler = hs.getScreenHandler();
                    if (slotId < 0 || slotId >= handler.slots.size()) return false;
                    mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, p);
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    attempts++;
                    noGuiStreak = 0;
                } else {
                    noGuiStreak++;
                }
                Thread.sleep(400);
            }
            return fut.isDone();
        } finally {
            WorldChangeListener.unregister(listener);
        }
    }

    private boolean awaitHandledScreen(int timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            checkPause();
            Screen s = MinecraftClient.getInstance().currentScreen;
            if (s instanceof HandledScreen) {
                Thread.sleep(250);
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private boolean useCompass() throws Exception {
        return callOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.interactionManager == null) {
                return false;
            }
            int hotbarIdx = -1;
            int invSlotId = -1;
            for (int i = 0; i < 9; i++) {
                if (p.inventory.getStack(i).getItem() == Items.COMPASS) {
                    hotbarIdx = i;
                    break;
                }
            }
            if (hotbarIdx < 0) {
                for (int i = 9; i < 36; i++) {
                    if (p.inventory.getStack(i).getItem() == Items.COMPASS) {
                        invSlotId = i;
                        break;
                    }
                }
                if (invSlotId < 0) {
                    return false;
                }
                int targetHotbar = p.inventory.selectedSlot;
                int invSlotInContainer = invSlotId < 9 ? 36 + invSlotId : invSlotId;
                mc.interactionManager.clickSlot(
                        p.playerScreenHandler.syncId,
                        invSlotInContainer,
                        targetHotbar,
                        SlotActionType.SWAP,
                        p);
                hotbarIdx = targetHotbar;
            }
            p.inventory.selectedSlot = hotbarIdx;
            mc.interactionManager.interactItem(p, mc.world, Hand.MAIN_HAND);
            return true;
        });
    }

    private boolean clickFirstItem(net.minecraft.item.Item item) throws Exception {
        return clickNthItem(item, 1);
    }

    private boolean clickNthItem(net.minecraft.item.Item item, int n) throws Exception {
        return callOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.interactionManager == null) {
                return false;
            }
            Screen s = mc.currentScreen;
            if (!(s instanceof HandledScreen)) {
                return false;
            }
            HandledScreen<?> hs = (HandledScreen<?>) s;
            ScreenHandler handler = hs.getScreenHandler();
            int found = 0;
            int containerSize = getContainerInventorySize(handler);
            for (int i = 0; i < containerSize; i++) {
                Slot slot = handler.slots.get(i);
                ItemStack st = slot.getStack();
                if (!st.isEmpty() && st.getItem() == item) {
                    found++;
                    if (found == n) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, p);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static int getContainerInventorySize(ScreenHandler handler) {
        return handler.slots.size() - 36;
    }

    private void clickGunpowderUntilLimit(RwsConfig cfg) throws Exception {
        Pattern limitPattern = Pattern.compile(cfg.limitTriggerRegex);
        final AtomicReference<Boolean> limitReached = new AtomicReference<>(false);
        Consumer<String> chatL = msg -> {
            if (limitPattern.matcher(msg).find()) {
                limitReached.set(true);
            }
        };
        ChatListener.register(chatL);
        try {
            int clicks = 0;
            int emptyAttempts = 0;
            final int maxEmptyAttempts = 50;
            while (clicks < cfg.maxGunpowderClicks && !limitReached.get() && emptyAttempts < maxEmptyAttempts) {
                checkPause();
                Boolean clicked = callOnClient(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    ClientPlayerEntity p = mc.player;
                    if (p == null || mc.interactionManager == null) {
                        return false;
                    }
                    Screen s = mc.currentScreen;
                    if (!(s instanceof HandledScreen)) {
                        return false;
                    }
                    HandledScreen<?> hs = (HandledScreen<?>) s;
                    ScreenHandler handler = hs.getScreenHandler();
                    int containerSize = getContainerInventorySize(handler);
                    for (int i = 0; i < containerSize; i++) {
                        Slot slot = handler.slots.get(i);
                        ItemStack st = slot.getStack();
                        if (!st.isEmpty() && st.getItem() == Items.GUNPOWDER) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, p);
                            return true;
                        }
                    }
                    return false;
                });
                if (Boolean.TRUE.equals(clicked)) {
                    clicks++;
                    emptyAttempts = 0;
                } else {
                    emptyAttempts++;
                }
                Thread.sleep(80);
            }
            String reason;
            if (limitReached.get()) reason = " (лимит сервера)";
            else if (emptyAttempts >= maxEmptyAttempts) reason = " (порох не найден в GUI)";
            else reason = " (исчерпан maxGunpowderClicks)";
            LogBuffer.get().info("Порох кликов: " + clicks + reason);
        } finally {
            ChatListener.unregister(chatL);
        }
    }

    private void closeHandledScreen() {
        runOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.closeHandledScreen();
            }
        });
    }

    private long queryBalance(RwsConfig cfg) throws Exception {
        Pattern balancePattern = Pattern.compile(cfg.balanceRegex);
        final AtomicReference<Long> result = new AtomicReference<>(-1L);
        final CompletableFuture<Long> fut = new CompletableFuture<>();
        Consumer<String> chatL = msg -> {
            Matcher m = balancePattern.matcher(msg);
            if (m.find()) {
                try {
                    String raw = m.group(1).replaceAll("\\s+", "").replace(',', '.');
                    double val = Double.parseDouble(raw);
                    long amount = (long) Math.floor(val);
                    result.set(amount);
                    fut.complete(amount);
                } catch (Throwable ignored) {
                }
            }
        };
        ChatListener.register(chatL);
        try {
            sendChat("/balance");
            try {
                fut.get(3000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                LogBuffer.get().warn("Баланс не пришёл в чат за 3сек");
                return -1L;
            } catch (Exception ignored) {
            }
            return result.get();
        } finally {
            ChatListener.unregister(chatL);
        }
    }

    public void sendChat(String msg) {
        runOnClient(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendChatMessage(msg);
                LogBuffer.get().info("> " + msg);
            }
        });
    }

}
