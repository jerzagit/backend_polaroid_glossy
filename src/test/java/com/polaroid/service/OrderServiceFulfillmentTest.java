package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.OrderRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderServiceFulfillmentTest {

    @Test
    void expectedImageCountMultipliesSelectedImagesByQuantity() throws Exception {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setQuantity(3);
        request.setImageUrls(List.of("photo-1.jpg", "photo-2.jpg"));

        assertEquals(6, expectedImageCount(request));
    }

    @Test
    void explicitExpectedImageCountRemainsAuthoritative() throws Exception {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setQuantity(3);
        request.setImageUrls(List.of("photo-1.jpg", "photo-2.jpg"));
        request.setExpectedImageCount(4);

        assertEquals(4, expectedImageCount(request));
    }

    private int expectedImageCount(OrderRequest.OrderItemRequest request) throws Exception {
        OrderService service = new OrderService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper());
        Method method = OrderService.class.getDeclaredMethod("expectedImageCount", OrderRequest.OrderItemRequest.class);
        method.setAccessible(true);
        return (int) method.invoke(service, request);
    }
}
