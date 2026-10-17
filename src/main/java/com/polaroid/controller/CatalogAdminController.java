package com.polaroid.controller;

import com.polaroid.dto.request.admin.CatalogPackageCreateRequest;
import com.polaroid.dto.request.admin.CatalogPackageUpdateRequest;
import com.polaroid.dto.request.admin.CatalogProductCreateRequest;
import com.polaroid.dto.request.admin.CatalogProductUpdateRequest;
import com.polaroid.dto.request.admin.CatalogVariantCoverRequest;
import com.polaroid.dto.request.admin.CatalogVariantCreateRequest;
import com.polaroid.dto.request.admin.CatalogVariantUpdateRequest;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.service.CatalogAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Seller dashboard management of the Size -> Style -> Package catalog.
 * Reads are available to ADMIN and MARKETING; configuration changes
 * (sizes, styles, packages, prices) are restricted to ADMIN.
 *
 * Product / variant / package ids are database-generated, so every path
 * variable is a numeric id.
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class CatalogAdminController {

    private final CatalogAdminService catalogAdminService;

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<List<ProductListingResponse>> getAllProducts() {
        return ResponseEntity.ok(catalogAdminService.getAllProducts());
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public ResponseEntity<ProductListingResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(catalogAdminService.getProduct(id));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> createProduct(
            @Valid @RequestBody CatalogProductCreateRequest request) {
        return ResponseEntity.ok(catalogAdminService.createProduct(request));
    }

    @PatchMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CatalogProductUpdateRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateProduct(id, request));
    }

    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CatalogVariantCreateRequest request) {
        return ResponseEntity.ok(catalogAdminService.createVariant(productId, request));
    }

    @PatchMapping("/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> updateVariant(
            @PathVariable Long variantId,
            @Valid @RequestBody CatalogVariantUpdateRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateVariant(variantId, request));
    }

    /** Set (or clear, by sending null) the cover image of one style variant. */
    @PutMapping("/variants/{variantId}/cover")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> setVariantCover(
            @PathVariable Long variantId,
            @RequestBody CatalogVariantCoverRequest request) {
        return ResponseEntity.ok(catalogAdminService.setVariantCover(variantId, request.getCoverImageUrl()));
    }

    @PostMapping("/variants/{variantId}/packages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> createPackage(
            @PathVariable Long variantId,
            @Valid @RequestBody CatalogPackageCreateRequest request) {
        return ResponseEntity.ok(catalogAdminService.createPackage(variantId, request));
    }

    @PatchMapping("/packages/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductListingResponse> updatePackage(
            @PathVariable Long packageId,
            @Valid @RequestBody CatalogPackageUpdateRequest request) {
        return ResponseEntity.ok(catalogAdminService.updatePackage(packageId, request));
    }
}
