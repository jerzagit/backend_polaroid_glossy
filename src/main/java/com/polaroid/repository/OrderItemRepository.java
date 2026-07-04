package com.polaroid.repository;

import com.polaroid.model.OrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);
    Optional<OrderItem> findByOrderIdAndId(UUID orderId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i WHERE i.order.id = :orderId")
    List<OrderItem> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrderItem i WHERE i.order.id = :orderId AND i.id = :id")
    Optional<OrderItem> findByOrderIdAndIdForUpdate(@Param("orderId") UUID orderId, @Param("id") UUID id);
    
    @Query("SELECT oi.sizeId, SUM(oi.quantity) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE o.paymentStatus = 'PAID' " +
           "GROUP BY oi.sizeId " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingSizes();
}
