package com.polaroid.controller;

import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.service.OrderService;
import com.polaroid.service.PaymentService;
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
    private final PaymentService paymentService;
    
    @PostMapping("/toyyibpay")
    public ResponseEntity<Map<String, String>> handleToyyibpayCallback(
            @RequestParam(required = false) String refno,
            @RequestParam(required = false) String order_id,
            @RequestParam(required = false) String billcode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String hash) {
        
        try {
            String orderNumber = paymentService.resolveOrderNumber(order_id, billcode, refno);
            log.info("ToyyibPay callback received - order: {}, billcode: {}, status: {}, amount: {}", orderNumber, billcode, status, amount);
            PaymentStatus paymentStatus = paymentService.verifyCallback(orderNumber, refno, billcode, status, amount, hash);
            orderService.updatePaymentStatus(orderNumber, paymentStatus);
            return ResponseEntity.ok(Map.of("status", paymentStatus.name().toLowerCase(), "message", "Payment callback processed"));
        } catch (SecurityException e) {
            log.warn("Rejected ToyyibPay callback: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Invalid payment callback"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing ToyyibPay callback: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
