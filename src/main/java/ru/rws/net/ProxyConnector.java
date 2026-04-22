package ru.rws.net;

import io.netty.channel.Channel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import ru.rws.config.ProxyEntry;

import java.net.InetSocketAddress;

public final class ProxyConnector {

    private static final ThreadLocal<ProxyEntry> CURRENT = new ThreadLocal<>();

    private ProxyConnector() {
    }

    public static void setProxy(ProxyEntry entry) {
        if (entry == null || !entry.enabled || entry.host == null || entry.host.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(entry);
        }
    }

    public static ProxyEntry consumeProxy() {
        ProxyEntry e = CURRENT.get();
        CURRENT.remove();
        return e;
    }

    public static void applyToChannel(Channel channel, ProxyEntry entry) {
        if (entry == null || !entry.enabled || entry.host == null || entry.host.isEmpty()) {
            return;
        }
        InetSocketAddress addr = new InetSocketAddress(entry.host, entry.port);
        String user = entry.hasAuth() ? entry.username : null;
        String pass = entry.hasAuth() ? entry.password : null;
        switch (entry.type) {
            case HTTP:
                if (user != null) {
                    channel.pipeline().addFirst("rws_proxy", new HttpProxyHandler(addr, user, pass));
                } else {
                    channel.pipeline().addFirst("rws_proxy", new HttpProxyHandler(addr));
                }
                break;
            case SOCKS5:
            default:
                if (user != null) {
                    channel.pipeline().addFirst("rws_proxy", new Socks5ProxyHandler(addr, user, pass));
                } else {
                    channel.pipeline().addFirst("rws_proxy", new Socks5ProxyHandler(addr));
                }
                break;
        }
    }
}
