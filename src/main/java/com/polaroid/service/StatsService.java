package com.polaroid.service;

import com.polaroid.dto.response.StatsOverviewResponse;
import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.OrderItemRepository;
import com.polaroid.repository.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StatsService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    
    public StatsOverviewResponse getOverview() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return getOverview(null, null, null, thirtyDaysAgo, LocalDateTime.now(), null, null, null);
    }

    public StatsOverviewResponse getOverview(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String customerState,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String orderReference,
            String customerEmail,
            String customerPhone) {
        Specification<Order> baseSpec = OrderSpecifications.withFilters(
                status,
                paymentStatus,
                customerState,
                fromDate,
                toDate,
                orderReference,
                customerEmail,
                customerPhone);

        long totalOrders = orderRepository.count(baseSpec);
        long pendingOrders = orderRepository.count(baseSpec.and(OrderSpecifications.hasStatus(OrderStatus.PENDING)));
        long processingOrders = orderRepository.count(baseSpec.and(OrderSpecifications.hasStatus(OrderStatus.PROCESSING)));
        long deliveredOrders = orderRepository.count(baseSpec.and(OrderSpecifications.hasStatus(OrderStatus.DELIVERED)));
        long cancelledOrders = orderRepository.count(baseSpec.and(OrderSpecifications.hasStatus(OrderStatus.CANCELLED)));

        long paidOrders = orderRepository.count(baseSpec.and(OrderSpecifications.hasPaymentStatus(PaymentStatus.PAID)));
        long pendingPayments = orderRepository.count(baseSpec.and(OrderSpecifications.hasPaymentStatus(PaymentStatus.PENDING)));

        List<Order> filteredOrders = orderRepository.findAll(baseSpec);
        BigDecimal totalRevenue = filteredOrders.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalCustomers = filteredOrders.stream()
                .map(Order::getCustomerEmail)
                .filter(Objects::nonNull)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        
        return StatsOverviewResponse.builder()
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .paidOrders(paidOrders)
                .pendingPayments(pendingPayments)
                .totalCustomers(totalCustomers)
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
