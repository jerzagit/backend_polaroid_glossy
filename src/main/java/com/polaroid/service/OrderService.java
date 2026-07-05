package com.polaroid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.OrderRequest;
import com.polaroid.dto.request.UpdateOrderRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ForbiddenException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.*;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.OrderSpecifications;
import com.polaroid.repository.OrderItemRepository;
import com.polaroid.repository.OrderStatusHistoryRepository;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.UserRepository;
import com.polaroid.dto.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final PrintSizeRepository printSizeRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.upload.token-expiration-hours:168}")
    private int uploadTokenExpirationHours;

    @Value("${app.draft.expiration-hours:24}")
    private int draftExpirationHours;
    
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String userEmail) {
        User user = null;
        if (userEmail != null) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }
        
        User affiliate = null;
        if (request.getAffiliateCode() != null && !request.getAffiliateCode().isEmpty()) {
            affiliate = userRepository.findByAffiliateCode(request.getAffiliateCode()).orElse(null);
        }
        
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            PrintSize printSize = printSizeRepository.findById(itemReq.getSizeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + itemReq.getSizeId()));
            
            BigDecimal itemTotal = printSize.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemTotal);
            
            OrderItem item = OrderItem.builder()
                    .sizeId(printSize.getId())
                    .sizeName(printSize.getDisplayName())
                    .quantity(itemReq.getQuantity())
                    .expectedImageCount(expectedImageCount(itemReq))
                    .unitPrice(printSize.getPrice())
                    .totalPrice(itemTotal)
                    .images(formatJsonArray(itemReq.getImageUrls()))
                    .s3Keys(formatJsonArray(itemReq.getImageUrls()))
                    .customTexts(formatJsonArray(itemReq.getCustomTexts()))
                    .build();
            
            orderItems.add(item);
        }
        
        BigDecimal shipping = shippingCost(request.getCustomerState());

        String uploadToken = user == null ? generateUploadToken() : null;

        boolean isGuest = user == null;
        OrderStatus initialStatus = isGuest ? OrderStatus.DRAFT : OrderStatus.PENDING;

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(user != null ? user.getId() : null)
                .affiliateId(affiliate != null ? affiliate.getId() : null)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(normalizeMalaysiaPhone(request.getCustomerPhone()))
                .customerHouseUnitNo(request.getCustomerHouseUnitNo())
                .customerAddressLine1(request.getCustomerAddressLine1())
                .customerAddressLine2(request.getCustomerAddressLine2())
                .customerPostcode(request.getCustomerPostcode())
                .customerCity(request.getCustomerCity())
                .customerState(request.getCustomerState())
                .customerCountry("Malaysia")
                .notes(request.getNotes())
                .status(initialStatus)
                .paymentStatus(PaymentStatus.PENDING)
                .subtotal(subtotal)
                .shipping(shipping)
                .total(subtotal.add(shipping))
                .expiresAt(isGuest ? LocalDateTime.now().plusHours(draftExpirationHours) : null)
                .uploadTokenHash(uploadToken != null ? hashUploadToken(uploadToken) : null)
                .uploadTokenExpiresAt(uploadToken != null ? LocalDateTime.now().plusHours(uploadTokenExpirationHours) : null)
                .items(orderItems)
                .build();
        
        orderItems.forEach(item -> item.setOrder(order));
        
        Order savedOrder = orderRepository.save(order);
        
        addStatusHistory(savedOrder, initialStatus, isGuest ? "Draft order created" : "Order created");

        emailService.sendOrderConfirmation(savedOrder);
        
        OrderResponse response = orderMapper.toDto(savedOrder);
        response.setUploadToken(uploadToken);
        return response;
    }
    
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return orderMapper.toDto(order);
    }
    
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber, String requesterEmail, String verificationEmail) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (requesterEmail != null) {
            User user = userRepository.findByEmail(requesterEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (isStaff(user.getRole()) || (order.getUserId() != null && order.getUserId().equals(user.getId()))) {
                return orderMapper.toDto(order);
            }
        }

        if (verificationEmail != null && verificationEmail.equalsIgnoreCase(order.getCustomerEmail())) {
            return orderMapper.toDto(order);
        }

        throw new ForbiddenException("Order email verification is required");
    }
    
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(orderMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String userEmail, Pageable pageable, OrderStatus status) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (status != null) {
            return orderRepository.findByUserIdAndStatus(user.getId(), status, pageable)
                    .map(orderMapper::toDto);
        }
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(orderMapper::toDto);
    }

    @Transactional
    public void expireDraftOrders() {
        List<Order> expiredDrafts = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.DRAFT, LocalDateTime.now());
        for (Order order : expiredDrafts) {
            order.setStatus(OrderStatus.EXPIRED);
            order.setDraftExpiredAt(LocalDateTime.now());
            orderRepository.save(order);
            addStatusHistory(order, OrderStatus.EXPIRED, "Draft expired without payment");
        }
        if (!expiredDrafts.isEmpty()) {
            log.info("Expired {} draft orders", expiredDrafts.size());
        }
    }
    
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersWithFilters(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String customerState,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String orderReference,
            String customerEmail,
            String customerPhone,
            Pageable pageable) {
        if (fromDate == null) {
            fromDate = LocalDateTime.of(2000, 1, 1, 0, 0);
        }
        if (toDate == null) {
            toDate = LocalDateTime.of(2099, 12, 31, 23, 59);
        }
        return orderRepository.findAll(OrderSpecifications.withFilters(
                        status,
                        paymentStatus,
                        customerState,
                        fromDate,
                        toDate,
                        orderReference,
                        customerEmail,
                        customerPhone),
                pageable)
                .map(orderMapper::toDto);
    }
    
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, String message, String userEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        
        switch (newStatus) {
            case PROCESSING -> order.setStatus(OrderStatus.PROCESSING);
            case POSTED -> {
                order.setShippedAt(LocalDateTime.now());
                addStatusHistory(order, OrderStatus.POSTED, message != null ? message : "Order posted");
            }
            case ON_DELIVERY -> addStatusHistory(order, OrderStatus.ON_DELIVERY, message != null ? message : "Out for delivery");
            case DELIVERED -> {
                order.setDeliveredAt(LocalDateTime.now());
                addStatusHistory(order, OrderStatus.DELIVERED, message != null ? message : "Order delivered");
            }
            case CANCELLED -> {
                order.setCancelledAt(LocalDateTime.now());
                order.setCancelReason(message);
                addStatusHistory(order, OrderStatus.CANCELLED, message != null ? message : "Order cancelled");
            }
            default -> addStatusHistory(order, newStatus, message);
        }
        
        order = orderRepository.save(order);
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public OrderResponse updateTrackingNumber(UUID orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setTrackingNumber(trackingNumber);
        order = orderRepository.save(order);
        
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public OrderResponse addNotes(UUID orderId, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setNotes(notes);
        order = orderRepository.save(order);
        
        return orderMapper.toDto(order);
    }
    
    @Transactional
    public void updatePaymentStatus(String orderNumber, PaymentStatus status) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        order.setPaymentStatus(status);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            if (order.getStatus() == OrderStatus.DRAFT) {
                order.setStatus(OrderStatus.PENDING);
                order.setExpiresAt(null);
            }
            addStatusHistory(order, order.getStatus(), "Payment received");
            emailService.sendPaymentConfirmation(order);
        }
        
        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse updatePaymentStatusForAdmin(UUID orderId, PaymentStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setPaymentStatus(status);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            addStatusHistory(order, order.getStatus(), "Payment confirmed by admin");
        }
        if (status == PaymentStatus.FAILED) {
            addStatusHistory(order, order.getStatus(), "Payment rejected by admin");
        }

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updatePaymentStatusForUser(String orderNumber, PaymentStatus status, String requesterEmail, String message) {
        Order order = getAuthorizedOrder(orderNumber, requesterEmail);

        order.setPaymentStatus(status);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        }
        addStatusHistory(order, order.getStatus(), message);

        return orderMapper.toDto(orderRepository.save(order));
    }
    
    private void addStatusHistory(Order order, OrderStatus status, String message) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .message(message)
                .build();
        statusHistoryRepository.save(history);
        if (order.getStatusHistory() != null) {
            order.getStatusHistory().add(history);
        }
    }
    
    private String generateOrderNumber() {
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(5);
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "PG" + timestamp + random;
    }

    private String generateUploadToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashUploadToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash upload token", e);
        }
    }
    
    private String formatJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid image or custom text metadata");
        }
    }

    private int expectedImageCount(OrderRequest.OrderItemRequest itemReq) {
        int expected;
        if (itemReq.getExpectedImageCount() != null) {
            if (itemReq.getExpectedImageCount() < 0) {
                throw new BadRequestException("expectedImageCount cannot be negative");
            }
            expected = itemReq.getExpectedImageCount();
        } else if (itemReq.getImageUrls() != null && !itemReq.getImageUrls().isEmpty()) {
            expected = itemReq.getImageUrls().size();
        } else if (itemReq.getCustomTexts() != null && !itemReq.getCustomTexts().isEmpty()) {
            expected = itemReq.getCustomTexts().size();
        } else {
            expected = itemReq.getQuantity() != null ? itemReq.getQuantity() : 0;
        }

        if (itemReq.getImageUrls() != null
                && !itemReq.getImageUrls().isEmpty()
                && itemReq.getImageUrls().size() > expected) {
            throw new BadRequestException("imageUrls cannot exceed expectedImageCount");
        }
        if (itemReq.getCustomTexts() != null
                && !itemReq.getCustomTexts().isEmpty()
                && itemReq.getCustomTexts().size() != expected) {
            throw new BadRequestException("customTexts must contain one entry per expected image");
        }

        return expected;
    }

    private BigDecimal shippingCost(String state) {
        if (state == null || state.isBlank()) {
            return new BigDecimal("7.00");
        }

        String normalizedState = state.trim().toLowerCase();
        if (normalizedState.equals("sabah")
                || normalizedState.equals("sarawak")
                || normalizedState.equals("labuan")
                || normalizedState.equals("e_sabah")
                || normalizedState.equals("e_sarawak")) {
            return new BigDecimal("11.00");
        }

        return new BigDecimal("7.00");
    }

    private String normalizeMalaysiaPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("60")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("6")) {
            digits = digits.substring(1);
        }

        return digits.isBlank() ? null : digits;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(UUID userId) {
        return orderRepository.findByUserId(userId, Pageable.unpaged()).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Transactional
    public OrderResponse updateOrder(String orderNumber, UpdateOrderRequest request, String userEmail) {
        Order order = getAuthorizedOrder(orderNumber, userEmail);

        if (request.getCustomerName() != null) order.setCustomerName(request.getCustomerName());
        if (request.getCustomerEmail() != null) order.setCustomerEmail(request.getCustomerEmail());
        if (request.getCustomerPhone() != null) order.setCustomerPhone(normalizeMalaysiaPhone(request.getCustomerPhone()));
        if (request.getCustomerHouseUnitNo() != null) order.setCustomerHouseUnitNo(request.getCustomerHouseUnitNo());
        if (request.getCustomerAddressLine1() != null) order.setCustomerAddressLine1(request.getCustomerAddressLine1());
        if (request.getCustomerAddressLine2() != null) order.setCustomerAddressLine2(request.getCustomerAddressLine2());
        if (request.getCustomerPostcode() != null) order.setCustomerPostcode(request.getCustomerPostcode());
        if (request.getCustomerCity() != null) order.setCustomerCity(request.getCustomerCity());
        if (request.getCustomerState() != null) order.setCustomerState(request.getCustomerState());
        if (request.getNotes() != null) order.setNotes(request.getNotes());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            order.getItems().clear();
            List<OrderItem> newItems = new ArrayList<>();
            for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
                PrintSize printSize = printSizeRepository.findById(itemReq.getSizeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + itemReq.getSizeId()));
                BigDecimal itemTotal = printSize.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                OrderItem item = OrderItem.builder()
                        .sizeId(printSize.getId())
                        .sizeName(printSize.getDisplayName())
                        .quantity(itemReq.getQuantity())
                        .expectedImageCount(expectedImageCount(itemReq))
                        .unitPrice(printSize.getPrice())
                        .totalPrice(itemTotal)
                        .images(formatJsonArray(itemReq.getImageUrls()))
                        .s3Keys(formatJsonArray(itemReq.getImageUrls()))
                        .customTexts(formatJsonArray(itemReq.getCustomTexts()))
                        .build();
                newItems.add(item);
            }
            newItems.forEach(item -> item.setOrder(order));
            order.setItems(newItems);
            BigDecimal subtotal = newItems.stream()
                    .map(OrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotal(subtotal);
            order.setShipping(shippingCost(order.getCustomerState()));
            order.setTotal(subtotal.add(order.getShipping()));
        }

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumberOrId, String userEmail) {
        Order order = null;
        try {
            UUID uuid = UUID.fromString(orderNumberOrId);
            order = orderRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {}
        if (order == null) {
            order = orderRepository.findByOrderNumber(orderNumberOrId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumberOrId));
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isStaff(user.getRole()) && (order.getUserId() == null || !order.getUserId().equals(user.getId()))) {
            throw new ForbiddenException("Not authorized to cancel this order");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason("Cancelled by customer");
        addStatusHistory(order, OrderStatus.CANCELLED, "Order cancelled by customer");

        return orderMapper.toDto(orderRepository.save(order));
    }

    public Order getAuthorizedOrder(String orderNumber, String requesterEmail) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        User user = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (isStaff(user.getRole()) || (order.getUserId() != null && order.getUserId().equals(user.getId()))) {
            return order;
        }

        throw new ForbiddenException("Not authorized to access this order");
    }

    private boolean isStaff(Role role) {
        return role == Role.ADMIN || role == Role.MARKETING || role == Role.PACKER;
    }
}
