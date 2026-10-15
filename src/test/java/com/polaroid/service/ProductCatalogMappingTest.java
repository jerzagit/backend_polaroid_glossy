package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.dto.response.ProductVariantResponse;
import com.polaroid.model.PrintSize;
import com.polaroid.model.PrintSizePricingTier;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizePricingTierRepository;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductMetaRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCatalogMappingTest {

    private PrintSizeRepository printSizeRepository;
    private ProductVariantRepository variantRepository;
    private ProductPackageRepository packageRepository;
    private ProductService productService;

    private PrintSize ic;
    private PrintSize icBorder;

    @BeforeEach
    void setUp() {
        printSizeRepository = mock(PrintSizeRepository.class);
        ProductMetaRepository metaRepository = mock(ProductMetaRepository.class);
        PrintSizePricingTierRepository tierRepository = mock(PrintSizePricingTierRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        packageRepository = mock(ProductPackageRepository.class);
        productService = new ProductService(printSizeRepository, metaRepository, tierRepository,
                variantRepository, packageRepository, new ObjectMapper());

        ic = PrintSize.builder().id("ic").name("IC").displayName("IC (5.5 x 8.9 cm)")
                .width(new BigDecimal("5.5")).height(new BigDecimal("8.9"))
                .price(new BigDecimal("2.00")).isActive(true).build();
        icBorder = PrintSize.builder().id("ic-border").name("IC Border").displayName("IC Border (5.5 x 8.9 cm)")
                .width(new BigDecimal("5.5")).height(new BigDecimal("8.9"))
                .price(new BigDecimal("2.50")).isActive(true).build();

        ProductVariant full = ProductVariant.builder().id("ic-full").productId("ic")
                .style(ProductStyle.FULL).label("Full").isActive(true).sortOrder(1).build();
        ProductVariant border = ProductVariant.builder().id("ic-border").productId("ic")
                .style(ProductStyle.BORDER).label("Border (Polaroid)").isActive(true).sortOrder(2).build();

        when(printSizeRepository.findById("ic")).thenReturn(Optional.of(ic));
        when(printSizeRepository.findById("ic-border")).thenReturn(Optional.of(icBorder));
        when(variantRepository.findByProductIdOrderBySortOrderAsc("ic")).thenReturn(List.of(full, border));
        when(packageRepository.findByVariantIdOrderBySortOrderAsc("ic-full")).thenReturn(List.of(
                package10("ic-full-10", "ic-full", "6.00"),
                package10("ic-full-20", "ic-full", "12.00")));
        when(packageRepository.findByVariantIdOrderBySortOrderAsc("ic-border")).thenReturn(List.of(
                package10("ic-border-10", "ic-border", "5.00"),
                package10("ic-border-20", "ic-border", "10.00")));
        when(tierRepository.findBySizeIdOrderByQuantityAsc("ic")).thenReturn(List.of());
        when(tierRepository.findBySizeIdOrderByQuantityAsc("ic-border")).thenReturn(List.of());
    }

    @Test
    void productExposesBothStylesWithOwnPackagePrices() {
        ProductListingResponse product = productService.getProductById("ic");

        assertEquals("ic", product.getId());
        assertEquals("cm", product.getDimensions().getUnit());
        assertEquals(2, product.getVariants().size());

        ProductVariantResponse full = product.getVariants().get(0);
        assertEquals("ic-full", full.getId());
        assertEquals(ProductStyle.FULL, full.getStyle());
        assertEquals("Full", full.getLabel());
        assertEquals("6.00", full.getPackages().get(0).getPrice());

        ProductVariantResponse border = product.getVariants().get(1);
        assertEquals("ic-border", border.getId());
        assertEquals(ProductStyle.BORDER, border.getStyle());
        assertEquals("Border (Polaroid)", border.getLabel());
        assertEquals("5.00", border.getPackages().get(0).getPrice());
    }

    @Test
    void legacyIcBorderLinkKeepsIdentityButUsesCanonicalBorderVariant() {
        ProductListingResponse product = productService.getProductById("ic-border");

        assertEquals("ic-border", product.getId());
        assertEquals("IC Border (5.5 x 8.9 cm)", product.getDisplayName());
        assertEquals(1, product.getVariants().size());
        assertEquals(ProductStyle.BORDER, product.getVariants().get(0).getStyle());
        assertEquals("5.00", product.getVariants().get(0).getPackages().get(0).getPrice());
    }

    @Test
    void inactiveVariantsAreHiddenFromStorefrontButShownToAdmin() {
        ProductVariant full = variantRepository.findByProductIdOrderBySortOrderAsc("ic").get(0);
        full.setIsActive(false);

        assertFalse(productService.getProductById("ic").getVariants().stream()
                .anyMatch(v -> "ic-full".equals(v.getId())));

        ProductListingResponse admin = productService.getProductForAdmin("ic");
        assertTrue(admin.getVariants().stream().anyMatch(v -> "ic-full".equals(v.getId()) && !v.getActive()));
    }

    @Test
    void inactivePackagesAreHiddenFromStorefront() {
        packageRepository.findByVariantIdOrderBySortOrderAsc("ic-full").get(0).setIsActive(false);

        ProductListingResponse product = productService.getProductById("ic");
        ProductVariantResponse full = product.getVariants().stream()
                .filter(v -> "ic-full".equals(v.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, full.getPackages().size());
        assertEquals("12.00", full.getPackages().get(0).getPrice());
    }

    private ProductPackage package10(String id, String variantId, String price) {
        return ProductPackage.builder().id(id).variantId(variantId)
                .quantity(Integer.parseInt(id.substring(id.lastIndexOf('-') + 1)))
                .price(new BigDecimal(price)).isActive(true).build();
    }
}
