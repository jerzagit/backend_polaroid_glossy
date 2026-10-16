package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.dto.response.ProductVariantResponse;
import com.polaroid.model.Product;
import com.polaroid.model.ProductAlias;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizePricingTierRepository;
import com.polaroid.repository.ProductAliasRepository;
import com.polaroid.repository.ProductMetaRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductRepository;
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

    private ProductRepository productRepository;
    private ProductAliasRepository aliasRepository;
    private ProductVariantRepository variantRepository;
    private ProductPackageRepository packageRepository;
    private ProductService productService;

    private Product ic;
    private ProductVariant full;
    private ProductVariant border;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        aliasRepository = mock(ProductAliasRepository.class);
        ProductMetaRepository metaRepository = mock(ProductMetaRepository.class);
        PrintSizePricingTierRepository tierRepository = mock(PrintSizePricingTierRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        packageRepository = mock(ProductPackageRepository.class);
        productService = new ProductService(productRepository, aliasRepository, metaRepository, tierRepository,
                variantRepository, packageRepository, new ObjectMapper());

        ic = Product.builder().id(1L).code("ic").name("IC").displayName("IC (5.5 x 8.9 cm)")
                .width(new BigDecimal("5.5")).height(new BigDecimal("8.9"))
                .basePrice(new BigDecimal("2.00")).isActive(true).build();

        full = ProductVariant.builder().id(10L).productId(1L)
                .style(ProductStyle.FULL).label("Full").isActive(true).sortOrder(1).build();
        border = ProductVariant.builder().id(11L).productId(1L)
                .style(ProductStyle.BORDER).label("Border (Polaroid)").isActive(true).sortOrder(2).build();

        when(productRepository.findByCode("ic")).thenReturn(Optional.of(ic));
        when(productRepository.findById(1L)).thenReturn(Optional.of(ic));
        when(aliasRepository.findById("ic-border"))
                .thenReturn(Optional.of(ProductAlias.builder()
                        .legacyCode("ic-border").productId(1L).style(ProductStyle.BORDER).build()));
        when(variantRepository.findByProductIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(full, border));
        when(packageRepository.findByVariantIdOrderBySortOrderAscQuantityAsc(10L)).thenReturn(List.of(
                pkg(100L, 10L, 10, "6.00"),
                pkg(101L, 10L, 20, "12.00")));
        when(packageRepository.findByVariantIdOrderBySortOrderAscQuantityAsc(11L)).thenReturn(List.of(
                pkg(102L, 11L, 10, "5.00"),
                pkg(103L, 11L, 20, "10.00")));
        when(tierRepository.findBySizeIdOrderByQuantityAsc("ic")).thenReturn(List.of());
    }

    @Test
    void productExposesBothStylesWithOwnPackagePrices() {
        ProductListingResponse product = productService.getProductById("ic");

        assertEquals(1L, product.getId());
        assertEquals("ic", product.getCode());
        assertEquals("cm", product.getDimensions().getUnit());
        assertEquals(2, product.getVariants().size());

        ProductVariantResponse fullResponse = product.getVariants().get(0);
        assertEquals(10L, fullResponse.getId());
        assertEquals(ProductStyle.FULL, fullResponse.getStyle());
        assertEquals("Full", fullResponse.getLabel());
        assertEquals("6.00", fullResponse.getPackages().get(0).getPrice());

        ProductVariantResponse borderResponse = product.getVariants().get(1);
        assertEquals(11L, borderResponse.getId());
        assertEquals(ProductStyle.BORDER, borderResponse.getStyle());
        assertEquals("Border (Polaroid)", borderResponse.getLabel());
        assertEquals("5.00", borderResponse.getPackages().get(0).getPrice());
    }

    @Test
    void productCanBeFetchedByNumericId() {
        ProductListingResponse product = productService.getProductById("1");
        assertEquals("ic", product.getCode());
        assertEquals(2, product.getVariants().size());
    }

    @Test
    void legacyIcBorderLinkResolvesToCanonicalBorderStyle() {
        ProductListingResponse product = productService.getProductById("ic-border");

        assertEquals(1L, product.getId());
        assertEquals("ic", product.getCode());
        assertEquals(1, product.getVariants().size());
        assertEquals(ProductStyle.BORDER, product.getVariants().get(0).getStyle());
        assertEquals("5.00", product.getVariants().get(0).getPackages().get(0).getPrice());
    }

    @Test
    void inactiveVariantsAreHiddenFromStorefrontButShownToAdmin() {
        full.setIsActive(false);

        assertFalse(productService.getProductById("ic").getVariants().stream()
                .anyMatch(v -> Long.valueOf(10L).equals(v.getId())));

        ProductListingResponse admin = productService.getProductForAdmin(1L);
        assertTrue(admin.getVariants().stream()
                .anyMatch(v -> Long.valueOf(10L).equals(v.getId()) && !v.getActive()));
    }

    @Test
    void inactivePackagesAreHiddenFromStorefront() {
        packageRepository.findByVariantIdOrderBySortOrderAscQuantityAsc(10L).get(0).setIsActive(false);

        ProductListingResponse product = productService.getProductById("ic");
        ProductVariantResponse fullResponse = product.getVariants().stream()
                .filter(v -> Long.valueOf(10L).equals(v.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, fullResponse.getPackages().size());
        assertEquals("12.00", fullResponse.getPackages().get(0).getPrice());
    }

    private ProductPackage pkg(Long id, Long variantId, int quantity, String price) {
        return ProductPackage.builder().id(id).variantId(variantId)
                .quantity(quantity).price(new BigDecimal(price)).isActive(true).build();
    }
}
