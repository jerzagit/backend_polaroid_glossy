package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.model.Order;
import java.time.LocalDateTime;
import java.util.*;

/** Append-only snapshots preserve receipts and decisions when customers replace proof. */
public final class PaymentReviews {
    private PaymentReviews() {}
    public static List<Map<String, String>> read(Order order, ObjectMapper mapper) {
        try {
            return order.getPaymentReviewHistory() == null ? new ArrayList<>()
                    : mapper.readValue(order.getPaymentReviewHistory(), new TypeReference<>() {});
        } catch (Exception e) { throw new IllegalStateException("Cannot read payment review history", e); }
    }
    public static void append(Order order, ObjectMapper mapper, String action, String actor, String notes) {
        var history = read(order, mapper);
        var event = new LinkedHashMap<String, String>();
        event.put("action", action);
        event.put("actor", actor);
        event.put("at", LocalDateTime.now().toString());
        event.put("proofKey", order.getPaymentProofUrl());
        event.put("source", order.getPaymentProofSource());
        event.put("reference", order.getPaymentReference());
        event.put("notes", notes);
        event.put("amount", order.getPaymentAmountReceived() == null ? null : order.getPaymentAmountReceived().toPlainString());
        history.add(event);
        try { order.setPaymentReviewHistory(mapper.writeValueAsString(history)); }
        catch (Exception e) { throw new IllegalStateException("Cannot save payment review history", e); }
    }
}
