package com.mod.trading.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Manages a single IBKR connection for ONE user.
 * Each user has their own VPS with IB Gateway running.
 *
 * This class is NOT a Spring bean - it's instantiated per-user
 * by IbkrConnectionPool, which passes in the shared {@link ApplicationEventPublisher}.
 */
@Slf4j
public class IbkrConnectionManager {

    private final String userTag;
    private final String host;
    private final int port;
    private final int clientId;

    @Getter
    private final IbkrEventWrapper wrapper;

    private final EJavaSignal signal;
    private final EClientSocket clientSocket;
    private EReader reader;
    private Thread readerThread;

    public IbkrConnectionManager(String userTag, String host, int port, int clientId,
                                 ApplicationEventPublisher eventPublisher) {
        this.userTag = userTag;
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.wrapper = new IbkrEventWrapper(userTag, eventPublisher);
        this.signal = new EJavaSignal();
        this.clientSocket = new EClientSocket(wrapper, signal);
    }

    /**
     * Connect to user's IB Gateway. Blocks until connected or fails.
     * Returns true if successful.
     */
    public synchronized boolean connect() {
        if (isConnected()) {
            log.debug("[{}] Already connected", userTag);
            return true;
        }

        log.info("[{}] Connecting to IB Gateway at {}:{} (clientId={})",
                userTag, host, port, clientId);

        try {
            clientSocket.eConnect(host, port, clientId);

            if (!clientSocket.isConnected()) {
                log.error("[{}] Failed to connect to {}:{}", userTag, host, port);
                return false;
            }

            // Start reader thread
            reader = new EReader(clientSocket, signal);
            reader.start();

            readerThread = new Thread(() -> {
                while (clientSocket.isConnected()) {
                    signal.waitForSignal();
                    try {
                        reader.processMsgs();
                    } catch (Exception e) {
                        log.error("[{}] Error processing message", userTag, e);
                    }
                }
            }, "ibkr-reader-" + userTag);
            readerThread.setDaemon(true);
            readerThread.start();

            // Wait for nextValidId callback (max 5 seconds)
            int waited = 0;
            while (!wrapper.hasNextOrderId() && waited < 5000) {
                Thread.sleep(100);
                waited += 100;
            }

            if (!wrapper.hasNextOrderId()) {
                log.error("[{}] Timeout waiting for nextValidId", userTag);
                disconnect();
                return false;
            }

            log.info("[{}] Connected successfully", userTag);
            return true;

        } catch (Exception e) {
            log.error("[{}] Connect failed", userTag, e);
            disconnect();
            return false;
        }
    }

    public synchronized void disconnect() {
        try {
            if (clientSocket != null && clientSocket.isConnected()) {
                clientSocket.eDisconnect();
            }
            if (readerThread != null) {
                readerThread.interrupt();
            }
            log.info("[{}] Disconnected", userTag);
        } catch (Exception e) {
            log.error("[{}] Error during disconnect", userTag, e);
        }
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && wrapper.isConnected();
    }

    public EClientSocket getClientSocket() {
        return clientSocket;
    }

    public String getUserTag() {
        return userTag;
    }
}

