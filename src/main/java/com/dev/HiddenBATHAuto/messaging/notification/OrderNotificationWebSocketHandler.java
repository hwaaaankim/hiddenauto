package com.dev.HiddenBATHAuto.messaging.notification;

import java.io.IOException;
import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderNotificationWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> sessionsByUsername =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = resolveUsername(session);
        if (username == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("인증된 사용자만 연결할 수 있습니다."));
            return;
        }

        sessionsByUsername.computeIfAbsent(username, key -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("오더 알림 WebSocket 연결: username={}, sessionId={}", username, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        removeSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void sendToUsername(String username, String jsonPayload) {
        if (username == null || username.isBlank() || jsonPayload == null) return;

        Set<WebSocketSession> sessions = sessionsByUsername.get(username);
        if (sessions == null || sessions.isEmpty()) return;

        TextMessage message = new TextMessage(jsonPayload);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeSession(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("오더 알림 WebSocket 전송 실패: username={}, sessionId={}", username, session.getId(), e);
                removeSession(session);
            }
        }
    }

    private void removeSession(WebSocketSession session) {
        String username = resolveUsername(session);
        if (username == null) return;

        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByUsername.get(username);
        if (sessions == null) return;

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUsername.remove(username, sessions);
        }
    }

    private String resolveUsername(WebSocketSession session) {
        Principal principal = session != null ? session.getPrincipal() : null;
        String username = principal != null ? principal.getName() : null;
        return username == null || username.isBlank() ? null : username.trim();
    }
}
