package com.polaroid.service;

import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.PriceConflictException;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.model.PrintSize;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductPackageRepository;
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

    private final PrintSizeRepository printSizeRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductPackageRepository packageRepository;

    /** A fully validated, currently purchasable selection. */
    public record ResolvedPackage(PrintSize product, ProductVariant variant, ProductPackage pkg) {
        public BigDecimal totalPrice(int packageCount) {
            return pkg.getPrice().multiply(BigDecimal.valueOf(packageCount));
        }
    }

    /**
     * Validates that the package belongs to the selected variant and size and
     * that the product, variant, and package are all active.
     */
    @Transactional(readOnly = true)
    public ResolvedPackage resolveActivePackage(String productId, String variantId, String packageId) {
        if (packageId == null || packageId.isBlank()) {
            throw new BadRequestException("packageId is required");
        }

        ProductPackage pkg = packageRepository.findById(packageId.trim())
                .orElseThrow(() -> new BadRequestException("Package not found or no longer available: " + packageId));
        ProductVariant variant = variantRepository.findById(pkg.getVariantId())
                .orElseThrow(() -> new BadRequestException("Variant not found for package: " + packageId));

        if (variantId != null && !variantId.isBlank() && !variantId.trim().equals(variant.getId())) {
            throw new BadRequestException("Package " + packageId + " does not belong to variant " + variantId);
        }
        if (productId != null && !productId.isBlank() && !productId.trim().equals(variant.getProductId())) {
            throw new BadRequestException("Package " + packageId + " does not belong to size " + productId);
        }

        PrintSize product = printSizeRepository.findById(variant.getProductId())
                .orElseThrow(() -> new BadRequestException("Size not found for package: " + packageId));

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
                ProductPackage pkg = packageRepository.findById(item.getPackageId()).orElse(null);
                ProductVariant variant = pkg == null ? null : variantRepository.findById(pkg.getVariantId()).orElse(null);
                PrintSize product = variant == null ? null : printSizeRepository.findById(variant.getProductId()).orElse(null);

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
                        item.getSizeId(), item.getVariantId(), item.getPackageId());
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
                ProductPackage pkg = packageRepository.findById(item.getPackageId()).orElse(null);
                if (pkg != null) {
                    subtotal = subtotal.add(pkg.getPrice().multiply(BigDecimal.valueOf(packageCount)));
                    continue;
                }
            }
            subtotal = subtotal.add(item.getTotalPrice());
        }
        return subtotal;
    }

    private boolean isActive(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
