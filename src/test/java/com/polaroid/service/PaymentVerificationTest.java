package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.mapper.OrderMapper;
import com.polaroid.dto.request.VerifyPaymentRequest;
import com.polaroid.exception.*;
import com.polaroid.model.*;
import com.polaroid.model.enums.*;
import com.polaroid.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PaymentVerificationTest {
    final ObjectMapper json = new ObjectMapper();
    final OrderRepository orders = mock(OrderRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final EmailService mail = mock(EmailService.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final OrderService service = new OrderService(orders, null, history, null, users, mock(OrderMapper.class), mail, json, null);
    Order order;
    VerifyPaymentRequest request;
    @BeforeEach void setup() {
        when(users.findByEmail("admin")).thenReturn(Optional.of(User.builder().role(Role.ADMIN).build()));
        order = Order.builder().id(UUID.randomUUID()).orderNumber("PG-VERIFY").status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING).total(new BigDecimal("17.00"))
                .paymentProofUrl("proof-1").paymentProofSource("WHATSAPP").paymentReviewStatus("AWAITING_VERIFICATION").build();
        order.getItems().add(OrderItem.builder().sizeId("ic").quantity(1).s3Keys("[\"photo.jpg\"]").build());
        when(orders.lockByOrderNumber("PG-VERIFY")).thenReturn(Optional.of(order));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        request = new VerifyPaymentRequest(); request.setAction("APPROVE"); request.setFundsConfirmed(true);
        request.setAmountReceived(new BigDecimal("17.00")); request.setReference("BANK-123"); request.setProofKey("proof-1");
    }
    @Test void approvalRecordsAuditButRequiresSeparateProcessingAndIsIdempotent() {
        service.verifyPayment("PG-VERIFY", request, "admin");
        var verifiedAt = order.getPaymentVerifiedAt(); var paidAt = order.getPaidAt();
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("17.00"), order.getPaymentAmountReceived());
        assertEquals("admin", order.getPaymentVerifiedBy());
        assertEquals("proof-1", PaymentReviews.read(order, json).get(0).get("proofKey"));
        service.verifyPayment("PG-VERIFY", request, "admin");
        assertEquals(verifiedAt, order.getPaymentVerifiedAt()); assertEquals(paidAt, order.getPaidAt());
        assertEquals(1, PaymentReviews.read(order, json).size());
        verify(mail, times(1)).sendPaymentConfirmation(order);
        verify(mail, times(1)).sendNewOrderAlert(order);
        service.updateOrderStatus(order.getId(), OrderStatus.PROCESSING, null, "admin");
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
    }
    @Test void approvalDoesNotBypassMissingPhotos() {
        order.getItems().get(0).setS3Keys("[]");
        service.verifyPayment("PG-VERIFY", request, "admin");
        assertThrows(BadRequestException.class, () -> service.updateOrderStatus(order.getId(), OrderStatus.PROCESSING, null, "admin"));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }
    @Test void requiresFundsCheckboxAndExactAmountIncludingDelivery() {
        request.setFundsConfirmed(false);
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
        request.setFundsConfirmed(true); request.setAmountReceived(new BigDecimal("10.00"));
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus()); verifyNoInteractions(mail);
    }
    @Test void rejectsStaleProofAndClosedOrders() {
        request.setProofKey("old-proof");
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
        request.setProofKey("proof-1"); order.setStatus(OrderStatus.CANCELLED);
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
    }
    @Test void marketingAndPackersCannotApprove() {
        for (Role role : List.of(Role.MARKETING, Role.PACKER, Role.CUSTOMER)) {
            when(users.findByEmail("other")).thenReturn(Optional.of(User.builder().role(role).build()));
            assertThrows(ForbiddenException.class, () -> service.verifyPayment("PG-VERIFY", request, "other"));
        }
        verifyNoInteractions(mail);
    }
    @Test void rejectionKeepsProofAndReasonAndCannotUndoPayment() {
        request.setAction("REJECT");
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
        request.setReason("Reference does not match bank statement");
        service.verifyPayment("PG-VERIFY", request, "admin");
        assertEquals("proof-1", order.getPaymentProofUrl());
        assertEquals("REJECTED", order.getPaymentReviewStatus());
        assertEquals(request.getReason(), PaymentReviews.read(order, json).get(0).get("notes"));
        assertTrue(PaymentCleanupService.awaitingPayment(order));
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThrows(BadRequestException.class, () -> service.verifyPayment("PG-VERIFY", request, "admin"));
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
    }
}
