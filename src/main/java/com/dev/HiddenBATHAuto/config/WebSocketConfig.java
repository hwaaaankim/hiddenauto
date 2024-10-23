package com.dev.HiddenBATHAuto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.dev.HiddenBATHAuto.handler.UserWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RedisSessionRepository sessionRepository;

    public WebSocketConfig(RedisSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userWebSocketHandler(), "/ws/users").setAllowedOrigins("*");
    }

    @Bean
    UserWebSocketHandler userWebSocketHandler() {
        return new UserWebSocketHandler(sessionRepository);
    }
}