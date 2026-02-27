package com.polaroid.dto.response;

import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String orderNumber;
    private String userId;
    private String affiliateId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerState;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private String toyyibpayRef;
    private BigDecimal subtotal;
    private BigDecimal shipping;
    private BigDecimal total;
    private LocalDateTime paidAt;
    private String trackingNumber;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String notes;
    private List<OrderItemResponse> items;
    private List<StatusHistoryResponse> statusHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
