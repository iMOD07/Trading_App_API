package com.mod.trading.repository;

import com.mod.trading.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    List<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TradeOrder> findByUserIdAndSymbolOrderByCreatedAtDesc(Long userId, String symbol);

    List<TradeOrder> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TradeOrder> findByClientOrderId(String clientOrderId);

    /**
     * Find the parent {@link TradeOrder} for any given IBKR orderId.
     * The same callback may arrive for the parent, take-profit, or stop-loss leg —
     * we return the single TradeOrder row that owns any of the three IDs.
     *
     * Performance: indexed on all three columns in V2 migration.
     */
    @Query("""
        SELECT o FROM TradeOrder o
        WHERE o.ibkrParentOrderId      = :ibkrId
           OR o.ibkrTakeProfitOrderId  = :ibkrId
           OR o.ibkrStopLossOrderId    = :ibkrId
        """)
    Optional<TradeOrder> findByAnyIbkrOrderId(@Param("ibkrId") Integer ibkrId);

    /**
     * Used by reconciliation job (B4 — coming in next sprint).
     * Returns rows that are still "open" from our point of view, so we can
     * compare against IBKR's openOrders() snapshot.
     */
    @Query("""
        SELECT o FROM TradeOrder o
        WHERE o.user.id = :userId
          AND o.orderStatus IN ('PENDING','SUBMITTED','PRESUBMITTED','PARTIALLY_FILLED','PENDING_CANCEL')
        """)
    List<TradeOrder> findOpenOrdersByUserId(@Param("userId") Long userId);
}
