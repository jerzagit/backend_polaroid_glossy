package com.polaroid.service;

import com.polaroid.dto.response.StatsOverviewResponse;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    
    public StatsOverviewResponse getOverview() {
        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        long processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
        long deliveredOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);
        
        long paidOrders = orderRepository.countByPaymentStatus(PaymentStatus.PAID);
        long pendingPayments = orderRepository.countByPaymentStatus(PaymentStatus.PENDING);
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        BigDecimal totalRevenue = orderRepository.sumTotalByPaymentStatusAndDateRange(
                PaymentStatus.PAID, thirtyDaysAgo, LocalDateTime.now());
        
        return StatsOverviewResponse.builder()
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .paidOrders(paidOrders)
                .pendingPayments(pendingPayments)
                .build();
    }
    
    public List<Object[]> getOrdersByStatus() {
        return orderRepository.countByStatusGrouped();
    }
    
    public List<Object[]> getOrdersByState() {
        return orderRepository.countByState();
    }
    
    public List<Object[]> getTopSellingSizes() {
        return orderItemRepository.findTopSellingSizes();
    }
    
    public List<Object[]> getDailySales(LocalDateTime from, LocalDateTime to) {
        return orderRepository.getDailySales(from, to);
    }
    
    public Map<String, Long> getOrdersByStatusMap() {
        List<Object[]> results = orderRepository.countByStatusGrouped();
        Map<String, Long> statusMap = new HashMap<>();
        for (Object[] row : results) {
            statusMap.put(row[0].toString(), (Long) row[1]);
        }
        return statusMap;
    }
}
