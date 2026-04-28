package com.mod.trading.service;

import com.mod.trading.entity.User;
import com.mod.trading.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * يدير كل اتصالات اليوزرات بـ IB Gateway
 * Map<userId, IbkrConnection>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IbkrConnectionPool {

    private final UserRepository userRepository;

    // Pool الرئيسي - كل يوزر له connection خاص
    private final ConcurrentHashMap<Long, IbkrConnection> pool = new ConcurrentHashMap<>();

    // clientId counter - كل connection له clientId مختلف
    private final AtomicInteger clientIdCounter = new AtomicInteger(1);

    // ─── عند بدء التشغيل ──────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        log.info("🚀 IbkrConnectionPool starting...");
        // افتح connections لكل اليوزرات النشطين عند إقلاع السيرفر
        userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getIbkrHost() != null && !u.getIbkrHost().isBlank())
                .forEach(this::openConnection);
        log.info("✅ IbkrConnectionPool ready - {} connections", pool.size());
    }

    // ─── عند إيقاف السيرفر ────────────────────────────────────────────────────

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Closing all IBKR connections...");
        pool.values().forEach(IbkrConnection::disconnect);
        pool.clear();
    }

    // ─── Public Methods ───────────────────────────────────────────────────────

    /**
     * جيب connection اليوزر - لو مو موجود أو منقطع حاول تفتحه
     */
    public IbkrConnection getConnection(Long userId) {
        IbkrConnection conn = pool.get(userId);

        if (conn != null && conn.isConnected()) {
            return conn; // ✅ متصل
        }

        // حاول تعيد الاتصال
        if (conn != null) {
            log.warn("⚠️ [userId={}] Connection lost, reconnecting...", userId);
            pool.remove(userId);
        }

        // جيب بيانات اليوزر من DB وافتح connection جديد
        return userRepository.findById(userId)
                .filter(User::isActive)
                .filter(u -> u.getIbkrHost() != null && !u.getIbkrHost().isBlank())
                .map(u -> {
                    openConnection(u);
                    return pool.get(userId);
                })
                .orElse(null);
    }

    /**
     * افتح connection جديد ليوزر
     */
    public void openConnection(User user) {
        if (user.getIbkrHost() == null || user.getIbkrHost().isBlank()) {
            log.warn("⚠️ [{}] No IBKR host configured, skipping", user.getUsername());
            return;
        }

        // أغلق القديم لو موجود
        IbkrConnection old = pool.get(user.getId());
        if (old != null) old.disconnect();

        IbkrConnection conn = new IbkrConnection(
                user.getId(),
                user.getUsername(),
                user.getIbkrHost(),
                user.getIbkrPort()
        );

        conn.connect(clientIdCounter.getAndIncrement());
        pool.put(user.getId(), conn);
        log.info("🔌 [{}] Connection opened → {}:{}", user.getUsername(), user.getIbkrHost(), user.getIbkrPort());
    }

    /**
     * أغلق connection يوزر
     */
    public void closeConnection(Long userId) {
        IbkrConnection conn = pool.remove(userId);
        if (conn != null) {
            conn.disconnect();
            log.info("🔌 [userId={}] Connection closed", userId);
        }
    }

    /**
     * تحقق إذا اليوزر متصل
     */
    public boolean isConnected(Long userId) {
        IbkrConnection conn = pool.get(userId);
        return conn != null && conn.isConnected();
    }

    /**
     * إحصائيات الـ Pool
     */
    public Map<String, Object> getStats() {
        long connected    = pool.values().stream().filter(IbkrConnection::isConnected).count();
        long disconnected = pool.size() - connected;
        return Map.of(
                "total",        pool.size(),
                "connected",    connected,
                "disconnected", disconnected
        );
    }

    // ─── Background Health Check ──────────────────────────────────────────────

    /**
     * كل دقيقتين - تحقق من الاتصالات المنقطعة وأعد الاتصال
     */
    @Scheduled(fixedDelay = 120_000)
    public void healthCheck() {
        pool.forEach((userId, conn) -> {
            if (!conn.isConnected()) {
                log.warn("⚠️ [{}] Reconnecting...", conn.getUsername());
                userRepository.findById(userId).ifPresent(this::openConnection);
            }
        });
    }
}
