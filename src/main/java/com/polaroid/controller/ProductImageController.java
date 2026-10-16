package com.polaroid.controller;

import com.polaroid.dto.request.admin.ProductImagesRequest;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.service.ProductImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ProductImageController {
    private final ProductImageService images;

    @PutMapping("/api/admin/catalog/products/{id}/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductListingResponse update(@PathVariable Long id, @Valid @RequestBody ProductImagesRequest request) {
        return images.updateImages(id, request.getImages());
    }

    @PostMapping(value = "/api/admin/catalog/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductListingResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return images.upload(id, file);
    }

    @GetMapping("/api/products/{id}/images/{name}")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable String name) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(images.read(id, name));
    }
}
