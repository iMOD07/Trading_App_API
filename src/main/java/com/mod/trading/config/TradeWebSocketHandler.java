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
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByUser =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
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
    }

    /** Send to one user's sessions. Use for order placement, fills, etc. */
    public void sendToUser(String username, String type, Object data) {
        CopyOnWriteArrayList<WebSocketSession> sessions = sessionsByUser.get(username);
        if (sessions == null || sessions.isEmpty()) return;
        TextMessage msg = serialize(type, data);
        if (msg != null) sendAll(sessions, msg);
    }

    /**
     * Broadcast to all connected sessions. Used by IBKR callbacks where we
     * don't yet know which user owns the orderId at callback time.
     */
    public void broadcast(String type, Object data) {
        TextMessage msg = serialize(type, data);
        if (msg == null) return;
        sessionsByUser.values().forEach(list -> sendAll(list, msg));
    }

    private TextMessage serialize(String type, Object data) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
            return new TextMessage(json);
        } catch (Exception e) {
            log.error("WS serialization error", e);
            return null;
        }
    }

    private void sendAll(CopyOnWriteArrayList<WebSocketSession> sessions, TextMessage msg) {
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try { s.sendMessage(msg); }
                catch (Exception e) { log.warn("WS send failed: {}", e.getMessage()); }
            }
        }
    }
}
