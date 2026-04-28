package com.mod.trading.config;

import com.mod.trading.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT supplied as the {@code token} query parameter during the
 * WebSocket upgrade. If valid, the username is placed in session attributes.
 *
 * Connect URL example: ws://host/ws/trades?token=eyJhbGc...
 */
@Slf4j
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.debug("WS handshake rejected: no token");
            return false;
        }
        try {
            String username = jwtService.extractUsername(token);
            if (username == null || !jwtService.isTokenValid(token, username)) {
                log.debug("WS handshake rejected: invalid token");
                return false;
            }
            attributes.put("username", username);
            return true;
        } catch (Exception e) {
            log.debug("WS handshake error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
