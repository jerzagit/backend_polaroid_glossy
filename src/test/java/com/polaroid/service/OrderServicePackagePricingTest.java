package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.OrderRequest;
import com.polaroid.model.OrderItem;
import com.polaroid.model.Product;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checkout must price IC Full 10 pcs at RM6.00 and IC Border 10 pcs at
 * RM5.00, snapshotting size, style, and package onto the order item.
 */
class OrderServicePackagePricingTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        CatalogPricingService pricingService = mock(CatalogPricingService.class);
        PrintSizeRepository printSizeRepository = mock(PrintSizeRepository.class);
        orderService = new OrderService(null, null, null, printSizeRepository,
                null, null, null, new ObjectMapper(), pricingService);

        Product ic = Product.builder().id(1L).code("ic").name("IC").displayName("IC (5.5 x 8.9 cm)")
                .basePrice(new BigDecimal("2.00")).isActive(true).build();
        ProductVariant full = ProductVariant.builder().id(10L).productId(1L)
                .style(ProductStyle.FULL).label("Full").isActive(true).build();
        ProductPackage full10 = ProductPackage.builder().id(100L).variantId(10L)
                .quantity(10).price(new BigDecimal("6.00")).isActive(true).build();
        ProductVariant border = ProductVariant.builder().id(11L).productId(1L)
                .style(ProductStyle.BORDER).label("Border (Polaroid)").isActive(true).build();
        ProductPackage border10 = ProductPackage.builder().id(101L).variantId(11L)
                .quantity(10).price(new BigDecimal("5.00")).isActive(true).build();

        when(pricingService.resolveActivePackage(1L, 10L, 100L))
                .thenReturn(new CatalogPricingService.ResolvedPackage(ic, full, full10));
        when(pricingService.resolveActivePackage(1L, 11L, 101L))
                .thenReturn(new CatalogPricingService.ResolvedPackage(ic, border, border10));
    }

    @Test
    void icFullTenPcsCostsSixRinggit() throws Exception {
        OrderItem item = buildItem(1L, 10L, 100L, null);

        assertEquals(new BigDecimal("6.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("6.00"), item.getTotalPrice());
        assertEquals(10, item.getQuantity());
        assertEquals("ic", item.getSizeId());
        assertEquals("10", item.getVariantId());
        assertEquals("Full", item.getVariantLabel());
        assertEquals("FULL", item.getStyle());
        assertEquals("100", item.getPackageId());
        assertEquals(10, item.getPackageQuantity());
        assertEquals(1, item.getPackageCount());
        assertEquals(10, item.getExpectedImageCount());
    }

    @Test
    void icBorderTenPcsCostsFiveRinggit() throws Exception {
        OrderItem item = buildItem(1L, 11L, 101L, null);

        assertEquals(new BigDecimal("5.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("5.00"), item.getTotalPrice());
        assertEquals("Border (Polaroid)", item.getVariantLabel());
        assertEquals("BORDER", item.getStyle());
    }

    @Test
    void buyingTwoPackagesDoublesPrintsAndTotal() throws Exception {
        OrderItem item = buildItem(1L, 10L, 100L, 2);

        assertEquals(2, item.getPackageCount());
        assertEquals(20, item.getQuantity());
        assertEquals(new BigDecimal("12.00"), item.getTotalPrice());
        assertEquals(20, item.getExpectedImageCount());
    }

    @Test
    void selectionWithoutPackageOrSizeIsRejected() {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setQuantity(10);
        Throwable thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> buildItem(request));
        assertEquals(com.polaroid.exception.BadRequestException.class, thrown.getCause().getClass());
    }

    private OrderItem buildItem(Long productId, Long variantId, Long packageId, Integer packages) throws Exception {
        OrderRequest.OrderItemRequest request = new OrderRequest.OrderItemRequest();
        request.setProductId(productId);
        request.setVariantId(variantId);
        request.setPackageId(packageId);
        request.setPackages(packages);
        return buildItem(request);
    }

    private OrderItem buildItem(OrderRequest.OrderItemRequest request) throws Exception {
        Method method = OrderService.class.getDeclaredMethod("buildOrderItem", OrderRequest.OrderItemRequest.class);
        method.setAccessible(true);
        return (OrderItem) method.invoke(orderService, request);
    }
}
