package com.urleads.config;

import com.urleads.websocket.NotificationWebSocketHandler;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;

/**
 * Configuración de WebSocket estándar (RFC 6455) sin STOMP ni SockJS.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Asegura que WebSocketHandlerMapping tenga prioridad (orden -1) sobre RequestMappingHandlerMapping (orden 0),
     * evitando que el catch-all del SpaController intercepte los handshakes de WebSocket.
     */
    @Bean
    public static BeanPostProcessor webSocketHandlerMappingOrderCustomizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof WebSocketHandlerMapping mapping) {
                    mapping.setOrder(-1);
                }
                return bean;
            }
        };
    }
}
