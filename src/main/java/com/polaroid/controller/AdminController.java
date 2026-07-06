package com.polaroid.controller;

import com.polaroid.dto.request.OrderStatusUpdateRequest;
import com.polaroid.dto.request.PrintSizeRequest;
import com.polaroid.dto.request.UpdatePaymentStatusRequest;
import com.polaroid.dto.request.UserRoleUpdateRequest;
import com.polaroid.dto.request.VerifyPaymentRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.dto.response.PrintSizeResponse;
import com.polaroid.dto.response.StatsOverviewResponse;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.Role;
import com.polaroid.service.OrderService;
import com.polaroid.service.PrintSizeService;
import com.polaroid.service.StatsService;
import com.polaroid.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final OrderService orderService;
    private final UserService userService;
    private final StatsService statsService;
    private final PrintSizeService printSizeService;
    
    @GetMapping("/stats/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<StatsOverviewResponse> getStatsOverview(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String customerState,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String orderReference,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) String customerPhone) {
        return ResponseEntity.ok(statsService.getOverview(
                status, paymentStatus, customerState, fromDate, toDate, orderReference, customerEmail, customerPhone));
    }
    
    @GetMapping("/stats/orders-by-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<Map<String, Long>> getOrdersByStatus() {
        return ResponseEntity.ok(statsService.getOrdersByStatusMap());
    }
    
    @GetMapping("/stats/by-state")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Object[]>> getOrdersByState() {
        return ResponseEntity.ok(statsService.getOrdersByState());
    }
    
    @GetMapping("/stats/top-sizes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<List<Object[]>> getTopSellingSizes() {
        return ResponseEntity.ok(statsService.getTopSellingSizes());
    }
    
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String customerState,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String orderReference,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("asc") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return ResponseEntity.ok(orderService.getOrdersWithFilters(
                status, paymentStatus, customerState, fromDate, toDate, orderReference, customerEmail, customerPhone, pageable));
    }
    
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderByNumber(id.toString()));
    }
    
    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            Authentication authentication) {
        
        return ResponseEntity.ok(orderService.updateOrderStatus(
                id, request.getStatus(), request.getMessage(), authentication.getName()));
    }
    
    @PatchMapping("/orders/{id}/tracking")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<OrderResponse> updateTrackingNumber(
            @PathVariable UUID id,
            @RequestParam String trackingNumber) {
        return ResponseEntity.ok(orderService.updateTrackingNumber(id, trackingNumber));
    }
    
    @PostMapping("/orders/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public ResponseEntity<OrderResponse> addNotes(
            @PathVariable UUID id,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(orderService.addNotes(id, notes));
    }

    @PatchMapping("/orders/{id}/payment-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<OrderResponse> updatePaymentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return ResponseEntity.ok(orderService.updatePaymentStatusForAdmin(id, request.getPaymentStatus()));
    }

    @PostMapping("/orders/{orderNumber}/verify-payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<OrderResponse> verifyPayment(
            @PathVariable String orderNumber,
            @Valid @RequestBody VerifyPaymentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(orderService.verifyPayment(orderNumber, request, authentication.getName()));
    }
    
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(userService.searchUsers(search, pageable));
        } else if (role != null) {
            return ResponseEntity.ok(userService.getUsersByRole(role, pageable));
        }
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }
    
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
    
    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserRoleUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserRole(id, request));
    }
    
    @GetMapping("/settings/print-sizes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PrintSizeResponse>> getPrintSizes() {
        return ResponseEntity.ok(printSizeService.getAllPrintSizes());
    }
    
    @PostMapping("/settings/print-sizes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PrintSizeResponse> createPrintSize(@Valid @RequestBody PrintSizeRequest request) {
        return ResponseEntity.ok(printSizeService.createPrintSize(request));
    }
    
    @PatchMapping("/settings/print-sizes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PrintSizeResponse> updatePrintSize(
            @PathVariable String id,
            @Valid @RequestBody PrintSizeRequest request) {
        return ResponseEntity.ok(printSizeService.updatePrintSize(id, request));
    }
    
    @DeleteMapping("/settings/print-sizes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePrintSize(@PathVariable String id) {
        printSizeService.deletePrintSize(id);
        return ResponseEntity.noContent().build();
    }
}
