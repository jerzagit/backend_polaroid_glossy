package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.mapper.OrderMapper;
import com.polaroid.dto.request.VerifyPaymentRequest;
import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shop is only alerted about orders that actually got paid, and only once:
 * a duplicate payment callback must not email the shop twice.
 */
class OrderServicePaymentAlertTest {

    private static final String ORDER_NUMBER = "PGTEST0001";

    private Order order(PaymentStatus paymentStatus) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(ORDER_NUMBER);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private OrderService service(OrderRepository repository,
                                 OrderStatusHistoryRepository history,
                                 EmailService mail,
                                 OrderMapper mapper) {
        OrderService service = new OrderService(repository, null, history, null, null, mapper, mail,
                new ObjectMapper(), null);
        setField(service, "uploadTokenExpirationHours", 168);
        return service;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void paymentCallbackAlertsShopOncePerOrder() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
        EmailService mail = mock(EmailService.class);
        Order order = order(PaymentStatus.PENDING);
        when(repository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(repository.lockById(order.getId())).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenReturn(order);

        OrderService service = service(repository, history, mail, mock(OrderMapper.class));
        service.updatePaymentStatus(ORDER_NUMBER, PaymentStatus.PAID);

        verify(mail, times(1)).sendPaymentConfirmation(order);
        verify(mail, times(1)).sendNewOrderAlert(order);
    }

    @Test
    void duplicatePaymentCallbackDoesNotEmailAgain() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
        EmailService mail = mock(EmailService.class);
        Order order = order(PaymentStatus.PENDING);
        when(repository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(repository.lockById(order.getId())).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenReturn(order);

        OrderService service = service(repository, history, mail, mock(OrderMapper.class));
        service.updatePaymentStatus(ORDER_NUMBER, PaymentStatus.PAID);
        clearInvocations(mail);

        service.updatePaymentStatus(ORDER_NUMBER, PaymentStatus.PAID);

        verify(mail, never()).sendPaymentConfirmation(any(Order.class));
        verify(mail, never()).sendNewOrderAlert(any(Order.class));
    }

    @Test
    void adminApprovingBankTransferAlertsShop() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
        EmailService mail = mock(EmailService.class);
        Order order = order(PaymentStatus.PENDING);
        when(repository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(repository.lockById(order.getId())).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenReturn(order);

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setAction("APPROVE");

        OrderService service = service(repository, history, mail, mock(OrderMapper.class));
        service.verifyPayment(ORDER_NUMBER, request, "admin@polaroidglossy.my");

        verify(mail, times(1)).sendPaymentConfirmation(order);
        verify(mail, times(1)).sendNewOrderAlert(order);
    }
}
