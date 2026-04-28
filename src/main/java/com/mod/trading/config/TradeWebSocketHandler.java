package com.mod.trading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    /** Sessions grouped by username so we can target broadcasts. */
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByUser =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            log.warn("WS session without username; closing");
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
            return;
        }
        sessionsByUser.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WS connected: user={}, sessionId={}", username, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            CopyOnWriteArrayList<WebSocketSession> list = sessionsByUser.get(username);
            if (list != null) {
                list.remove(session);
                if (list.isEmpty()) sessionsByUser.remove(username);
            }
        }
        log.info("WS disconnected: sessionId={}", session.getId());
    }

    /**
     * Sends an event only to the sessions belonging to {@code username}.
     */
    public void sendToUser(String username, String type, Object data) {
        CopyOnWriteArrayList<WebSocketSession> sessions = sessionsByUser.get(username);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try { s.sendMessage(msg); }
                    catch (Exception e) { log.warn("WS send failed: {}", e.getMessage()); }
                }
            }
        } catch (Exception e) {
            log.error("WS broadcast serialization error", e);
        }
    }
}
