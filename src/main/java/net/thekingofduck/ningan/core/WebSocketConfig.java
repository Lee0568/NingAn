package net.thekingofduck.ningan.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSSHHandler webSSHHandler;

    public WebSocketConfig(WebSSHHandler webSSHHandler) {
        this.webSSHHandler = webSSHHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSSHHandler, "/ssh-terminal").setAllowedOrigins("*");
    }
}