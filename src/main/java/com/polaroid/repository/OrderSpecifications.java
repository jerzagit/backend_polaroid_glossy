package com.polaroid.repository;

import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public final class OrderSpecifications {
    private OrderSpecifications() {
    }

    public static Specification<Order> withFilters(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String customerState,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String orderReference,
            String customerEmail,
            String customerPhone) {
        return Specification
                .where(hasStatus(status))
                .and(hasPaymentStatus(paymentStatus))
                .and(hasCustomerState(customerState))
                .and(createdFrom(fromDate))
                .and(createdTo(toDate))
                .and(matchesOrderReference(orderReference))
                .and(matchesCustomerEmail(customerEmail))
                .and(matchesCustomerPhone(customerPhone));
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasPaymentStatus(PaymentStatus paymentStatus) {
        return (root, query, cb) -> paymentStatus == null ? null : cb.equal(root.get("paymentStatus"), paymentStatus);
    }

    private static Specification<Order> hasCustomerState(String customerState) {
        return (root, query, cb) -> isBlank(customerState) ? null : cb.equal(root.get("customerState"), customerState);
    }

    private static Specification<Order> createdFrom(LocalDateTime fromDate) {
        return (root, query, cb) -> fromDate == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate);
    }

    private static Specification<Order> createdTo(LocalDateTime toDate) {
        return (root, query, cb) -> toDate == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), toDate);
    }

    private static Specification<Order> matchesOrderReference(String orderReference) {
        return (root, query, cb) -> {
            if (isBlank(orderReference)) {
                return null;
            }

            String normalized = orderReference.trim();
            var orderNumberLike = cb.like(cb.lower(root.get("orderNumber")), contains(normalized));
            try {
                UUID id = UUID.fromString(normalized);
                return cb.or(orderNumberLike, cb.equal(root.get("id"), id));
            } catch (IllegalArgumentException ignored) {
                return orderNumberLike;
            }
        };
    }

    private static Specification<Order> matchesCustomerEmail(String customerEmail) {
        return (root, query, cb) -> isBlank(customerEmail)
                ? null
                : cb.like(cb.lower(root.get("customerEmail")), contains(customerEmail));
    }

    private static Specification<Order> matchesCustomerPhone(String customerPhone) {
        return (root, query, cb) -> {
            if (isBlank(customerPhone)) {
                return null;
            }

            String digits = normalizePhone(customerPhone);
            return cb.like(root.get("customerPhone"), "%" + digits + "%");
        };
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalizePhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("60")) {
            return digits.substring(2);
        }
        if (digits.startsWith("6")) {
            return digits.substring(1);
        }
        return digits;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
