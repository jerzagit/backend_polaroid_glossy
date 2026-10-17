package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Product;
import com.polaroid.model.ProductMeta;
import com.polaroid.repository.*;
import com.polaroid.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {
    private ProductRepository products;
    private ProductMetaRepository metadata;
    private StorageService storage;
    private ProductService listings;
    private ProductImageService service;
    private ProductMeta meta;

    @BeforeEach
    void setup() {
        products = mock(ProductRepository.class);
        metadata = mock(ProductMetaRepository.class);
        storage = mock(StorageService.class);
        var product = Product.builder().id(1L).code("ic").isActive(true).build();
        when(products.findForImageUpdate(1L)).thenReturn(Optional.of(product));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(products.findByCode("ic")).thenReturn(Optional.of(product));
        meta = ProductMeta.builder().id("ic").images("[\"https://example.com/old.png\",\"https://example.com/new.png\"]").tag("Featured").build();
        when(metadata.findById("ic")).thenAnswer(invocation -> Optional.of(meta));
        var mapper = new ObjectMapper();
        listings = new ProductService(products, mock(ProductAliasRepository.class), metadata,
                mock(PrintSizePricingTierRepository.class), mock(ProductVariantRepository.class),
                mock(ProductPackageRepository.class), mapper);
        service = new ProductImageService(products, metadata, listings, storage, mapper, mock(ProductVariantRepository.class));
        ReflectionTestUtils.setField(service, "publicApiUrl", "https://api.example.com");
    }

    @Test
    void coverSelectionPersistsAndPublicCatalogReturnsItFirst() {
        var ordered = List.of("https://example.com/new.png", "https://example.com/old.png");
        var response = service.updateImages(1L, ordered);
        assertEquals(ordered, response.getImages());
        assertEquals(ordered.get(0), response.getImage());
        assertEquals(ordered.get(0), listings.getProductById("ic").getImage());
        assertEquals("Featured", meta.getTag());
        verify(metadata).saveAndFlush(meta);
    }

    @Test
    void removingCoverPromotesNextAndRemovingLastClearsCover() {
        assertEquals("https://example.com/new.png", service.updateImages(1L, List.of("https://example.com/new.png")).getImage());
        assertNull(service.updateImages(1L, List.of()).getImage());
        assertEquals(List.of(), listings.getProductById("ic").getImages());
    }

    @Test
    void firstImageCreatesMetadataWithoutLosingTheCover() {
        when(metadata.findById("ic")).thenReturn(Optional.empty());
        when(metadata.saveAndFlush(any())).thenAnswer(invocation -> {
            meta = invocation.getArgument(0);
            when(metadata.findById("ic")).thenReturn(Optional.of(meta));
            return meta;
        });
        var response = service.updateImages(1L, List.of("https://example.com/first.png"));
        assertEquals("https://example.com/first.png", response.getImage());
        assertEquals(0, response.getReviewCount());
    }

    @Test
    void invalidAndDuplicateUrlsAreRejectedWithoutSaving() {
        for (String url : List.of("javascript:alert(1)", "data:image/png;base64,123", "http://external.test/x", "https://user:pass@example.com/x")) {
            assertThrows(BadRequestException.class, () -> service.updateImages(1L, List.of(url)));
        }
        assertThrows(BadRequestException.class, () -> service.updateImages(1L, Collections.nCopies(2, "https://example.com/x")));
        assertThrows(BadRequestException.class, () -> service.updateImages(1L, Collections.nCopies(13, "https://example.com/x")));
        verify(metadata, never()).saveAndFlush(any());
    }

    @Test
    void uploadAppendsWithoutReplacingCoverAndImageCanBeRead() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var result = service.upload(1L, new MockMultipartFile("file", "photo.png", "image/png", output.toByteArray()));
        assertEquals("https://example.com/old.png", result.getImage());
        String url = result.getImages().get(2);
        String name = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(url.startsWith("https://api.example.com/api/products/1/images/"));
        verify(storage).upload(eq("catalog/1/" + name), any(byte[].class), eq("image/png"));
        when(storage.download("catalog/1/" + name)).thenReturn(output.toByteArray());
        assertArrayEquals(output.toByteArray(), service.read(1L, name));
        service.updateImages(1L, List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.read(1L, name));
    }

    @Test
    void rejectsNonImagesAndOversizedUploadsAndUnknownStorageKeys() {
        assertThrows(BadRequestException.class, () -> service.upload(1L, new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        var oversized = mock(org.springframework.web.multipart.MultipartFile.class);
        when(oversized.getSize()).thenReturn(11L * 1024 * 1024);
        assertThrows(BadRequestException.class, () -> service.upload(1L, oversized));
        assertThrows(ResourceNotFoundException.class, () -> service.read(1L, "../../orders/private.jpg"));
        assertThrows(ResourceNotFoundException.class, () -> service.read(1L, UUID.randomUUID() + ".png"));
        verifyNoInteractions(storage);
    }
}
