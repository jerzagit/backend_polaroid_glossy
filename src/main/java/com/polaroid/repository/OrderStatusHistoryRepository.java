package com.polaroid.repository;

import com.polaroid.model.OrderStatusHistory;
import com.polaroid.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
    long countByStatus(OrderStatus status);
}
