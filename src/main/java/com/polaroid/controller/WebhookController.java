package com.polaroid.controller;

import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    
    private final OrderService orderService;
    
    @PostMapping("/toyyibpay")
    public ResponseEntity<Map<String, String>> handleToyyibpayCallback(
            @RequestParam(required = false) String refno,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amount) {
        
        log.info("ToyyibPay callback received - refno: {}, status: {}, amount: {}", refno, status, amount);
        
        if (refno == null || refno.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing refno"));
        }
        
        try {
            if ("1".equals(status) || "success".equalsIgnoreCase(status)) {
                orderService.updatePaymentStatus(refno, PaymentStatus.PAID);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Payment processed"));
            } else if ("3".equals(status) || "pending".equalsIgnoreCase(status)) {
                orderService.updatePaymentStatus(refno, PaymentStatus.PENDING);
                return ResponseEntity.ok(Map.of("status", "pending", "message", "Payment pending"));
            } else if ("2".equals(status) || "failed".equalsIgnoreCase(status)) {
                orderService.updatePaymentStatus(refno, PaymentStatus.FAILED);
                return ResponseEntity.ok(Map.of("status", "failed", "message", "Payment failed"));
            }
            
            return ResponseEntity.ok(Map.of("status", "unknown", "message", "Unknown status"));
        } catch (Exception e) {
            log.error("Error processing ToyyibPay callback: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
