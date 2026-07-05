package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.UploadConflictException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
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
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceFulfillmentTest {
    private static final String ORDER_NUMBER = "PG71946458A6DA";
    private static final String CUSTOMER_EMAIL = "customer@example.com";

    private StorageService storageService;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private UserRepository userRepository;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        userRepository = mock(UserRepository.class);
        fileService = new FileService(
                storageService,
                orderRepository,
                orderItemRepository,
                userRepository,
                new ObjectMapper());
    }

    @Test
    void uploadToPendingOrderAppendsExactItemAndReturnsCounts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 3, "[\"orders/old/item/photo.jpg\"]");

        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user(userId, CUSTOMER_EMAIL)));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(java.util.List.of(item));
        when(storageService.getSignedUrl(any(String.class), eq(3600))).thenReturn("https://r2.example/new.jpg");

        Map<String, Object> result = fileService.uploadFile(
                jpegFile(),
                ORDER_NUMBER,
                itemId.toString(),
                CUSTOMER_EMAIL,
                CUSTOMER_EMAIL);

        assertEquals(itemId.toString(), result.get("orderItemId"));
        assertEquals(2, result.get("uploadedImageCount"));
        assertEquals(3, result.get("expectedImageCount"));
        assertEquals(1, result.get("remainingImageCount"));
        verify(orderItemRepository).save(item);
    }

    @Test
    void authenticatedCustomerCanUploadWhenOrderUserIdIsMissingButEmailMatches() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Order order = order(orderId, null, OrderStatus.PROCESSING, PaymentStatus.PAID);
        OrderItem item = item(itemId, order, 1, "[]");

        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user(userId, CUSTOMER_EMAIL)));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(java.util.List.of(item));
        when(storageService.getSignedUrl(any(String.class), eq(3600))).thenReturn("https://r2.example/new.jpg");

        Map<String, Object> result = fileService.uploadFile(
                jpegFile(),
                ORDER_NUMBER,
                itemId.toString(),
                CUSTOMER_EMAIL,
                CUSTOMER_EMAIL);

        assertEquals(1, result.get("uploadedImageCount"));
        assertEquals(0, result.get("remainingImageCount"));
    }

    @Test
    void uploadToClosedOrderReturnsConflict() {
        UUID userId = UUID.randomUUID();
        Order order = order(UUID.randomUUID(), userId, OrderStatus.DELIVERED, PaymentStatus.PAID);

        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user(userId, CUSTOMER_EMAIL)));

        UploadConflictException ex = assertThrows(UploadConflictException.class, () ->
                fileService.uploadFile(jpegFile(), ORDER_NUMBER, UUID.randomUUID().toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL));

        assertEquals("Order is not open for uploads", ex.getMessage());
    }

    @Test
    void uploadToFullItemReturnsConflictWithCounts() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 2, "[\"a\",\"b\"]");

        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user(userId, CUSTOMER_EMAIL)));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));

        UploadConflictException ex = assertThrows(UploadConflictException.class, () ->
                fileService.uploadFile(jpegFile(), ORDER_NUMBER, itemId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL));

        assertEquals(2, ex.getUploadedImageCount());
        assertEquals(2, ex.getExpectedImageCount());
        assertEquals(0, ex.getRemainingImageCount());
    }

    private Order order(UUID orderId, UUID userId, OrderStatus status, PaymentStatus paymentStatus) {
        return Order.builder()
                .id(orderId)
                .orderNumber(ORDER_NUMBER)
                .userId(userId)
                .customerEmail(CUSTOMER_EMAIL)
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
    }

    private OrderItem item(UUID itemId, Order order, int expectedImageCount, String s3Keys) {
        return OrderItem.builder()
                .id(itemId)
                .order(order)
                .quantity(expectedImageCount)
                .expectedImageCount(expectedImageCount)
                .s3Keys(s3Keys)
                .images("[]")
                .build();
    }

    private User user(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .role(Role.CUSTOMER)
                .build();
    }

    private MockMultipartFile jpegFile() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", output.toByteArray());
    }
}
