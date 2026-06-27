package com.polaroid.repository;

import com.polaroid.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
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
    
    @Query("SELECT oi.sizeId, SUM(oi.quantity) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE o.paymentStatus = 'PAID' " +
           "GROUP BY oi.sizeId " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingSizes();
}
