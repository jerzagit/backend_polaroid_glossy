package com.polaroid.service;

import com.polaroid.dto.request.OrderRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.*;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.OrderItemRepository;
import com.polaroid.repository.OrderStatusHistoryRepository;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.UserRepository;
import com.polaroid.dto.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PrintSizeRepository printSizeRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String userEmail) {
        User user = null;
        if (userEmail != null) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }
        
        User affiliate = null;
        if (request.getAffiliateCode() != null && !request.getAffiliateCode().isEmpty()) {
            affiliate = userRepository.findByAffiliateCode(request.getAffiliateCode()).orElse(null);
        }
        
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            PrintSize printSize = printSizeRepository.findById(itemReq.getSizeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + itemReq.getSizeId()));
            
            BigDecimal itemTotal = printSize.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemTotal);
            
            OrderItem item = OrderItem.builder()
                    .sizeId(printSize.getId())
                    .sizeName(printSize.getDisplayName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(printSize.getPrice())
                    .totalPrice(itemTotal)
                    .images(formatJsonArray(itemReq.getImageUrls()))
                    .customTexts(formatJsonArray(itemReq.getCustomTexts()))
                    .build();
            
            orderItems.add(item);
        }
        
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(user != null ? user.getId() : null)
                .affiliateId(affiliate != null ? affiliate.getId() : null)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .customerState(request.getCustomerState() != null ? request.getCustomerState() : "W")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(subtotal)
                .shipping(BigDecimal.ZERO)
                .total(subtotal)
                .items(orderItems)
                .build();
        
        orderItems.forEach(item -> item.setOrder(order));
        
        Order savedOrder = orderRepository.save(order);
        
        addStatusHistory(savedOrder, OrderStatus.PENDING, "Order created");
        
        return orderMapper.toDto(savedOrder);
    }
    
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return orderMapper.toDto(order);
    }
    
    public Page<OrderResponse> getUserOrders(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(orderMapper::toDto);
    }
    
    public Page<OrderResponse> getOrdersWithFilters(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String customerState,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        return orderRepository.findWithFilters(status, paymentStatus, customerState, fromDate, toDate, pageable)
                .map(orderMapper::toDto);
    }
    
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, String message, String userEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        
        switch (newStatus) {
            case PROCESSING -> order.setStatus(OrderStatus.PROCESSING);
            case POSTED -> {
                order.setShippedAt(LocalDateTime.now());
                addStatusHistory(order, OrderStatus.POSTED, message != null ? message : "Order posted");
            }
            case ON_DELIVERY -> addStatusHistory(order, OrderStatus.ON_DELIVERY, message != null ? message : "Out for delivery");
            case DELIVERED -> {
                order.setDeliveredAt(LocalDateTime.now());
                addStatusHistory(order, OrderStatus.DELIVERED, message != null ? message : "Order delivered");
            }
            case CANCELLED -> {
                order.setCancelledAt(LocalDateTime.now());
                order.setCancelReason(message);
                addStatusHistory(order, OrderStatus.CANCELLED, message != null ? message : "Order cancelled");
            }
            default -> addStatusHistory(order, newStatus, message);
        }
        
        order = orderRepository.save(order);
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public OrderResponse updateTrackingNumber(UUID orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setTrackingNumber(trackingNumber);
        order = orderRepository.save(order);
        
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public OrderResponse addNotes(UUID orderId, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setNotes(notes);
        order = orderRepository.save(order);
        
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public void updatePaymentStatus(String orderNumber, PaymentStatus status) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setPaymentStatus(status);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            addStatusHistory(order, order.getStatus(), "Payment received");
        }
        
        orderRepository.save(order);
    }
    
    private void addStatusHistory(Order order, OrderStatus status, String message) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .message(message)
                .build();
        statusHistoryRepository.save(history);
    }
    
    private String generateOrderNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(5);
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "PG" + timestamp + random;
    }
    
    private String formatJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", list) + "\"]";
    }
}
