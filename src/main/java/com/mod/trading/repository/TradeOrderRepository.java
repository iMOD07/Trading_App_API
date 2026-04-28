package com.mod.trading.repository;

import com.mod.trading.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TradeOrder> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);
    List<TradeOrder> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<TradeOrder> findByIbkrOrderId(String ibkrOrderId);
    List<TradeOrder> findByPendingTrue(); // ← الأوامر المعلقة
}
