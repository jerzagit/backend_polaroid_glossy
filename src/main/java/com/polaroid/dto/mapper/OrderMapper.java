package com.polaroid.dto.mapper;

import com.polaroid.dto.response.OrderItemResponse;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.dto.response.StatusHistoryResponse;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.OrderStatusHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper implements EntityMapper<Order, OrderResponse> {
    
    @Override
    public OrderResponse toDto(Order entity) {
        if (entity == null) return null;
        
        List<OrderItemResponse> itemResponses = entity.getItems() != null 
                ? entity.getItems().stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList())
                : List.of();
        
        List<StatusHistoryResponse> historyResponses = entity.getStatusHistory() != null
                ? entity.getStatusHistory().stream()
                        .map(this::toHistoryDto)
                        .collect(Collectors.toList())
                : List.of();
        
        return OrderResponse.builder()
                .id(entity.getId().toString())
                .orderNumber(entity.getOrderNumber())
                .userId(entity.getUserId() != null ? entity.getUserId().toString() : null)
                .affiliateId(entity.getAffiliateId() != null ? entity.getAffiliateId().toString() : null)
                .customerName(entity.getCustomerName())
                .customerEmail(entity.getCustomerEmail())
                .customerPhone(entity.getCustomerPhone())
                .customerState(entity.getCustomerState())
                .status(entity.getStatus())
                .paymentStatus(entity.getPaymentStatus())
                .paymentMethod(entity.getPaymentMethod())
                .toyyibpayRef(entity.getToyyibpayRef())
                .subtotal(entity.getSubtotal())
                .shipping(entity.getShipping())
                .total(entity.getTotal())
                .paidAt(entity.getPaidAt())
                .trackingNumber(entity.getTrackingNumber())
                .shippedAt(entity.getShippedAt())
                .deliveredAt(entity.getDeliveredAt())
                .cancelledAt(entity.getCancelledAt())
                .cancelReason(entity.getCancelReason())
                .notes(entity.getNotes())
                .items(itemResponses)
                .statusHistory(historyResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
    
    @Override
    public Order toEntity(OrderResponse dto) {
        if (dto == null) return null;
        
        return Order.builder()
                .id(java.util.UUID.fromString(dto.getId()))
                .orderNumber(dto.getOrderNumber())
                .customerName(dto.getCustomerName())
                .customerEmail(dto.getCustomerEmail())
                .customerPhone(dto.getCustomerPhone())
                .customerState(dto.getCustomerState())
                .status(dto.getStatus())
                .paymentStatus(dto.getPaymentStatus())
                .total(dto.getTotal())
                .notes(dto.getNotes())
                .build();
    }
    
    private OrderItemResponse toItemDto(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId().toString())
                .sizeId(item.getSizeId())
                .sizeName(item.getSizeName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .images(item.getImages())
                .customTexts(item.getCustomTexts())
                .build();
    }
    
    private StatusHistoryResponse toHistoryDto(OrderStatusHistory history) {
        return StatusHistoryResponse.builder()
                .id(history.getId().toString())
                .status(history.getStatus())
                .message(history.getMessage())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
