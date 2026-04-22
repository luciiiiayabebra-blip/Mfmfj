package ru.rws.config;

public class ProxyEntry {
    public enum Type {
        SOCKS5,
        HTTP
    }

    public boolean enabled = false;
    public Type type = Type.SOCKS5;
    public String host = "";
    public int port = 1080;
    public String username = "";
    public String password = "";

    public ProxyEntry() {
    }

    public boolean hasAuth() {
        return username != null && !username.isEmpty();
    }
}
