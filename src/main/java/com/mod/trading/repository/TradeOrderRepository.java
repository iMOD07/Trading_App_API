package com.mod.trading.repository;

import com.mod.trading.entity.TradeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    Page<TradeOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<TradeOrder> findByUserIdAndSymbolOrderByCreatedAtDesc(
            Long userId, String symbol, Pageable pageable);

    List<TradeOrder> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TradeOrder> findByClientOrderId(String clientOrderId);

    Optional<TradeOrder> findByIbkrParentOrderId(Integer parentId);

    Optional<TradeOrder> findByIbkrPermId(Long permId);

    @Query("SELECT COALESCE(SUM((o.entryPrice - o.stopLoss) * o.qty), 0) " +
           "FROM TradeOrder o " +
           "WHERE o.user.id = :userId " +
           "AND o.createdAt >= :startOfDay")
    BigDecimal sumPotentialLossesToday(@Param("userId") Long userId,
                                       @Param("startOfDay") LocalDateTime startOfDay);
}
