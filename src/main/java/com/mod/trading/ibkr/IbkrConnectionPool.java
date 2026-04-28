package com.mod.trading.ibkr;

import com.mod.trading.entity.User;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all active IBKR connections, one per user.
 *
 * Lifecycle:
 * - Connection is created lazily when user makes their first trade
 * - Connection is reused for subsequent trades by same user
 * - Connections are closed on application shutdown
 * - If a connection breaks, it's removed and a new one is created on next request
 */
@Slf4j
@Component
public class IbkrConnectionPool {

    private final Map<Long, IbkrConnectionManager> connections = new ConcurrentHashMap<>();

    /**
     * Get or create a connection for the given user.
     * If existing connection is broken, it's recreated.
     */
    public synchronized IbkrConnectionManager getConnection(User user) {
        if (!user.isIbkrConfigured()) {
            throw new IbkrException(
                "User '" + user.getUsername() + "' has not configured IBKR settings. " +
                "Admin must set ibkrHost, ibkrPort, and ibkrAccountId."
            );
        }

        IbkrConnectionManager existing = connections.get(user.getId());

        // Reuse if still connected
        if (existing != null && existing.isConnected()) {
            return existing;
        }

        // Cleanup broken connection
        if (existing != null) {
            log.warn("[{}] Connection broken, recreating", user.getUsername());
            existing.disconnect();
            connections.remove(user.getId());
        }

        // Create new connection
        IbkrConnectionManager manager = new IbkrConnectionManager(
                user.getUsername(),
                user.getIbkrHost(),
                user.getIbkrPort(),
                user.getIbkrClientId()
        );

        if (!manager.connect()) {
            throw new IbkrException(
                "Failed to connect to IB Gateway for user '" + user.getUsername() + "' " +
                "at " + user.getIbkrHost() + ":" + user.getIbkrPort() + ". " +
                "Check VPS is running and IB Gateway is logged in."
            );
        }

        connections.put(user.getId(), manager);
        log.info("Created new IBKR connection for user [{}] (total active: {})",
                user.getUsername(), connections.size());

        return manager;
    }

    /**
     * Disconnect a specific user's connection (e.g. when user is deactivated).
     */
    public void disconnectUser(Long userId) {
        IbkrConnectionManager manager = connections.remove(userId);
        if (manager != null) {
            manager.disconnect();
            log.info("Disconnected user id={}", userId);
        }
    }

    public int getActiveConnectionCount() {
        return (int) connections.values().stream().filter(IbkrConnectionManager::isConnected).count();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IBKR connection pool ({} connections)", connections.size());
        connections.values().forEach(IbkrConnectionManager::disconnect);
        connections.clear();
    }
}
