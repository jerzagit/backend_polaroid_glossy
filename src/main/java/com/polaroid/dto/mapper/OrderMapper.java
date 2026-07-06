package com.polaroid.dto.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.OrderItemResponse;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.dto.response.StatusHistoryResponse;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.OrderStatusHistory;
import com.polaroid.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper implements EntityMapper<Order, OrderResponse> {
    private static final int IMAGE_URL_EXPIRATION_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final StorageService storageService;
    
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
                .customerHouseUnitNo(entity.getCustomerHouseUnitNo())
                .customerAddressLine1(entity.getCustomerAddressLine1())
                .customerAddressLine2(entity.getCustomerAddressLine2())
                .customerPostcode(entity.getCustomerPostcode())
                .customerCity(entity.getCustomerCity())
                .customerState(entity.getCustomerState())
                .customerCountry(entity.getCustomerCountry())
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
                .expiresAt(entity.getExpiresAt())
                .draftExpiredAt(entity.getDraftExpiredAt())
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
                .customerHouseUnitNo(dto.getCustomerHouseUnitNo())
                .customerAddressLine1(dto.getCustomerAddressLine1())
                .customerAddressLine2(dto.getCustomerAddressLine2())
                .customerPostcode(dto.getCustomerPostcode())
                .customerCity(dto.getCustomerCity())
                .customerState(dto.getCustomerState())
                .customerCountry(dto.getCustomerCountry())
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
                .expectedImageCount(item.getExpectedImageCount())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .images(freshImageUrls(item))
                .customTexts(readJsonStringList(item.getCustomTexts()))
                .build();
    }

    private List<String> freshImageUrls(OrderItem item) {
        List<String> keys = readJsonStringList(item.getS3Keys());
        if (!keys.isEmpty()) {
            return keys.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(this::renderableImageUrl)
                    .collect(Collectors.toList());
        }
        return readJsonStringList(item.getImages());
    }

    private String renderableImageUrl(String keyOrUrl) {
        if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            return keyOrUrl;
        }
        return storageService.getSignedUrl(keyOrUrl, IMAGE_URL_EXPIRATION_SECONDS);
    }

    private List<String> readJsonStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse order item JSON metadata: {}", e.getMessage());
            return List.of();
        }
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
