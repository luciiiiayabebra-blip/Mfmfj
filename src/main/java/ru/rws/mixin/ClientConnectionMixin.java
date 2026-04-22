package ru.rws.mixin;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.rws.config.ProxyEntry;
import ru.rws.core.LogBuffer;
import ru.rws.net.ProxyConnector;

import java.lang.reflect.Method;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Redirect(
            method = "connect(Ljava/net/InetAddress;IZ)Lnet/minecraft/network/ClientConnection;",
            at = @At(value = "INVOKE",
                    target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;"),
            require = 0
    )
    private static AbstractBootstrap<?, ?> rws$wrapHandler(Bootstrap bootstrap, final ChannelHandler original) {
        final ProxyEntry proxy = ProxyConnector.consumeProxy();
        if (proxy == null || !proxy.enabled) {
            return bootstrap.handler(original);
        }
        LogBuffer.get().info("Подключение через прокси: " + proxy.type + " " + proxy.host + ":" + proxy.port);
        ChannelInitializer<Channel> wrapper = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                try {
                    ProxyConnector.applyToChannel(ch, proxy);
                    Method m = findInitChannel(original.getClass());
                    if (m != null) {
                        m.setAccessible(true);
                        m.invoke(original, ch);
                    } else {
                        LogBuffer.get().error("Не удалось найти initChannel у " + original.getClass().getName());
                    }
                } catch (Throwable t) {
                    LogBuffer.get().error("Ошибка инициализации канала: " + t.getMessage());
                    throw t;
                }
            }
        };
        return bootstrap.handler(wrapper);
    }

    private static Method findInitChannel(Class<?> cls) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if ("initChannel".equals(m.getName())
                        && m.getParameterCount() == 1
                        && Channel.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
