package com.dev.HiddenBATHAuto.config.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.dev.HiddenBATHAuto.messaging.notification.OrderNotificationWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class OrderNotificationWebSocketConfiguration implements WebSocketConfigurer {

    private final OrderNotificationWebSocketHandler orderNotificationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderNotificationWebSocketHandler, "/ws/order-notifications");
    }
}
