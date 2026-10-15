package com.polaroid.service;

import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.PriceConflictException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.PrintSize;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogPricingServiceTest {

    private PrintSizeRepository printSizeRepository;
    private ProductVariantRepository variantRepository;
    private ProductPackageRepository packageRepository;
    private CatalogPricingService service;

    private PrintSize ic;
    private ProductVariant icFull;
    private ProductVariant icBorder;
    private ProductPackage full10;
    private ProductPackage border10;

    @BeforeEach
    void setUp() {
        printSizeRepository = mock(PrintSizeRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        packageRepository = mock(ProductPackageRepository.class);
        service = new CatalogPricingService(printSizeRepository, variantRepository, packageRepository);

        ic = PrintSize.builder().id("ic").name("IC").displayName("IC (5.5 x 8.9 cm)")
                .price(new BigDecimal("2.00")).isActive(true).build();
        icFull = ProductVariant.builder().id("ic-full").productId("ic").style(ProductStyle.FULL)
                .label("Full").isActive(true).build();
        icBorder = ProductVariant.builder().id("ic-border").productId("ic").style(ProductStyle.BORDER)
                .label("Border (Polaroid)").isActive(true).build();
        full10 = ProductPackage.builder().id("ic-full-10").variantId("ic-full")
                .quantity(10).price(new BigDecimal("6.00")).isActive(true).build();
        border10 = ProductPackage.builder().id("ic-border-10").variantId("ic-border")
                .quantity(10).price(new BigDecimal("5.00")).isActive(true).build();

        when(printSizeRepository.findById("ic")).thenReturn(Optional.of(ic));
        when(variantRepository.findById("ic-full")).thenReturn(Optional.of(icFull));
        when(variantRepository.findById("ic-border")).thenReturn(Optional.of(icBorder));
        when(packageRepository.findById("ic-full-10")).thenReturn(Optional.of(full10));
        when(packageRepository.findById("ic-border-10")).thenReturn(Optional.of(border10));
    }

    @Test
    void icFullTenCostsSixRinggit() {
        var resolved = service.resolveActivePackage("ic", "ic-full", "ic-full-10");
        assertEquals(new BigDecimal("6.00"), resolved.pkg().getPrice());
        assertEquals(10, resolved.pkg().getQuantity());
        assertEquals(new BigDecimal("6.00"), resolved.totalPrice(1));
        assertEquals(new BigDecimal("12.00"), resolved.totalPrice(2));
    }

    @Test
    void icBorderTenCostsFiveRinggit() {
        var resolved = service.resolveActivePackage("ic", "ic-border", "ic-border-10");
        assertEquals(new BigDecimal("5.00"), resolved.pkg().getPrice());
    }

    @Test
    void packageFromAnotherVariantIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.resolveActivePackage("ic", "ic-border", "ic-full-10"));
    }

    @Test
    void packageFromAnotherSizeIsRejected() {
        assertThrows(BadRequestException.class,
                () -> service.resolveActivePackage("polaroid-mini", "ic-full", "ic-full-10"));
    }

    @Test
    void inactivePackageIsRejected() {
        full10.setIsActive(false);
        assertThrows(BadRequestException.class,
                () -> service.resolveActivePackage("ic", "ic-full", "ic-full-10"));
    }

    @Test
    void inactiveVariantIsRejected() {
        icFull.setIsActive(false);
        assertThrows(BadRequestException.class,
                () -> service.resolveActivePackage("ic", "ic-full", "ic-full-10"));
    }

    @Test
    void inactiveProductIsRejected() {
        ic.setIsActive(false);
        assertThrows(BadRequestException.class,
                () -> service.resolveActivePackage("ic", "ic-full", "ic-full-10"));
    }

    @Test
    void priceChangeBeforePaymentRequiresReconfirmation() {
        Order order = orderWith(full10, new BigDecimal("4.00"), new BigDecimal("4.00"));
        PriceConflictException ex = assertThrows(PriceConflictException.class,
                () -> service.assertOrderPricingCurrent(order));
        assertEquals(PriceConflictException.CODE_PRICE_CHANGED, ex.getCode());
        assertEquals(new BigDecimal("13.00"), ex.getCurrentTotal());
    }

    @Test
    void unavailablePackageBlocksPayment() {
        full10.setIsActive(false);
        Order order = orderWith(full10, new BigDecimal("6.00"), new BigDecimal("6.00"));
        PriceConflictException ex = assertThrows(PriceConflictException.class,
                () -> service.assertOrderPricingCurrent(order));
        assertEquals(PriceConflictException.CODE_UNAVAILABLE, ex.getCode());
    }

    @Test
    void currentPricingPassesPaymentGuard() {
        Order order = orderWith(full10, new BigDecimal("6.00"), new BigDecimal("6.00"));
        service.assertOrderPricingCurrent(order);
    }

    @Test
    void resyncUpdatesSnapshotFromCatalog() {
        Order order = orderWith(full10, new BigDecimal("4.00"), new BigDecimal("4.00"));
        order.getItems().get(0).setPackageCount(2);

        BigDecimal subtotal = service.resyncOrderItemPrices(order);

        assertEquals(new BigDecimal("12.00"), subtotal);
        OrderItem item = order.getItems().get(0);
        assertEquals(new BigDecimal("6.00"), item.getPackagePrice());
        assertEquals(new BigDecimal("6.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("12.00"), item.getTotalPrice());
        assertEquals(20, item.getQuantity());
    }

    private Order orderWith(ProductPackage pkg, BigDecimal snapshotPackagePrice, BigDecimal snapshotTotal) {
        OrderItem item = OrderItem.builder()
                .sizeId("ic")
                .variantId(pkg.getVariantId())
                .packageId(pkg.getId())
                .packageCount(1)
                .packageQuantity(pkg.getQuantity())
                .packagePrice(snapshotPackagePrice)
                .quantity(pkg.getQuantity())
                .unitPrice(snapshotPackagePrice)
                .totalPrice(snapshotTotal)
                .build();
        Order order = Order.builder()
                .customerName("Test")
                .customerEmail("test@example.com")
                .subtotal(snapshotTotal)
                .shipping(new BigDecimal("7.00"))
                .total(snapshotTotal.add(new BigDecimal("7.00")))
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        order.setItems(List.of(item));
        return order;
    }
}
