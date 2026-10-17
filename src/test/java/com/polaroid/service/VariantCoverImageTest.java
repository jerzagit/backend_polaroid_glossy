package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.model.Product;
import com.polaroid.model.ProductAlias;
import com.polaroid.model.ProductMeta;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.*;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Style-level cover images: public exposure, admin set/clear, and gallery-removal cleanup. */
class VariantCoverImageTest {

    private static final String FULL_COVER = "https://cdn.example.com/ic-full.png";
    private static final String BORDER_COVER = "https://cdn.example.com/ic-border.png";

    private ProductRepository products;
    private ProductMetaRepository metadata;
    private ProductVariantRepository variants;
    private ProductPackageRepository packages;
    private ProductAliasRepository aliases;
    private ProductImageService images;
    private ProductService listings;
    private CatalogAdminService admin;
    private ProductMeta meta;
    private ProductVariant full;
    private ProductVariant border;

    @BeforeEach
    void setup() {
        var mapper = new ObjectMapper();
        products = mock(ProductRepository.class);
        metadata = mock(ProductMetaRepository.class);
        variants = mock(ProductVariantRepository.class);
        packages = mock(ProductPackageRepository.class);
        aliases = mock(ProductAliasRepository.class);

        var product = Product.builder().id(1L).code("ic").name("IC").isActive(true).build();
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(products.findForImageUpdate(1L)).thenReturn(Optional.of(product));
        when(products.findByCode("ic")).thenReturn(Optional.of(product));

        meta = ProductMeta.builder().id("ic")
                .images("[\"" + FULL_COVER + "\",\"" + BORDER_COVER + "\"]").build();
        when(metadata.findById("ic")).thenAnswer(invocation -> Optional.of(meta));

        full = ProductVariant.builder().id(1L).productId(1L).style(ProductStyle.FULL)
                .label("Full").isActive(true).sortOrder(0).coverImageUrl(FULL_COVER).build();
        border = ProductVariant.builder().id(11L).productId(1L).style(ProductStyle.BORDER)
                .label("Border (Polaroid)").isActive(true).sortOrder(1).coverImageUrl(BORDER_COVER).build();
        when(variants.findByProductIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(full, border));
        when(variants.findById(1L)).thenReturn(Optional.of(full));
        when(variants.findById(11L)).thenReturn(Optional.of(border));
        when(packages.findByVariantIdOrderBySortOrderAscQuantityAsc(any())).thenReturn(List.of());

        listings = new ProductService(products, aliases, metadata,
                mock(PrintSizePricingTierRepository.class), variants, packages, mapper);
        images = new ProductImageService(products, metadata, listings, mock(StorageService.class), mapper, variants);
        ReflectionTestUtils.setField(images, "publicApiUrl", "https://api.example.com");
        admin = new CatalogAdminService(products, mock(PrintSizeRepository.class), variants, packages, listings, images);
    }

    @Test
    void listAndDetailExposeEachStylesCover() {
        ProductListingResponse listed = listings.getProductById("ic");
        assertEquals(FULL_COVER, listed.getVariants().get(0).getCoverImageUrl());
        assertEquals(BORDER_COVER, listed.getVariants().get(1).getCoverImageUrl());

        ProductListingResponse detailed = listings.getProductById("1");
        assertEquals(FULL_COVER, detailed.getVariants().get(0).getCoverImageUrl());
        assertEquals(BORDER_COVER, detailed.getVariants().get(1).getCoverImageUrl());
    }

    @Test
    void aliasDetailReturnsOnlyThatStylesCover() {
        var alias = ProductAlias.builder().legacyCode("ic-border").productId(1L).style(ProductStyle.BORDER).build();
        when(aliases.findById("ic-border")).thenReturn(Optional.of(alias));

        ProductListingResponse aliased = listings.getProductById("ic-border");
        assertEquals(1, aliased.getVariants().size());
        assertEquals(ProductStyle.BORDER, aliased.getVariants().get(0).getStyle());
        assertEquals(BORDER_COVER, aliased.getVariants().get(0).getCoverImageUrl());
    }

    @Test
    void variantWithoutCoverReportsNull() {
        full.setCoverImageUrl(null);
        assertNull(listings.getProductById("ic").getVariants().get(0).getCoverImageUrl());
    }

    @Test
    void adminSetsAndClearsCover() {
        admin.setVariantCover(1L, BORDER_COVER);
        assertEquals(BORDER_COVER, full.getCoverImageUrl());
        verify(variants).save(full);

        admin.setVariantCover(1L, null);
        assertNull(full.getCoverImageUrl());
        verify(variants, times(2)).save(full);
    }

    @Test
    void adminRejectsCoverOutsideTheProductGallery() {
        var rejected = assertThrows(BadRequestException.class,
                () -> admin.setVariantCover(1L, "https://cdn.example.com/someone-else.png"));
        assertTrue(rejected.getMessage().contains("gallery"));
        assertNotEquals("https://cdn.example.com/someone-else.png", full.getCoverImageUrl());
    }

    @Test
    void adminRejectsNonHttpsCover() {
        assertThrows(BadRequestException.class, () -> admin.setVariantCover(1L, "http://cdn.example.com/ic.png"));
        assertThrows(BadRequestException.class, () -> admin.setVariantCover(1L, ""));
        verify(variants, never()).save(any());
    }

    @Test
    void removingGalleryImageAlsoClearsTheStyleCoverUsingIt() {
        images.updateImages(1L, List.of(BORDER_COVER));
        assertNull(full.getCoverImageUrl());
        assertEquals(BORDER_COVER, border.getCoverImageUrl());
        verify(variants).save(full);
    }

    @Test
    void keepingGalleryImagePreservesTheStyleCover() {
        images.updateImages(1L, List.of(FULL_COVER, BORDER_COVER));
        assertEquals(FULL_COVER, full.getCoverImageUrl());
        assertEquals(BORDER_COVER, border.getCoverImageUrl());
        verify(variants, never()).save(any());
    }
}
