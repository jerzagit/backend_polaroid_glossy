package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.UploadRateLimitException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.StripDesignState;
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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 upload reliability: authoritative status counts, replay-safe photo identities,
 * per-order attempt budgets and upload-later on drafts.
 */
class FileServiceUploadReliabilityTest {
    private static final String ORDER_NUMBER = "PG71946458A6DA";
    private static final String CUSTOMER_EMAIL = "customer@example.com";

    private StorageService storageService;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private UserRepository userRepository;
    private FileService fileService;
    private UUID userId;
    private UUID orderId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        userRepository = mock(UserRepository.class);
        fileService = new FileService(storageService, orderRepository, orderItemRepository, userRepository, new ObjectMapper());
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        when(storageService.getSignedUrl(any(String.class), any(Integer.class))).thenReturn("https://r2.example/photo.jpg");
    }

    @Test
    void uploadStatusSeparatesSavedIdentitiesFromMissingOnes() {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        // One photo saved with a stable identity, one saved before identities existed.
        OrderItem item = item(itemId, order, 3, json(List.of(
                "orders/" + ORDER_NUMBER + "/" + itemId + "/upload-p2.jpg",
                "orders/" + ORDER_NUMBER + "/" + itemId + "/legacy-uuid.jpg")));
        order.setItems(List.of(item));
        stubOrderLookup(order, item);

        @SuppressWarnings("unchecked")
        Map<String, Object> status = fileService.uploadStatus(orderId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) status.get("items");

        assertEquals(1, items.size());
        assertEquals(3, items.get(0).get("expectedImageCount"));
        assertEquals(2, items.get(0).get("uploadedImageCount"));
        assertEquals(1, items.get(0).get("remainingImageCount"));
        assertEquals(List.of("p2"), items.get(0).get("uploadedPhotoIds"));
        assertEquals(Boolean.FALSE, items.get(0).get("complete"));
        assertEquals(Boolean.FALSE, status.get("complete"));
    }

    @Test
    void uploadStatusCountsUploadsMadeBeforeStableIdentitiesAsComplete() {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 2, json(List.of(
                "orders/" + ORDER_NUMBER + "/" + itemId + "/legacy-a.jpg",
                "orders/" + ORDER_NUMBER + "/" + itemId + "/legacy-b.jpg")));
        order.setItems(List.of(item));
        stubOrderLookup(order, item);

        @SuppressWarnings("unchecked")
        Map<String, Object> status = fileService.uploadStatus(orderId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) status.get("items");

        // The counts are authoritative even though no per-photo identity can be named;
        // clients must trust `complete` rather than re-uploading unknown identities.
        assertEquals(Boolean.TRUE, items.get(0).get("complete"));
        assertEquals(List.of(), items.get(0).get("uploadedPhotoIds"));
        assertEquals(Boolean.TRUE, status.get("complete"));
    }

    @Test
    void uploadStatusReportsStripItemsFromReservedAssets() throws Exception {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 1, "[]");
        item.setSizeId("strip-3");
        item.setStripDesign(new ObjectMapper().writeValueAsString(readyStripState()));
        order.setItems(List.of(item));
        stubOrderLookup(order, item);

        @SuppressWarnings("unchecked")
        Map<String, Object> status = fileService.uploadStatus(orderId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) status.get("items");

        assertEquals("strip", items.get(0).get("kind"));
        assertEquals(1, items.get(0).get("expectedImageCount"));
        assertEquals(1, items.get(0).get("uploadedImageCount"));
        assertEquals(0, items.get(0).get("remainingImageCount"));
        assertEquals(Boolean.TRUE, items.get(0).get("complete"));
    }

    @Test
    void replayingSavedPhotoIdentityDoesNotAppendOrNeedCapacity() throws Exception {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        // Item is already full: only the duplicate-safe lookup before the capacity check can succeed.
        OrderItem item = item(itemId, order, 1, json(List.of("orders/" + ORDER_NUMBER + "/" + itemId + "/upload-p1.jpg")));
        order.setItems(List.of(item));
        when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user()));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));

        Map<String, Object> result = fileService.uploadWithId(jpegFile(), orderId.toString(), itemId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL, "p1");

        assertEquals(Boolean.TRUE, result.get("alreadyUploaded"));
        assertEquals("p1", result.get("photoId"));
        assertEquals(1, result.get("uploadedImageCount"));
        assertEquals(0, result.get("remainingImageCount"));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }

    @Test
    void draftOrderAcceptsUploadsSoUploadLaterWorksBeforePayment() throws Exception {
        Order order = order(orderId, userId, OrderStatus.DRAFT, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 2, "[]");
        order.setItems(List.of(item));
        when(orderRepository.lockById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user()));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));
        when(orderItemRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(item));

        Map<String, Object> result = fileService.uploadFile(jpegFile(), orderId.toString(), itemId.toString(), CUSTOMER_EMAIL, CUSTOMER_EMAIL);

        assertEquals(1, result.get("uploadedImageCount"));
        assertEquals(1, result.get("remainingImageCount"));
    }

    @Test
    void attemptBudgetIsScopedToOneOrderAndReportsRetryAfter() throws Exception {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        order.setItems(List.of(item(itemId, order, 1, "[]")));
        Method limit = FileService.class.getDeclaredMethod("enforceAnonymousUploadRateLimit", Order.class, String.class);
        limit.setAccessible(true);

        // The floor is 60 attempts per window regardless of the purchased quantity.
        for (int attempt = 0; attempt < 60; attempt++) assertDoesNotThrow(() -> limit.invoke(fileService, order, "203.0.113.7"));

        java.lang.reflect.InvocationTargetException thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> limit.invoke(fileService, order, "203.0.113.7"));
        assertTrue(thrown.getCause() instanceof UploadRateLimitException);
        assertTrue(((UploadRateLimitException) thrown.getCause()).getRetryAfter() > 0);

        // A different order keeps its own budget.
        Order other = order(UUID.randomUUID(), userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        other.setItems(List.of(item(UUID.randomUUID(), other, 1, "[]")));
        assertDoesNotThrow(() -> limit.invoke(fileService, other, "203.0.113.7"));
    }

    @Test
    void statusRequiresMatchingCustomerEmail() {
        Order order = order(orderId, userId, OrderStatus.PENDING, PaymentStatus.PENDING);
        OrderItem item = item(itemId, order, 1, "[]");
        order.setItems(List.of(item));
        stubOrderLookup(order, item);

        assertThrows(com.polaroid.exception.ForbiddenException.class,
                () -> fileService.uploadStatus(orderId.toString(), "someone-else@example.com", CUSTOMER_EMAIL, null));
    }

    private void stubOrderLookup(Order order, OrderItem item) {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(user()));
        when(orderItemRepository.findByOrderIdAndIdForUpdate(orderId, itemId)).thenReturn(Optional.of(item));
    }

    private StripDesignState readyStripState() {
        StripDesignState state = new StripDesignState();
        state.setSchemaVersion(1);
        state.setSlotCount(3);
        StripDesignState.Asset asset = new StripDesignState.Asset();
        asset.setId("asset-1");
        asset.setClientAssetId("photo-a");
        asset.setStatus("uploaded");
        asset.setStorageKey("orders/" + ORDER_NUMBER + "/assets/asset-1/original.jpg");
        asset.setSha256("abc123");
        state.getAssets().add(asset);
        StripDesignState.Design design = new StripDesignState.Design();
        design.setId("design-1");
        design.setClientDesignId("design-a");
        design.setCopies(1);
        for (int position = 1; position <= 3; position++) {
            StripDesignState.Slot slot = new StripDesignState.Slot();
            slot.setPosition(position);
            slot.setAssetId("asset-1");
            design.getSlots().add(slot);
        }
        state.getDesigns().add(design);
        return state;
    }

    private Order order(UUID id, UUID ownerId, OrderStatus status, PaymentStatus paymentStatus) {
        return Order.builder()
                .id(id)
                .orderNumber(ORDER_NUMBER)
                .userId(ownerId)
                .customerEmail(CUSTOMER_EMAIL)
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
    }

    private OrderItem item(UUID id, Order order, int expectedImageCount, String s3Keys) {
        return OrderItem.builder()
                .id(id)
                .order(order)
                .quantity(expectedImageCount)
                .expectedImageCount(expectedImageCount)
                .s3Keys(s3Keys)
                .images("[]")
                .build();
    }

    private User user() {
        return User.builder().id(userId).email(CUSTOMER_EMAIL).role(Role.CUSTOMER).build();
    }

    private String json(List<String> values) {
        try {
            return new ObjectMapper().writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile jpegFile() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", output.toByteArray());
    }
}
