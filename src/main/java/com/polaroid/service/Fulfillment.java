package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.model.Order;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;

/** Payment and photo readiness are independent; fail closed on malformed metadata. */
public final class Fulfillment {
    private Fulfillment() {}
    public static boolean photosComplete(Order order, ObjectMapper mapper) {
        return order.getItems() != null && !order.getItems().isEmpty() && order.getItems().stream().allMatch(item -> {
            int expected = item.getExpectedImageCount() != null ? item.getExpectedImageCount() : item.getQuantity() == null ? 0 : item.getQuantity();
            try {
                String json = item.getS3Keys() != null ? item.getS3Keys() : item.getImages();
                var images = mapper.readTree(json == null ? "[]" : json);
                return expected > 0 && images.isArray() && images.size() >= expected;
            } catch (Exception e) { return false; }
        });
    }
    public static boolean ready(Order order, ObjectMapper mapper) {
        return order.getPaymentStatus() == PaymentStatus.PAID && photosComplete(order, mapper);
    }
    public static void reconcile(Order order, ObjectMapper mapper) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.DRAFT) return;
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setExpiresAt(null);
            order.setStatus(ready(order, mapper) ? OrderStatus.PROCESSING : OrderStatus.PENDING);
        }
    }
}
