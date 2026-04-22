package ru.rws.core;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;

public final class LogBuffer {
    public enum Level { INFO, WARN, ERROR }

    public static final int MAX_SIZE = 50;

    private static final LogBuffer INSTANCE = new LogBuffer();
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss");

    private final Deque<String> buffer = new ArrayDeque<>();

    private LogBuffer() {
    }

    public static LogBuffer get() {
        return INSTANCE;
    }

    public synchronized void log(Level level, String msg) {
        String line = "[" + FORMAT.format(new Date()) + "][" + level + "] " + msg;
        if (buffer.size() >= MAX_SIZE) {
            buffer.pollFirst();
        }
        buffer.addLast(line);
    }

    public void info(String msg)  { log(Level.INFO, msg); }
    public void warn(String msg)  { log(Level.WARN, msg); }
    public void error(String msg) { log(Level.ERROR, msg); }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(buffer);
    }

    public synchronized String asText() {
        StringBuilder sb = new StringBuilder();
        for (String s : buffer) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    public synchronized void clear() {
        buffer.clear();
    }

    public synchronized List<String> reversed() {
        List<String> list = new ArrayList<>(buffer);
        Collections.reverse(list);
        return list;
    }
}
