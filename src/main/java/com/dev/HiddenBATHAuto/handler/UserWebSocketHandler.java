package com.dev.HiddenBATHAuto.handler;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class UserWebSocketHandler extends TextWebSocketHandler {

	// 세션 관리용 변수
	private final SessionRepository<? extends Session> sessionRepository;
	private static final Map<String, String> users = new ConcurrentHashMap<>();
	private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper = new ObjectMapper();

	// RedisSessionRepository를 주입받는 생성자
	public UserWebSocketHandler(SessionRepository<? extends Session> sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		String sessionId = session.getId();
		users.put(sessionId, "NOT_LOGGED_IN (/)");
		sessions.put(sessionId, session); // WebSocketSession 저장
		broadcastUserList();
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		Map<String, String> request = objectMapper.readValue(message.getPayload(),
				new TypeReference<Map<String, String>>() {
				});

		if ("update".equals(request.get("action"))) {
			String page = request.get("page");
			String role = request.get("role");

			// 사용자 상태 업데이트: 로그인된 사용자는 ROLE로, 로그인되지 않은 사용자는 NOT_LOGGED_IN 상태로 저장
			if ("NOT_LOGGED_IN".equals(role)) {
				users.put(session.getId(), "NOT_LOGGED_IN (" + page + ")");
			} else {
				users.put(session.getId(), role + " (" + page + ")");
			}
		} else if ("logout".equals(request.get("action"))) {
			String sessionId = request.get("sessionId");
			if (users.containsKey(sessionId)) {
				logOutUser(sessionId); // 로그아웃 처리
			}
		}
		broadcastUserList();
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		String sessionId = session.getId();
		users.remove(sessionId);
		sessions.remove(sessionId); // WebSocketSession 제거
		broadcastUserList();
	}

	private void broadcastUserList() throws Exception {
		String userList = objectMapper.writeValueAsString(users);
		for (String sessionId : sessions.keySet()) {
			WebSocketSession userSession = sessions.get(sessionId);
			if (userSession != null) {
				userSession.sendMessage(new TextMessage(userList));
			}
		}
	}

	private void logOutUser(String sessionId) throws Exception {
		WebSocketSession webSocketSession = sessions.get(sessionId);
		if (webSocketSession != null) {
			webSocketSession.sendMessage(new TextMessage("LOGOUT"));
			webSocketSession.close(); // WebSocket 세션 닫기
		}

		// Redis에서 세션 삭제
		Session session = sessionRepository.findById(sessionId);
		if (session != null) {
			sessionRepository.deleteById(sessionId); // Redis에서 세션 삭제
		}

		// 로그아웃 후 "NOT_LOGGED_IN" 상태로 변경
		users.put(sessionId, "NOT_LOGGED_IN (/)");
	}
}
