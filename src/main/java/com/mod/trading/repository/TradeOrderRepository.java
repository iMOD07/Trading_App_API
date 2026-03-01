package com.mod.trading.repository;

import com.mod.trading.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    List<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TradeOrder> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);
    List<TradeOrder> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
}