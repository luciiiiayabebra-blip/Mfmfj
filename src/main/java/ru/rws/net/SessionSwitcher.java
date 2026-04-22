package ru.rws.net;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;
import net.minecraft.entity.player.PlayerEntity;
import ru.rws.core.LogBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

public final class SessionSwitcher {

    private SessionSwitcher() {
    }

    public static boolean changeNickname(String nickname) {
        try {
            UUID offlineUuid = PlayerEntity.getOfflinePlayerUuid(nickname);
            Session newSession = new Session(nickname, offlineUuid.toString(), "", "legacy");

            Field sessionField = findSessionField();
            if (sessionField == null) {
                LogBuffer.get().error("Не удалось найти поле session в MinecraftClient");
                return false;
            }
            sessionField.setAccessible(true);
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(sessionField, sessionField.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {
            }
            sessionField.set(MinecraftClient.getInstance(), newSession);
            LogBuffer.get().info("Сессия переключена на ник: " + nickname);
            return true;
        } catch (Throwable t) {
            LogBuffer.get().error("Ошибка смены сессии: " + t.getMessage());
            return false;
        }
    }

    private static Field findSessionField() {
        for (Field f : MinecraftClient.class.getDeclaredFields()) {
            if (f.getType() == Session.class) {
                return f;
            }
        }
        return null;
    }
}
