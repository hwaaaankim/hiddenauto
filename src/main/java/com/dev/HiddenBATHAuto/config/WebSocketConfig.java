package com.dev.HiddenBATHAuto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.dev.HiddenBATHAuto.handler.CustomWebSocketHandler;
import com.dev.HiddenBATHAuto.interceptor.CustomHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker // STOMP 메시지 브로커 활성화
@EnableWebSocket              // 기본 WebSocket 핸들러 활성화
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    // 1. STOMP 메시지 브로커 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // 브로커가 사용할 주제
        config.setApplicationDestinationPrefixes("/app"); // 클라이언트 메시지 전송 경로
    }

    // 2. STOMP 엔드포인트 설정 및 인터셉터 추가
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // CORS 설정: allowedOriginPatterns("*") 사용해 모든 도메인 허용
                .setAllowedOriginPatterns("*")
                .addInterceptors(new CustomHandshakeInterceptor()) // STOMP 엔드포인트용 인터셉터 추가
                .withSockJS(); // SockJS 지원
    }

    // 3. WebSocket 핸들러 설정 (STOMP 외 WebSocket 핸들러)
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CustomWebSocketHandler(), "/custom-ws") // STOMP 외 WebSocket 핸들러
                .setAllowedOriginPatterns("*") // allowedOriginPatterns("*") 사용
                .addInterceptors(new CustomHandshakeInterceptor()); // WebSocket 핸들러용 인터셉터
    }
}