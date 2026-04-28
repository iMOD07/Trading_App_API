package com.mod.trading.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a single TCP socket connection to IB Gateway / TWS.
 *
 * Lifecycle:
 *   1. Spring starts -> connect() opens socket, starts EReader thread
 *   2. EReader thread reads messages, queues them in EJavaSignal
 *   3. Background thread drains the queue and dispatches to IbkrEventWrapper
 *   4. On disconnect, scheduler retries with exponential backoff
 *
 * Important: TWS API uses a SHARED connection per process. All users in this
 * application share the SAME IB Gateway login. Per-user trading is enforced
 * by application logic (the IBKR account is the operator's, not the end user's).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IbkrConnectionManager {

    private final IbkrEventWrapper eventWrapper;

    @Value("${ibkr.host:127.0.0.1}")
    private String host;

    @Value("${ibkr.port:4002}")
    private int port;

    @Value("${ibkr.client-id:1}")
    private int clientId;

    @Value("${ibkr.connect-on-startup:true}")
    private boolean connectOnStartup;

    private EClientSocket clientSocket;
    private EJavaSignal signal;
    private EReader reader;
    private Thread readerThread;
    private Thread messageProcessorThread;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        if (connectOnStartup) {
            connect();
        } else {
            log.info("IBKR connect-on-startup disabled. Call connect() manually.");
        }
    }

    public synchronized void connect() {
        if (clientSocket != null && clientSocket.isConnected()) {
            log.info("Already connected to IB Gateway");
            return;
        }

        signal = new EJavaSignal();
        clientSocket = new EClientSocket(eventWrapper, signal);
        eventWrapper.setClientSocket(clientSocket);

        log.info("Connecting to IB Gateway at {}:{} clientId={}", host, port, clientId);
        clientSocket.eConnect(host, port, clientId);

        // Wait briefly for handshake
        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        if (!clientSocket.isConnected()) {
            log.error("Failed to connect to IB Gateway. Is it running and API enabled?");
            scheduleReconnect();
            return;
        }

        // Start EReader thread (reads raw bytes from socket)
        reader = new EReader(clientSocket, signal);
        reader.start();
        readerThread = new Thread(reader, "ibkr-ereader");
        readerThread.setDaemon(true);

        // Start message processor (drains the queue, dispatches to wrapper)
        running.set(true);
        messageProcessorThread = new Thread(this::processMessages, "ibkr-msg-processor");
        messageProcessorThread.setDaemon(true);
        messageProcessorThread.start();

        reconnectAttempts.set(0);
        log.info("✅ Connected to IB Gateway");
    }

    private void processMessages() {
        while (running.get() && clientSocket.isConnected()) {
            signal.waitForSignal();
            try {
                reader.processMsgs();
            } catch (Exception e) {
                log.error("Error processing IBKR message", e);
            }
        }
        log.info("Message processor exiting");
    }

    /**
     * Called by IbkrEventWrapper when the connection drops.
     */
    public void onDisconnected() {
        log.warn("Disconnected from IB Gateway");
        running.set(false);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        long delaySeconds = Math.min(60, (long) Math.pow(2, Math.min(attempt, 6)));
        log.info("Scheduling reconnect attempt #{} in {}s", attempt, delaySeconds);

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000);
                if (!isConnected()) connect();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "ibkr-reconnect-" + attempt);
        t.setDaemon(true);
        t.start();
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected();
    }

    public EClientSocket getClient() {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to IB Gateway");
        }
        return clientSocket;
    }

    @PreDestroy
    public synchronized void shutdown() {
        log.info("Shutting down IBKR connection");
        running.set(false);
        if (clientSocket != null && clientSocket.isConnected()) {
            clientSocket.eDisconnect();
        }
        if (messageProcessorThread != null) messageProcessorThread.interrupt();
    }
}
