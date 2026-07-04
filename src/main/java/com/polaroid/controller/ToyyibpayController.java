package com.polaroid.controller;

import com.polaroid.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/toyyibpay")
@RequiredArgsConstructor
public class ToyyibpayController {

    private final PaymentService paymentService;

    @PostMapping("/create-bill")
    public ResponseEntity<?> createBill(@RequestBody Map<String, String> request) {
        String orderNumber = request.get("orderNumber");
        String customerEmail = request.get("customerEmail");

        if (orderNumber == null || orderNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "orderNumber is required"));
        }

        try {
            Map<String, String> payment = paymentService.createPaymentForOrder(orderNumber, customerEmail);
            payment.put("success", "true");
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
