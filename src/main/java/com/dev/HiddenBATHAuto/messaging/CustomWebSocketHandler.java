package com.dev.HiddenBATHAuto.messaging;

//public class CustomWebSocketHandler extends TextWebSocketHandler {
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        Map<String, Object> attributes = session.getAttributes();
//        String username = (String) attributes.get("username");
//        Boolean isAdmin = (Boolean) attributes.get("isAdmin");
//
//        if (username != null) {
//            System.out.println("Connected user: " + username + ", Admin: " + isAdmin);
//        } else {
//            System.out.println("Anonymous user connected with session ID: " + session.getId());
//        }
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        // 메시지 처리 로직
//        System.out.println("Received message: " + message.getPayload());
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        // WebSocket 연결이 종료되었을 때 처리
//        System.out.println("Connection closed with session ID: " + session.getId());
//    }
//}