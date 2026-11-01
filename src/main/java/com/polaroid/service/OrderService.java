package com.polaroid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.OrderRequest;
import com.polaroid.dto.request.UpdateOrderRequest;
import com.polaroid.dto.request.VerifyPaymentRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ForbiddenException;
import com.polaroid.exception.PriceConflictException;
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
    private final CatalogPricingService catalogPricingService;

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
        
        if ("later".equals(request.getUploadMode()) && user == null) {
            throw new ForbiddenException("Sign in to upload photos later");
        }
        if (user != null && request.getCheckoutKey() != null) {
            userRepository.lockById(user.getId()).orElseThrow();
            var existing = orderRepository.findByUserIdAndCheckoutKey(user.getId(), request.getCheckoutKey());
            if (existing.isPresent()) {
                if (request.getItems().stream().anyMatch(i -> i.getStripDesign() != null))
                    throw new BadRequestException("Checkout key is already used by another order request");
                return orderMapper.toDto(existing.get());
            }
        }
        User affiliate = null;
        if (request.getAffiliateCode() != null && !request.getAffiliateCode().isEmpty()) {
            affiliate = userRepository.findByAffiliateCode(request.getAffiliateCode()).orElse(null);
        }
        
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = buildOrderItem(itemReq);
            if (StripDesigns.isStrip(item.getSizeId()) || itemReq.getStripDesign() != null) {
                if (itemReq.getProductId() == null || itemReq.getPackageId() == null || itemReq.getVariantId() == null)
                    throw new BadRequestException("Strip designs require a catalog product, variant and package");
                if (request.getCheckoutKey() == null || !request.getCheckoutKey().matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
                    throw new BadRequestException("Strip checkout requires a UUID Idempotency-Key or checkoutKey");
                if ((itemReq.getImageUrls() != null && !itemReq.getImageUrls().isEmpty())
                        || (itemReq.getCustomTexts() != null && !itemReq.getCustomTexts().isEmpty()))
                    throw new BadRequestException("Strip sources must use reserved assets, not image URLs or custom texts");
                var state = StripDesigns.reserve(item.getSizeId(), item.getQuantity(), itemReq.getStripDesign());
                StripDesigns.write(item, state, objectMapper);
                item.setExpectedImageCount(state.getAssets().size());
                item.setImages("[]"); item.setS3Keys("[]");
            }
            subtotal = subtotal.add(item.getTotalPrice());
            orderItems.add(item);
        }
        
        BigDecimal shipping = shippingCost(request.getCustomerState());
        BigDecimal total = subtotal.add(shipping);

        if (request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(total) != 0) {
            throw new PriceConflictException(
                    PriceConflictException.CODE_PRICE_CHANGED,
                    "Pricing has changed since you reviewed your order. Please confirm the updated total.",
                    total);
        }

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
                .paymentMethod(request.getPaymentMethod() == null ? "toyyibpay" : request.getPaymentMethod())
                .uploadMode(request.getUploadMode() == null ? "now" : request.getUploadMode())
                .checkoutKey(user != null ? request.getCheckoutKey() : null)
                .subtotal(subtotal)
                .shipping(shipping)
                .total(total)
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
            if (isStaff(user.getRole())
                    || (order.getUserId() != null && order.getUserId().equals(user.getId()))
                    || requesterEmail.equalsIgnoreCase(order.getCustomerEmail())) {
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
            if (!PaymentCleanupService.awaitingPayment(order)) continue;
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
        
        orderRepository.lockById(order.getId()).orElseThrow();
        if (java.util.Set.of(OrderStatus.PROCESSING, OrderStatus.POSTED, OrderStatus.ON_DELIVERY, OrderStatus.DELIVERED).contains(newStatus)
                && !Fulfillment.ready(order, objectMapper)) {
            throw new BadRequestException("Payment and all required photos must be complete before processing");
        }
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
        
        orderRepository.lockById(order.getId()).orElseThrow();
        boolean alreadyPaid = order.getPaymentStatus() == PaymentStatus.PAID;
        if (order.getPaymentStatus() == PaymentStatus.PAID && status != PaymentStatus.REFUNDED) {
            status = PaymentStatus.PAID;
        }
        order.setPaymentStatus(status);
        Fulfillment.reconcile(order, objectMapper);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            if (order.getStatus() == OrderStatus.DRAFT) {
                order.setStatus(OrderStatus.PENDING);
                order.setExpiresAt(null);
            }
            addStatusHistory(order, order.getStatus(), "Payment received");
            if (!alreadyPaid) {
                emailService.sendPaymentConfirmation(order);
                emailService.sendNewOrderAlert(order);
            }
        }
        
        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse updatePaymentStatusForAdmin(UUID orderId, PaymentStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        orderRepository.lockById(order.getId()).orElseThrow();
        boolean alreadyPaid = order.getPaymentStatus() == PaymentStatus.PAID;
        if (order.getPaymentStatus() == PaymentStatus.PAID && status != PaymentStatus.REFUNDED) {
            status = PaymentStatus.PAID;
        }
        order.setPaymentStatus(status);
        Fulfillment.reconcile(order, objectMapper);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
            addStatusHistory(order, order.getStatus(), "Payment confirmed by admin");
            if (!alreadyPaid) {
                emailService.sendPaymentConfirmation(order);
                emailService.sendNewOrderAlert(order);
            }
        }
        if (status == PaymentStatus.FAILED) {
            addStatusHistory(order, order.getStatus(), "Payment rejected by admin");
        }

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse updatePaymentStatusForUser(String orderNumber, PaymentStatus status, String requesterEmail, String message) {
        Order order = getAuthorizedOrder(orderNumber, requesterEmail);

        orderRepository.lockById(order.getId()).orElseThrow();
        if (order.getPaymentStatus() == PaymentStatus.PAID && status != PaymentStatus.REFUNDED) {
            status = PaymentStatus.PAID;
        }
        order.setPaymentStatus(status);
        Fulfillment.reconcile(order, objectMapper);
        if (status == PaymentStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        }
        addStatusHistory(order, order.getStatus(), message);

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse verifyPayment(String orderNumber, VerifyPaymentRequest request, String adminEmail) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        orderRepository.lockById(order.getId()).orElseThrow();
        String action = request.getAction().toUpperCase();

        switch (action) {
            case "APPROVE" -> {
                boolean approvedFromUnpaid = order.getPaymentStatus() != PaymentStatus.PAID;
                order.setPaymentStatus(PaymentStatus.PAID);
                Fulfillment.reconcile(order, objectMapper);
                order.setPaidAt(LocalDateTime.now());
                addStatusHistory(order, order.getStatus(), "Payment verified and approved by " + adminEmail);
                if (approvedFromUnpaid) {
                    emailService.sendPaymentConfirmation(order);
                    emailService.sendNewOrderAlert(order);
                }
            }
            case "REJECT" -> {
                order.setPaymentProofUrl(null);
                order.setPaymentReference(null);
                String reason = request.getReason() != null ? request.getReason() : "Payment proof rejected";
                addStatusHistory(order, order.getStatus(), "Payment rejected: " + reason + " (by " + adminEmail + ")");
            }
            default -> throw new BadRequestException("Invalid action: " + action + ". Use APPROVE or REJECT.");
        }

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
        return expectedImageCount(itemReq, itemReq.getQuantity());
    }

    private int expectedImageCount(OrderRequest.OrderItemRequest itemReq, Integer effectiveQuantity) {
        int quantity = effectiveQuantity != null ? effectiveQuantity : 0;
        int expected;
        if (itemReq.getExpectedImageCount() != null) {
            if (itemReq.getExpectedImageCount() < 0) {
                throw new BadRequestException("expectedImageCount cannot be negative");
            }
            expected = itemReq.getExpectedImageCount();
        } else if (itemReq.getImageUrls() != null && !itemReq.getImageUrls().isEmpty()) {
            expected = itemReq.getImageUrls().size() * quantity;
        } else if (itemReq.getCustomTexts() != null && !itemReq.getCustomTexts().isEmpty()) {
            expected = itemReq.getCustomTexts().size() * quantity;
        } else {
            expected = quantity;
        }

        if (itemReq.getImageUrls() != null
                && !itemReq.getImageUrls().isEmpty()
                && itemReq.getImageUrls().size() > expected) {
            throw new BadRequestException("imageUrls cannot exceed expectedImageCount");
        }
        if (itemReq.getCustomTexts() != null
                && !itemReq.getCustomTexts().isEmpty()
                && itemReq.getCustomTexts().size() > expected) {
            throw new BadRequestException("customTexts cannot exceed expectedImageCount");
        }

        return expected;
    }

    /**
     * Builds an order item from the customer's selection. Package selections
     * (productId + variantId + packageId) are validated against the database
     * and priced with whole-package totals; legacy sizeId selections keep the
     * existing per-print pricing until the frontend is fully migrated.
     */
    private OrderItem buildOrderItem(OrderRequest.OrderItemRequest itemReq) {
        if (itemReq.getPackageId() != null || itemReq.getVariantId() != null) {
            return buildPackageOrderItem(itemReq);
        }
        if (itemReq.getSizeId() == null || itemReq.getSizeId().isBlank()) {
            throw new BadRequestException("Each item requires either a package selection (packageId) or a sizeId");
        }
        if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be a positive number of prints");
        }

        String sizeId = normalizeSizeId(itemReq.getSizeId());
        PrintSize printSize = printSizeRepository.findById(sizeId)
                .orElseThrow(() -> new ResourceNotFoundException("Print size not found: " + sizeId));

        BigDecimal itemTotal = printSize.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
        return OrderItem.builder()
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
    }

    private OrderItem buildPackageOrderItem(OrderRequest.OrderItemRequest itemReq) {
        CatalogPricingService.ResolvedPackage resolved = catalogPricingService.resolveActivePackage(
                itemReq.getProductId(), itemReq.getVariantId(), itemReq.getPackageId());

        int packageCount = itemReq.getPackages() != null ? itemReq.getPackages() : 1;
        if (packageCount < 1) {
            throw new BadRequestException("packages must be at least 1");
        }
        int totalPrints;
        try { totalPrints = Math.multiplyExact(resolved.pkg().getQuantity(), packageCount); }
        catch (ArithmeticException e) { throw new BadRequestException("Package quantity is too large"); }

        return OrderItem.builder()
                .sizeId(resolved.product().getCode())
                .sizeName(resolved.product().getDisplayName())
                .variantId(String.valueOf(resolved.variant().getId()))
                .variantLabel(resolved.variant().getLabel())
                .style(resolved.variant().getStyle().name())
                .packageId(String.valueOf(resolved.pkg().getId()))
                .packageQuantity(resolved.pkg().getQuantity())
                .packagePrice(resolved.pkg().getPrice())
                .packageCount(packageCount)
                .quantity(totalPrints)
                .expectedImageCount(expectedImageCount(itemReq, totalPrints))
                .unitPrice(resolved.pkg().getPrice())
                .totalPrice(resolved.totalPrice(packageCount))
                .images(formatJsonArray(itemReq.getImageUrls()))
                .s3Keys(formatJsonArray(itemReq.getImageUrls()))
                .customTexts(formatJsonArray(itemReq.getCustomTexts()))
                .build();
    }

    /**
     * Re-reads current catalog prices into the order snapshot after the
     * customer has been shown a PRICE_CHANGED conflict. Returns the updated
     * order so the frontend can display the new total for confirmation.
     */
    @Transactional
    public OrderResponse confirmPricing(String orderNumber, String requesterEmail) {
        Order order = getAuthorizedOrder(orderNumber, requesterEmail);
        orderRepository.lockById(order.getId()).orElseThrow();

        BigDecimal subtotal = catalogPricingService.resyncOrderItemPrices(order);
        order.setSubtotal(subtotal);
        order.setShipping(shippingCost(order.getCustomerState()));
        order.setTotal(subtotal.add(order.getShipping()));

        addStatusHistory(order, order.getStatus(), "Customer reconfirmed catalog pricing before payment");
        return orderMapper.toDto(orderRepository.save(order));
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
                newItems.add(buildOrderItem(itemReq));
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

        if (isStaff(user.getRole())
                || (order.getUserId() != null && order.getUserId().equals(user.getId()))
                || requesterEmail.equalsIgnoreCase(order.getCustomerEmail())) {
            return order;
        }

        throw new ForbiddenException("Not authorized to access this order");
    }

    private boolean isStaff(Role role) {
        return role == Role.ADMIN || role == Role.MARKETING || role == Role.PACKER;
    }

    private String normalizeSizeId(String sizeId) {
        return sizeId == null ? null : sizeId.trim().toLowerCase();
    }
}
