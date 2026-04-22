package ru.rws.events;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ChatListener {

    private static final CopyOnWriteArrayList<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    private ChatListener() {
    }

    public static void register(Consumer<String> listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    public static void dispatch(String plainMessage) {
        for (Consumer<String> l : LISTENERS) {
            try {
                l.accept(plainMessage);
            } catch (Throwable ignored) {
            }
        }
    }
}
