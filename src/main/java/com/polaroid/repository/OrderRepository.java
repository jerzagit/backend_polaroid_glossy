package com.polaroid.repository;

import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
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
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);
    
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    Page<Order> findByAffiliateId(UUID affiliateId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);
    
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) AND " +
           "(:customerState IS NULL OR o.customerState = :customerState) AND " +
           "(:fromDate IS NULL OR o.createdAt >= :fromDate) AND " +
           "(:toDate IS NULL OR o.createdAt <= :toDate)")
    Page<Order> findWithFilters(
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("customerState") String customerState,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") OrderStatus status);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.paymentStatus = :paymentStatus")
    long countByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
    
    @Query("SELECT SUM(o.total) FROM Order o WHERE o.paymentStatus = :paymentStatus AND o.createdAt BETWEEN :from AND :to")
    BigDecimal sumTotalByPaymentStatusAndDateRange(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
    
    @Query("SELECT o.customerState, COUNT(o) FROM Order o GROUP BY o.customerState")
    List<Object[]> countByState();
    
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatusGrouped();
    
    @Query("SELECT FUNCTION('DATE', o.createdAt), COUNT(o), SUM(o.total) FROM Order o " +
           "WHERE o.createdAt BETWEEN :from AND :to GROUP BY FUNCTION('DATE', o.createdAt)")
    List<Object[]> getDailySales(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    
    long countByAffiliateId(UUID affiliateId);
    
    List<Order> findByPaymentStatusAndPaidAtBetween(PaymentStatus paymentStatus, LocalDateTime from, LocalDateTime to);
}
