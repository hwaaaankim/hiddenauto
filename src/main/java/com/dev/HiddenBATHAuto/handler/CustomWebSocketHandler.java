package com.dev.HiddenBATHAuto.handler;

import java.util.Map;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class CustomWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // HandshakeInterceptor에서 설정한 사용자 정보 가져오기
        Map<String, Object> attributes = session.getAttributes();
        String username = (String) attributes.get("username");
        Boolean isAdmin = (Boolean) attributes.get("isAdmin");

        if (username != null) {
            System.out.println("Connected user: " + username + ", Admin: " + isAdmin);
        } else {
            System.out.println("Anonymous user connected with session ID: " + session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 메시지 처리 로직
        System.out.println("Received message: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // WebSocket 연결이 종료되었을 때 처리
        System.out.println("Connection closed with session ID: " + session.getId());
    }
}