package com.polaroid.service;

import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCleanupService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EmailService emailService;

    @Value("${app.payment.expiration-hours:24}")
    private int expirationHours;

    @Value("${app.payment.reminder-hours:14}")
    private int reminderHours;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expirationThreshold = now.minusHours(expirationHours);
        LocalDateTime reminderThreshold = now.minusHours(reminderHours);

        List<Order> expiredOrders = orderRepository.findByPaymentStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, expirationThreshold);

        for (Order order : expiredOrders) {
            if (!awaitingPayment(order)) continue;
            // A reopened order gets a fresh payment window; createdAt is unchanged.
            if (order.getReopenedAt() != null && order.getReopenedAt().isAfter(expirationThreshold)) continue;
            try {
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setCancelReason("Auto-cancelled: payment not received within " + expirationHours + " hours");
                orderService.applyCancellationRetention(order);
                orderRepository.save(order);
                log.info("Auto-cancelled unpaid order {} (created {})", order.getOrderNumber(), order.getCreatedAt());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        List<Order> reminderOrders = orderRepository.findByPaymentStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, reminderThreshold);

        for (Order order : reminderOrders) {
            LocalDateTime effectiveCreatedAt = effectiveCreatedAt(order);
            if (awaitingPayment(order)
                    && order.getPaymentReminderSentAt() == null
                    && effectiveCreatedAt.isAfter(expirationThreshold)
                    && !effectiveCreatedAt.isAfter(reminderThreshold)) {
                log.info("Payment reminder needed for order {} (created {}, customer {})",
                        order.getOrderNumber(), effectiveCreatedAt, order.getCustomerEmail());
                if (emailService.sendPaymentReminder(order)) {
                    order.setPaymentReminderSentAt(now);
                    orderRepository.save(order);
                }
            }
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void remindMissingPhotos() {
        LocalDateTime now = LocalDateTime.now();
        for (Order order : orderRepository.findByPaymentStatusAndCreatedAtBefore(PaymentStatus.PAID, now.minusHours(24))) {
            if (order.getStatus() != OrderStatus.PENDING || order.getUploadReminderSentAt() != null
                    || order.getPaidAt() == null || order.getPaidAt().isAfter(now.minusHours(24))
                    || Fulfillment.photosComplete(order, new com.fasterxml.jackson.databind.ObjectMapper())) continue;
            if (emailService.sendUploadReminder(order)) {
                order.setUploadReminderSentAt(now);
                orderRepository.save(order);
            }
        }
    }

    static boolean awaitingPayment(Order order) {
        return order.getPaymentStatus() == PaymentStatus.PENDING
                && (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.DRAFT)
                && (order.getPaymentProofUrl() == null || order.getPaymentProofUrl().isBlank()
                    || "REJECTED".equals(order.getPaymentReviewStatus()));
    }

    private LocalDateTime effectiveCreatedAt(Order order) {
        return order.getReopenedAt() != null && order.getReopenedAt().isAfter(order.getCreatedAt())
                ? order.getReopenedAt()
                : order.getCreatedAt();
    }

    private void sendPaymentReminder(Order order) {
        emailService.sendPaymentReminder(order);
    }
}
