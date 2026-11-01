package com.polaroid.service;

import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCleanupServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final EmailService emailService = mock(EmailService.class);

    @Test
    void recentlyReopenedOrderGetsAFreshPaymentWindow() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("PG-REOPENED-1")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .reopenedAt(now.minusHours(1))
                .build();
        ReflectionTestUtils.setField(order, "createdAt", now.minusDays(3));
        when(orderRepository.findByPaymentStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of(order));

        service().cleanupExpiredPayments();

        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(orderRepository, never()).save(order);
        verify(orderService, never()).applyCancellationRetention(any());
    }

    @Test
    void reopenedOrderStillExpiresAfterItsFreshWindow() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("PG-REOPENED-2")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .reopenedAt(now.minusHours(25))
                .build();
        ReflectionTestUtils.setField(order, "createdAt", now.minusDays(3));
        when(orderRepository.findByPaymentStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of(order));

        service().cleanupExpiredPayments();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderService).applyCancellationRetention(order);
        verify(orderRepository).save(order);
    }

    @Test
    void oldPendingOrderIsAutoCancelledAndScheduledForRetention() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("PG-OLD-1")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(order, "createdAt", now.minusDays(3));
        when(orderRepository.findByPaymentStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of(order));

        service().cleanupExpiredPayments();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderService).applyCancellationRetention(order);
        verify(orderRepository).save(order);
    }

    private PaymentCleanupService service() throws Exception {
        PaymentCleanupService service = new PaymentCleanupService(orderRepository, orderService, emailService);
        setField(service, "expirationHours", 24);
        setField(service, "reminderHours", 14);
        return service;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
