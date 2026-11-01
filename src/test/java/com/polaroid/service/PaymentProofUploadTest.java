package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.*;
import com.polaroid.model.*;
import com.polaroid.model.enums.*;
import com.polaroid.repository.*;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentProofUploadTest {
    @Test void receiptEvidenceCannotBeDeletedThroughGenericFileEndpoint() {
        var storage = mock(StorageService.class);
        var service = new FileService(storage, null, null, null, new ObjectMapper());
        assertThrows(BadRequestException.class, () -> service.deleteFile("payment-proofs/PG/receipt.png"));
        verifyNoInteractions(storage);
    }
    @Test void adminAndCustomerSourcesAndReplacementHistoryAreSavedWithoutMarkingPaid() throws Exception {
        var orders = mock(OrderRepository.class); var users = mock(UserRepository.class); var storage = mock(StorageService.class);
        var json = new ObjectMapper(); var service = new FileService(storage, orders, null, users, json);
        UUID customerId = UUID.randomUUID();
        var order = Order.builder().id(UUID.randomUUID()).orderNumber("PG-UPLOAD").userId(customerId)
                .customerEmail("customer").status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING)
                .paymentMethod("bank_transfer").paymentProofUrl("old-receipt").paymentReviewStatus("REJECTED").build();
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(users.findByEmail("customer")).thenReturn(Optional.of(User.builder().id(customerId).role(Role.CUSTOMER).build()));
        when(users.findByEmail("admin")).thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build()));
        when(users.findByEmail("packer")).thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).role(Role.PACKER).build()));
        when(storage.getSignedUrl(anyString(), anyInt())).thenReturn("https://example.test/receipt");
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "receipt.png", "image/png", bytes.toByteArray());
        service.uploadPaymentProof(file, order.getId().toString(), "REF-1", "customer");
        assertEquals("SYSTEM_UPLOAD", order.getPaymentProofSource());
        assertEquals("AWAITING_VERIFICATION", order.getPaymentReviewStatus());
        assertEquals("old-receipt", PaymentReviews.read(order, json).get(0).get("proofKey"));
        service.uploadPaymentProof(file, order.getId().toString(), "REF-2", "admin");
        assertEquals("WHATSAPP", order.getPaymentProofSource());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
        assertEquals(3, PaymentReviews.read(order, json).size());
        assertThrows(ForbiddenException.class, () -> service.uploadPaymentProof(file, order.getId().toString(), "REF", "packer"));
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThrows(BadRequestException.class, () -> service.uploadPaymentProof(file, order.getId().toString(), "REF", "admin"));
    }
}
