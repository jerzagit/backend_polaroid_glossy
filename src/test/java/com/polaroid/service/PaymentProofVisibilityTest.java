package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.mapper.OrderMapper;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.exception.ForbiddenException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Order;
import com.polaroid.model.User;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.OrderItemRepository;
import com.polaroid.repository.OrderRepository;
import com.polaroid.repository.UserRepository;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Seller-side payment proof visibility: review history, the second reference and a
 * receipt link that survives a dashboard left open.
 */
class PaymentProofVisibilityTest {
    private static final String ADMIN = "admin@example.com";
    private static final String PROOF = "payment-proofs/PG-1/receipt.jpg";

    private final ObjectMapper json = new ObjectMapper();
    private final StorageService storage = mock(StorageService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private OrderMapper mapper;
    private FileService fileService;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        mapper = new OrderMapper(json, storage);
        fileService = new FileService(storage, orders, mock(OrderItemRepository.class), users, json);
        orderId = UUID.randomUUID();
    }

    @Test
    void adminResponseExposesReviewHistoryAndKeepsBothReferences() {
        Order order = order(OrderStatus.PENDING, PaymentStatus.PAID, PROOF);
        order.setPaymentReference("CUSTOMER-RCPT-77");
        order.setPaymentVerifiedReference("MAYBANK-998877");
        PaymentReviews.append(order, json, "SUBMITTED", "customer@example.com", null);
        PaymentReviews.append(order, json, "APPROVED", ADMIN, "funds confirmed");

        OrderResponse response = mapper.toDto(order);

        assertEquals("CUSTOMER-RCPT-77", response.getPaymentReference());
        assertEquals("MAYBANK-998877", response.getPaymentVerifiedReference());
        assertEquals(2, response.getPaymentReviewHistory().size());
        assertEquals("SUBMITTED", response.getPaymentReviewHistory().get(0).get("action"));
        assertEquals("CUSTOMER-RCPT-77", response.getPaymentReviewHistory().get(0).get("reference"));
        assertEquals("APPROVED", response.getPaymentReviewHistory().get(1).get("action"));
        assertEquals("MAYBANK-998877", response.getPaymentReviewHistory().get(1).get("verifiedReference"));
    }

    @Test
    void unreadableReviewHistoryDoesNotBreakTheAdminList() {
        Order order = order(OrderStatus.PENDING, PaymentStatus.PENDING, PROOF);
        order.setPaymentReviewHistory("{not json");

        OrderResponse response = mapper.toDto(order);

        assertEquals(0, response.getPaymentReviewHistory().size());
        assertEquals("PG-1", response.getOrderNumber());
    }

    @Test
    void redirectSignsAFreshLongLivedUrlForAnAdmin() {
        Order order = order(OrderStatus.PENDING, PaymentStatus.PENDING, PROOF);
        when(users.findByEmail(ADMIN)).thenReturn(Optional.of(user(Role.ADMIN)));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(storage.getSignedUrl(PROOF, 900)).thenReturn("https://r2.example/signed?exp=900");

        assertEquals("https://r2.example/signed?exp=900", fileService.paymentProofRedirect(orderId.toString(), ADMIN));
    }

    @Test
    void redirectRefusesCustomers() {
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(user(Role.CUSTOMER)));

        assertThrows(ForbiddenException.class,
                () -> fileService.paymentProofRedirect(orderId.toString(), "customer@example.com"));
    }

    @Test
    void redirectReportsAnOrderWithoutProof() {
        Order order = order(OrderStatus.PENDING, PaymentStatus.PENDING, null);
        when(users.findByEmail(ADMIN)).thenReturn(Optional.of(user(Role.ADMIN)));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> fileService.paymentProofRedirect(orderId.toString(), ADMIN));
        assertEquals("This order has no payment proof", ex.getMessage());
    }

    @Test
    void unverifiedOrderHasNoAdminReferenceYet() {
        Order order = order(OrderStatus.PENDING, PaymentStatus.PENDING, PROOF);
        order.setPaymentReference("CUSTOMER-RCPT-77");

        OrderResponse response = mapper.toDto(order);

        assertEquals("CUSTOMER-RCPT-77", response.getPaymentReference());
        assertNull(response.getPaymentVerifiedReference());
    }

    private Order order(OrderStatus status, PaymentStatus paymentStatus, String proofKey) {
        return Order.builder()
                .id(orderId)
                .orderNumber("PG-1")
                .customerEmail("customer@example.com")
                .status(status)
                .paymentStatus(paymentStatus)
                .paymentProofUrl(proofKey)
                .build();
    }

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email(ADMIN).role(role).build();
    }
}
