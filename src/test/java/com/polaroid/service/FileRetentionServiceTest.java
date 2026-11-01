package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.repository.OrderRepository;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileRetentionServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final StorageService storageService = mock(StorageService.class);

    @Test
    void deletesCustomerPhotosAndStripAssetsButNeverPaymentProofs() throws Exception {
        Order order = dueOrder();
        when(orderRepository.findDueForFileDeletion(any(), any(), any())).thenReturn(List.of(order));
        when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileRetentionService service = service(false);
        service.purgeDueOrderFiles();

        verify(storageService).delete("orders/o/a.jpg");
        verify(storageService).delete("orders/o/assets/b.jpg");
        verify(storageService, never()).delete("payment-proofs/proof.jpg");
        assertNotNull(order.getFilesDeletedAt());
        assertNull(order.getFilesDeleteError());
    }

    @Test
    void keepsOrderEligibleForRetryWhenAStorageDeleteFails() throws Exception {
        Order order = dueOrder();
        when(orderRepository.findDueForFileDeletion(any(), any(), any())).thenReturn(List.of(order));
        when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("R2 unavailable"))
                .when(storageService).delete("orders/o/a.jpg");

        FileRetentionService service = service(false);
        service.purgeDueOrderFiles();

        assertNull(order.getFilesDeletedAt());
        assertEquals(1, order.getFilesDeleteAttempts());
        assertTrue(order.getFilesDeleteError().contains("R2 unavailable"));
    }

    @Test
    void dryRunDoesNotDeleteOrMarkFiles() throws Exception {
        Order order = dueOrder();
        when(orderRepository.findDueForFileDeletion(any(), any(), any())).thenReturn(List.of(order));
        when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

        FileRetentionService service = service(true);
        service.purgeDueOrderFiles();

        verify(storageService, never()).delete(any());
        assertNull(order.getFilesDeletedAt());
    }

    private Order dueOrder() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("PG-RETENTION-1")
                .status(OrderStatus.CANCELLED)
                .filesDeleteAfter(LocalDateTime.now().minusMinutes(1))
                .build();

        OrderItem regular = OrderItem.builder()
                .id(UUID.randomUUID())
                .sizeId("4r")
                .s3Keys("[\"orders/o/a.jpg\",\"payment-proofs/proof.jpg\"]")
                .build();
        OrderItem strip = OrderItem.builder()
                .id(UUID.randomUUID())
                .sizeId("strip-3")
                .stripDesign("{\"schemaVersion\":1,\"slotCount\":3,\"assets\":[{\"id\":\"a1\",\"status\":\"uploaded\",\"storageKey\":\"orders/o/assets/b.jpg\"}],\"designs\":[]}")
                .build();
        order.getItems().add(regular);
        order.getItems().add(strip);
        return order;
    }

    private FileRetentionService service(boolean dryRun) throws Exception {
        FileRetentionService service = new FileRetentionService(orderRepository, storageService, new ObjectMapper());
        setField(service, "enabled", true);
        setField(service, "dryRun", dryRun);
        setField(service, "batchSize", 50);
        return service;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
