package com.dev.HiddenBATHAuto.interceptor;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

public class CustomHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            Principal principal = servletRequest.getUserPrincipal();

            if (principal != null) {
                // 로그인한 사용자 정보 및 권한 확인
                String username = principal.getName();
                System.out.println("Authenticated user: " + username);
                
                // 권한 정보도 확인 가능
                boolean isAdmin = servletRequest.isUserInRole("ROLE_ADMIN");
                System.out.println("User has admin role: " + isAdmin);

                // 로그인 여부와 권한을 attributes에 저장하여 WebSocket 핸들러에서 사용 가능
                attributes.put("username", username);
                attributes.put("isAdmin", isAdmin);
            } else {
                System.out.println("Anonymous user connected");
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception ex) {
        // 필요 시 연결 후 처리
    }
}