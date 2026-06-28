package com.polaroid.controller;

import com.polaroid.dto.request.OrderRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.service.OrderService;
import com.polaroid.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    private final PaymentService paymentService;

    @Value("${app.mock-payments.enabled:false}")
    private boolean mockPaymentsEnabled;
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(orderService.createOrder(request, userEmail));
    }

    @GetMapping("/payment-return")
    public ResponseEntity<Map<String, String>> getPaymentReturnStatus(
            @RequestParam(required = false, name = "order_id") String orderId,
            @RequestParam(required = false, name = "orderId") String camelOrderId,
            @RequestParam(required = false) String billcode,
            @RequestParam(required = false) String refno,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "status_id") String statusId) {
        String orderReference = orderId != null && !orderId.isBlank() ? orderId : camelOrderId;
        String gatewayStatus = status != null && !status.isBlank() ? status : statusId;
        return ResponseEntity.ok(paymentService.resolvePaymentReturn(orderReference, billcode, refno, gatewayStatus));
    }
    
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(
            @PathVariable String orderNumber,
            @RequestParam(required = false) String email,
            Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(orderService.getOrderByNumber(orderNumber, userEmail, email));
    }
    
    @GetMapping("/my")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return ResponseEntity.ok(orderService.getUserOrders(authentication.getName(), pageable));
    }
    
    @PostMapping("/{orderNumber}/pay")
    public ResponseEntity<Map<String, String>> initiatePayment(
            @PathVariable String orderNumber,
            Authentication authentication) {
        return ResponseEntity.ok(paymentService.createPayment(orderNumber, authentication.getName()));
    }

    @PostMapping("/{orderNumber}/mock-pay")
    public ResponseEntity<OrderResponse> mockPayment(
            @PathVariable String orderNumber,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {
        if (!mockPaymentsEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PaymentStatus status = PaymentStatus.PAID;
        if (request != null && request.get("status") != null) {
            status = PaymentStatus.valueOf(request.get("status").toUpperCase());
        }

        return ResponseEntity.ok(orderService.updatePaymentStatusForUser(
                orderNumber,
                status,
                authentication.getName(),
                "Local mock payment " + status.name().toLowerCase()
        ));
    }
}
