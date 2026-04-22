package ru.rws.events;

import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldChangeListener {

    public interface Listener {
        void onWorldChange();
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private WorldChangeListener() {
    }

    public static void register(Listener l) {
        LISTENERS.add(l);
    }

    public static void unregister(Listener l) {
        LISTENERS.remove(l);
    }

    public static void fire() {
        for (Listener l : LISTENERS) {
            try {
                l.onWorldChange();
            } catch (Throwable ignored) {
            }
        }
    }
}
