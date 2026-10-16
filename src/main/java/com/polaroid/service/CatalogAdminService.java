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
import com.polaroid.model.Product;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductRepository;
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
 *
 * Products carry a database-generated id; the seller never supplies one.
 * A matching legacy print_sizes row is kept in step so the older per-size
 * endpoint and any legacy cart links stay coherent.
 */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private static final Map<ProductStyle, String> DEFAULT_LABELS = Map.of(
            ProductStyle.FULL, "Full",
            ProductStyle.BORDER, "Border (Polaroid)");

    private final ProductRepository productRepository;
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
    public ProductListingResponse getProduct(Long id) {
        return productService.getProductForAdmin(id);
    }

    @Transactional
    public ProductListingResponse createProduct(CatalogProductCreateRequest request) {
        String code = normalizeCode(request.getCode());
        if (productRepository.existsByCode(code)) {
            throw new BadRequestException("Size already exists: " + code);
        }
        if (printSizeRepository.existsById(code)) {
            throw new BadRequestException("Size already exists: " + code);
        }
        validateDimensions(request.getWidth(), request.getHeight());

        Product product = Product.builder()
                .code(code)
                .name(request.getName().trim())
                .displayName(request.getDisplayName().trim())
                .width(request.getWidth())
                .height(request.getHeight())
                .basePrice(request.getBasePrice())
                .description(request.getDescription())
                .tag(request.getTag())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .sortOrder(0)
                .build();
        product = productRepository.save(product);
        syncLegacyPrintSize(product);
        return productService.getProductForAdmin(product.getId());
    }

    @Transactional
    public ProductListingResponse updateProduct(Long id, CatalogProductUpdateRequest request) {
        Product product = findProduct(id);
        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getDisplayName() != null) product.setDisplayName(request.getDisplayName().trim());
        if (request.getWidth() != null) product.setWidth(request.getWidth());
        if (request.getHeight() != null) product.setHeight(request.getHeight());
        if (request.getBasePrice() != null) product.setBasePrice(request.getBasePrice());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getTag() != null) product.setTag(request.getTag());
        if (request.getIsActive() != null) product.setIsActive(request.getIsActive());
        validateDimensions(product.getWidth(), product.getHeight());
        productRepository.save(product);
        syncLegacyPrintSize(product);
        return productService.getProductForAdmin(product.getId());
    }

    /* ---------- variants (styles) ---------- */

    @Transactional
    public ProductListingResponse createVariant(Long productId, CatalogVariantCreateRequest request) {
        Product product = findProduct(productId);
        ProductStyle style = request.getStyle();
        if (variantRepository.existsByProductIdAndStyle(product.getId(), style)) {
            throw new BadRequestException("Style " + style + " already exists for size " + product.getCode());
        }

        ProductVariant variant = ProductVariant.builder()
                .productId(product.getId())
                .style(style)
                .label(request.getLabel() != null && !request.getLabel().isBlank()
                        ? request.getLabel().trim()
                        : DEFAULT_LABELS.get(style))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        variantRepository.save(variant);
        return productService.getProductForAdmin(product.getId());
    }

    @Transactional
    public ProductListingResponse updateVariant(Long variantId, CatalogVariantUpdateRequest request) {
        ProductVariant variant = findVariant(variantId);
        if (request.getLabel() != null && !request.getLabel().isBlank()) variant.setLabel(request.getLabel().trim());
        if (request.getIsActive() != null) variant.setIsActive(request.getIsActive());
        if (request.getSortOrder() != null) variant.setSortOrder(request.getSortOrder());
        variantRepository.save(variant);
        return productService.getProductForAdmin(variant.getProductId());
    }

    /* ---------- packages ---------- */

    @Transactional
    public ProductListingResponse createPackage(Long variantId, CatalogPackageCreateRequest request) {
        ProductVariant variant = findVariant(variantId);
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
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
    public ProductListingResponse updatePackage(Long packageId, CatalogPackageUpdateRequest request) {
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

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductVariant findVariant(Long id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + id));
    }

    private ProductPackage findPackage(Long id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + id));
    }

    /** Keeps the legacy print_sizes row aligned with the canonical product. */
    private void syncLegacyPrintSize(Product product) {
        PrintSize size = printSizeRepository.findById(product.getCode())
                .orElseGet(() -> PrintSize.builder()
                        .id(product.getCode())
                        .price(product.getBasePrice() != null ? product.getBasePrice() : BigDecimal.ZERO)
                        .build());
        size.setName(product.getName());
        size.setDisplayName(product.getDisplayName());
        size.setWidth(product.getWidth());
        size.setHeight(product.getHeight());
        size.setDescription(product.getDescription());
        size.setTag(product.getTag());
        size.setIsActive(product.getIsActive());
        if (product.getBasePrice() != null) {
            size.setPrice(product.getBasePrice());
        }
        printSizeRepository.save(size);
    }

    private void validateDimensions(BigDecimal width, BigDecimal height) {
        if (width != null && width.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Width must be positive");
        }
        if (height != null && height.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Height must be positive");
        }
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toLowerCase();
    }
}
