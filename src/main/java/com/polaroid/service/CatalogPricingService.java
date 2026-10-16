package com.polaroid.service;

import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.PriceConflictException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.PrintSize;
import com.polaroid.model.Product;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductRepository;
import com.polaroid.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Single source of truth for Size -> Style -> Package pricing.
 * Checkout and payment both resolve prices from the database here;
 * clients never supply payable amounts.
 */
@Service
@RequiredArgsConstructor
public class CatalogPricingService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductPackageRepository packageRepository;
    private final PrintSizeRepository printSizeRepository;

    /** A fully validated, currently purchasable selection. */
    public record ResolvedPackage(Product product, ProductVariant variant, ProductPackage pkg) {
        public BigDecimal totalPrice(int packageCount) {
            return pkg.getPrice().multiply(BigDecimal.valueOf(packageCount));
        }
    }

    /**
     * Validates that the package belongs to the selected variant and product and
     * that the product, variant, and package are all active. {@code productId}
     * and {@code variantId} may be null when resolving from an order snapshot
     * (the package already pins the variant).
     */
    @Transactional(readOnly = true)
    public ResolvedPackage resolveActivePackage(Long productId, Long variantId, Long packageId) {
        if (packageId == null) {
            throw new BadRequestException("packageId is required");
        }

        ProductPackage pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new BadRequestException("Package not found or no longer available: " + packageId));
        ProductVariant variant = variantRepository.findById(pkg.getVariantId())
                .orElseThrow(() -> new BadRequestException("Style not found for package: " + packageId));

        if (variantId != null && !variantId.equals(variant.getId())) {
            throw new BadRequestException("Package " + packageId + " does not belong to style " + variantId);
        }
        if (productId != null && !productId.equals(variant.getProductId())) {
            throw new BadRequestException("Package " + packageId + " does not belong to product " + productId);
        }

        Product product = productRepository.findById(variant.getProductId())
                .orElseThrow(() -> new BadRequestException("Product not found for package: " + packageId));

        if (!isActive(product.getIsActive()) || !isActive(variant.getIsActive()) || !isActive(pkg.getIsActive())) {
            throw new BadRequestException("Selected package is not available: " + packageId);
        }

        return new ResolvedPackage(product, variant, pkg);
    }

    /**
     * Rejects an order (at payment time) whose snapshot no longer matches the
     * catalog: unavailable packages, or prices the customer has not seen.
     * Throws {@link PriceConflictException} carrying the current payable total.
     */
    @Transactional(readOnly = true)
    public void assertOrderPricingCurrent(Order order) {
        BigDecimal currentSubtotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            if (item.getPackageId() != null) {
                Long pkgId = parseLong(item.getPackageId());
                ProductPackage pkg = pkgId == null ? null : packageRepository.findById(pkgId).orElse(null);
                ProductVariant variant = pkg == null ? null : variantRepository.findById(pkg.getVariantId()).orElse(null);
                Product product = variant == null ? null : productRepository.findById(variant.getProductId()).orElse(null);

                boolean available = pkg != null && variant != null && product != null
                        && isActive(product.getIsActive()) && isActive(variant.getIsActive()) && isActive(pkg.getIsActive());
                if (!available) {
                    throw new PriceConflictException(
                            PriceConflictException.CODE_UNAVAILABLE,
                            "A selected package is no longer available. Please review your order.",
                            null);
                }
                if (pkg.getPrice().compareTo(item.getPackagePrice() == null ? item.getUnitPrice() : item.getPackagePrice()) != 0) {
                    throw new PriceConflictException(
                            PriceConflictException.CODE_PRICE_CHANGED,
                            "Pricing has changed since you selected your package. Please review the updated total.",
                            currentSubtotalFor(order).add(order.getShipping()));
                }
                int packageCount = item.getPackageCount() != null ? item.getPackageCount() : 1;
                currentSubtotal = currentSubtotal.add(pkg.getPrice().multiply(BigDecimal.valueOf(packageCount)));
            } else {
                PrintSize product = item.getSizeId() == null ? null : printSizeRepository.findById(item.getSizeId()).orElse(null);
                if (product == null || !isActive(product.getIsActive())) {
                    throw new PriceConflictException(
                            PriceConflictException.CODE_UNAVAILABLE,
                            "A selected product is no longer available. Please review your order.",
                            null);
                }
                if (product.getPrice().compareTo(item.getUnitPrice()) != 0) {
                    throw new PriceConflictException(
                            PriceConflictException.CODE_PRICE_CHANGED,
                            "Pricing has changed since you placed your order. Please review the updated total.",
                            currentSubtotalFor(order).add(order.getShipping()));
                }
                currentSubtotal = currentSubtotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
    }

    /** Recomputes the order's subtotal/total from current catalog prices,
     *  keeping each item's package count. Used by the price-reconfirm step. */
    @Transactional(readOnly = true)
    public BigDecimal resyncOrderItemPrices(Order order) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            if (item.getPackageId() != null) {
                ResolvedPackage resolved = resolveActivePackage(
                        null, parseLong(item.getVariantId()), parseLong(item.getPackageId()));
                int packageCount = item.getPackageCount() != null ? item.getPackageCount() : 1;
                item.setPackageQuantity(resolved.pkg().getQuantity());
                item.setPackagePrice(resolved.pkg().getPrice());
                item.setUnitPrice(resolved.pkg().getPrice());
                item.setTotalPrice(resolved.totalPrice(packageCount));
                item.setQuantity(resolved.pkg().getQuantity() * packageCount);
                subtotal = subtotal.add(item.getTotalPrice());
            } else {
                subtotal = subtotal.add(item.getTotalPrice());
            }
        }
        return subtotal;
    }

    private BigDecimal currentSubtotalFor(Order order) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            if (item.getPackageId() != null) {
                int packageCount = item.getPackageCount() != null ? item.getPackageCount() : 1;
                Long pkgId = parseLong(item.getPackageId());
                ProductPackage pkg = pkgId == null ? null : packageRepository.findById(pkgId).orElse(null);
                if (pkg != null) {
                    subtotal = subtotal.add(pkg.getPrice().multiply(BigDecimal.valueOf(packageCount)));
                    continue;
                }
            }
            subtotal = subtotal.add(item.getTotalPrice());
        }
        return subtotal;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isActive(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
