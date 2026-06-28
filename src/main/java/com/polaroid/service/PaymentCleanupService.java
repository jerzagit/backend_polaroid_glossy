package com.polaroid.service;

import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCleanupService {

    private final OrderRepository orderRepository;
    private final FileService fileService;
    private final OrderService orderService;

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
            try {
                deleteOrderFiles(order);
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setCancelReason("Auto-cancelled: payment not received within " + expirationHours + " hours");
                orderRepository.save(order);
                log.info("Auto-cancelled unpaid order {} (created {})", order.getOrderNumber(), order.getCreatedAt());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        List<Order> reminderOrders = orderRepository.findByPaymentStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, reminderThreshold);

        for (Order order : reminderOrders) {
            if (order.getCreatedAt().isAfter(expirationThreshold)) {
                log.info("Payment reminder needed for order {} (created {}, customer {})",
                        order.getOrderNumber(), order.getCreatedAt(), order.getCustomerEmail());
                sendPaymentReminder(order);
            }
        }
    }

    private void deleteOrderFiles(Order order) {
        List<String> keys = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                try {
                    List<String> itemKeys = parseJsonList(item.getS3Keys());
                    keys.addAll(itemKeys);
                } catch (Exception e) {
                    log.warn("Failed to parse s3Keys for item {}: {}", item.getId(), e.getMessage());
                }
            }
        }

        for (String key : keys) {
            try {
                fileService.deleteFile(key);
            } catch (IOException e) {
                log.warn("Failed to delete file {} for cancelled order {}: {}", key, order.getOrderNumber(), e.getMessage());
            }
        }
    }

    private void sendPaymentReminder(Order order) {
        log.warn("EMAIL NOT CONFIGURED - Would send payment reminder for order {} to {}",
                order.getOrderNumber(), order.getCustomerEmail());
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) return List.of(json);
        String inner = trimmed.substring(1, trimmed.length() - 1);
        if (inner.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : inner.split(",")) {
            String cleaned = part.trim().replaceAll("^\"|\"$", "");
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }
}
