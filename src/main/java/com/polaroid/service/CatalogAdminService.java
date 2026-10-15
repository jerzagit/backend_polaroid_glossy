package com.polaroid.service;

import com.polaroid.dto.request.admin.CatalogPackageCreateRequest;
import com.polaroid.dto.request.admin.CatalogPackageUpdateRequest;
import com.polaroid.dto.request.admin.CatalogProductCreateRequest;
import com.polaroid.dto.request.admin.CatalogProductUpdateRequest;
import com.polaroid.dto.request.admin.CatalogVariantCreateRequest;
import com.polaroid.dto.request.admin.CatalogVariantUpdateRequest;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.PrintSize;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Seller-managed Size -> Style -> Package configuration.
 * Who changed a price and when is captured by the audit columns
 * (created_by / updated_by / created_at / updated_at) via JPA auditing.
 */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private static final Map<ProductStyle, String> DEFAULT_LABELS = Map.of(
            ProductStyle.FULL, "Full",
            ProductStyle.BORDER, "Border (Polaroid)");

    private final PrintSizeRepository printSizeRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductPackageRepository packageRepository;
    private final ProductService productService;

    /* ---------- products (sizes) ---------- */

    @Transactional(readOnly = true)
    public List<ProductListingResponse> getAllProducts() {
        return productService.getAllProductsForAdmin();
    }

    @Transactional(readOnly = true)
    public ProductListingResponse getProduct(String id) {
        return productService.getProductForAdmin(normalize(id));
    }

    @Transactional
    public ProductListingResponse createProduct(CatalogProductCreateRequest request) {
        String id = normalize(request.getId());
        if (printSizeRepository.existsById(id)) {
            throw new BadRequestException("Size already exists: " + id);
        }
        validateDimensions(request.getWidth(), request.getHeight());

        PrintSize size = PrintSize.builder()
                .id(id)
                .name(request.getName().trim())
                .displayName(request.getDisplayName().trim())
                .width(request.getWidth())
                .height(request.getHeight())
                // Base per-print price kept for legacy carts; packages are the
                // source of truth for new purchases.
                .price(BigDecimal.ZERO)
                .description(request.getDescription())
                .tag(request.getTag())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        printSizeRepository.save(size);
        return productService.getProductForAdmin(id);
    }

    @Transactional
    public ProductListingResponse updateProduct(String id, CatalogProductUpdateRequest request) {
        PrintSize size = findSize(id);
        if (request.getName() != null) size.setName(request.getName().trim());
        if (request.getDisplayName() != null) size.setDisplayName(request.getDisplayName().trim());
        if (request.getWidth() != null) size.setWidth(request.getWidth());
        if (request.getHeight() != null) size.setHeight(request.getHeight());
        if (request.getDescription() != null) size.setDescription(request.getDescription());
        if (request.getTag() != null) size.setTag(request.getTag());
        if (request.getIsActive() != null) size.setIsActive(request.getIsActive());
        validateDimensions(size.getWidth(), size.getHeight());
        printSizeRepository.save(size);
        return productService.getProductForAdmin(size.getId());
    }

    /* ---------- variants (styles) ---------- */

    @Transactional
    public ProductListingResponse createVariant(String productId, CatalogVariantCreateRequest request) {
        PrintSize size = findSize(productId);
        ProductStyle style = request.getStyle();
        if (variantRepository.existsByProductIdAndStyle(size.getId(), style)) {
            throw new BadRequestException("Style " + style + " already exists for size " + size.getId());
        }

        ProductVariant variant = ProductVariant.builder()
                .id(size.getId() + "-" + style.name().toLowerCase())
                .productId(size.getId())
                .style(style)
                .label(request.getLabel() != null && !request.getLabel().isBlank()
                        ? request.getLabel().trim()
                        : DEFAULT_LABELS.get(style))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        variantRepository.save(variant);
        return productService.getProductForAdmin(size.getId());
    }

    @Transactional
    public ProductListingResponse updateVariant(String variantId, CatalogVariantUpdateRequest request) {
        ProductVariant variant = findVariant(variantId);
        if (request.getLabel() != null && !request.getLabel().isBlank()) variant.setLabel(request.getLabel().trim());
        if (request.getIsActive() != null) variant.setIsActive(request.getIsActive());
        if (request.getSortOrder() != null) variant.setSortOrder(request.getSortOrder());
        variantRepository.save(variant);
        return productService.getProductForAdmin(variant.getProductId());
    }

    /* ---------- packages ---------- */

    @Transactional
    public ProductListingResponse createPackage(String variantId, CatalogPackageCreateRequest request) {
        ProductVariant variant = findVariant(variantId);
        if (request.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be a positive number of prints");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Price must be a positive package total");
        }
        if (packageRepository.existsByVariantIdAndQuantity(variant.getId(), request.getQuantity())) {
            throw new BadRequestException(
                    "Package with quantity " + request.getQuantity() + " already exists for variant " + variant.getId());
        }

        ProductPackage pkg = ProductPackage.builder()
                .id(variant.getId() + "-" + request.getQuantity())
                .variantId(variant.getId())
                .quantity(request.getQuantity())
                .price(request.getPrice().setScale(2))
                .currency("MYR")
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        packageRepository.save(pkg);
        return productService.getProductForAdmin(variant.getProductId());
    }

    @Transactional
    public ProductListingResponse updatePackage(String packageId, CatalogPackageUpdateRequest request) {
        ProductPackage pkg = findPackage(packageId);
        if (request.getPrice() != null) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Price must be a positive package total");
            }
            pkg.setPrice(request.getPrice().setScale(2));
        }
        if (request.getIsActive() != null) pkg.setIsActive(request.getIsActive());
        if (request.getSortOrder() != null) pkg.setSortOrder(request.getSortOrder());
        packageRepository.save(pkg);

        ProductVariant variant = findVariant(pkg.getVariantId());
        return productService.getProductForAdmin(variant.getProductId());
    }

    /* ---------- helpers ---------- */

    private PrintSize findSize(String id) {
        return printSizeRepository.findById(normalize(id))
                .orElseThrow(() -> new ResourceNotFoundException("Size not found: " + id));
    }

    private ProductVariant findVariant(String id) {
        return variantRepository.findById(normalize(id))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + id));
    }

    private ProductPackage findPackage(String id) {
        return packageRepository.findById(normalize(id))
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + id));
    }

    private void validateDimensions(BigDecimal width, BigDecimal height) {
        if (width != null && width.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Width must be positive");
        }
        if (height != null && height.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Height must be positive");
        }
    }

    private String normalize(String id) {
        return id == null ? null : id.trim().toLowerCase();
    }
}
