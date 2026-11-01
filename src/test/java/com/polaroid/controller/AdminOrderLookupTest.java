package com.polaroid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.mapper.OrderMapper;
import com.polaroid.model.Order;
import com.polaroid.repository.OrderRepository;
import com.polaroid.service.OrderService;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOrderLookupTest {
    @Test
    void refreshAfterProofUploadUsesDatabaseIdAndReturnsSavedReceipt() throws Exception {
        UUID id = UUID.randomUUID();
        var order = Order.builder().id(id).orderNumber("PG27365857C53B")
                .paymentProofUrl("payment-proofs/receipt.png")
                .paymentProofSource("WHATSAPP").paymentReviewStatus("AWAITING_VERIFICATION").build();
        var repository = mock(OrderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(order));
        var storage = mock(StorageService.class);
        when(storage.getSignedUrl("payment-proofs/receipt.png", 300)).thenReturn("https://example.test/receipt.png");
        var mapper = new OrderMapper(new ObjectMapper(), storage);
        var service = new OrderService(repository, null, null, null, null, mapper, null, new ObjectMapper(), null);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminController(service, null, null, null, null)).build();

        mvc.perform(get("/api/admin/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.orderNumber").value("PG27365857C53B"))
                .andExpect(jsonPath("$.paymentProofUrl").value("https://example.test/receipt.png"))
                .andExpect(jsonPath("$.paymentReviewStatus").value("AWAITING_VERIFICATION"));
        verify(repository).findById(id);
        verify(repository, never()).findByOrderNumber(anyString());
    }
}
