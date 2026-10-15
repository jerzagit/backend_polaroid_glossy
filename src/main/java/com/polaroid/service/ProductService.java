package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.PricingTierResponse;
import com.polaroid.dto.response.ProductDimensionsResponse;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.dto.response.ProductPackageResponse;
import com.polaroid.dto.response.ProductVariantResponse;
import com.polaroid.dto.response.TikTokVideoResponse;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.PrintSize;
import com.polaroid.model.PrintSizePricingTier;
import com.polaroid.model.ProductMeta;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizePricingTierRepository;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductMetaRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * Legacy product ids that now resolve to a style variant of a canonical
     * product, so existing links and carts keep working after grouping
     * Size -> Style -> Package under one product.
     */
    private static final Map<String, LegacyAlias> LEGACY_ALIASES = Map.of(
            "ic-border", new LegacyAlias("ic", ProductStyle.BORDER));

    private record LegacyAlias(String canonicalProductId, ProductStyle style) {}

    private final PrintSizeRepository printSizeRepository;
    private final ProductMetaRepository productMetaRepository;
    private final PrintSizePricingTierRepository pricingTierRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductPackageRepository packageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ProductListingResponse> getAllProducts() {
        return buildAllProducts(false);
    }

    /** Includes inactive products, variants, and packages for the seller dashboard. */
    @Transactional(readOnly = true)
    public List<ProductListingResponse> getAllProductsForAdmin() {
        return buildAllProducts(true);
    }

    @Transactional(readOnly = true)
    public ProductListingResponse getProductById(String id) {
        return buildProductById(id, false);
    }

    @Transactional(readOnly = true)
    public ProductListingResponse getProductForAdmin(String id) {
        return buildProductById(id, true);
    }

    private List<ProductListingResponse> buildAllProducts(boolean includeInactive) {
        List<PrintSize> sizes = includeInactive
                ? printSizeRepository.findAll()
                : printSizeRepository.findByIsActiveTrue();
        Map<String, ProductMeta> metaMap = productMetaRepository.findAll().stream()
                .collect(Collectors.toMap(ProductMeta::getId, m -> m));
        Map<String, List<PrintSizePricingTier>> tiersMap = pricingTierRepository.findAll().stream()
                .collect(Collectors.groupingBy(PrintSizePricingTier::getSizeId));

        return sizes.stream()
                .map(size -> buildProductListing(
                        size,
                        metaMap.get(size.getId()),
                        tiersMap.getOrDefault(size.getId(), List.of()),
                        variantsFor(size.getId(), null, includeInactive),
                        includeInactive))
                .collect(Collectors.toList());
    }

    private ProductListingResponse buildProductById(String id, boolean includeInactive) {
        String normalizedId = id.trim().toLowerCase();
        PrintSize printSize = printSizeRepository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        ProductMeta meta = productMetaRepository.findById(normalizedId).orElse(null);
        List<PrintSizePricingTier> tiers = pricingTierRepository.findBySizeIdOrderByQuantityAsc(normalizedId);

        // Legacy aliases keep their own product identity for existing links,
        // but variants and prices come from the canonical product so there is
        // a single source of truth per style.
        LegacyAlias alias = LEGACY_ALIASES.get(normalizedId);
        String variantSourceProductId = printSize.getId();
        ProductStyle styleFilter = null;
        if (alias != null) {
            ProductStyle aliasStyle = alias.style();
            printSizeRepository.findById(alias.canonicalProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + alias.canonicalProductId()));
            ProductMeta canonicalMeta = productMetaRepository.findById(alias.canonicalProductId()).orElse(null);
            if (canonicalMeta != null) meta = canonicalMeta;
            tiers = pricingTierRepository.findBySizeIdOrderByQuantityAsc(alias.canonicalProductId());
            variantSourceProductId = alias.canonicalProductId();
            styleFilter = aliasStyle;
        }

        return buildProductListing(
                printSize,
                meta,
                tiers,
                variantsFor(variantSourceProductId, styleFilter, includeInactive),
                includeInactive);
    }

    /** Variants of a product; when {@code styleFilter} is set only that style is returned
     *  (used by legacy aliases such as ic-border -> ic BORDER). */
    private List<ProductVariantResponse> variantsFor(String productId, ProductStyle styleFilter, boolean includeInactive) {
        List<ProductVariant> variants = variantRepository.findByProductIdOrderBySortOrderAsc(productId).stream()
                .filter(v -> styleFilter == null || v.getStyle() == styleFilter)
                .filter(v -> includeInactive || Boolean.TRUE.equals(v.getIsActive()))
                .sorted(Comparator.comparing(ProductVariant::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductVariant::getId))
                .collect(Collectors.toList());

        return variants.stream()
                .map(v -> ProductVariantResponse.builder()
                        .id(v.getId())
                        .style(v.getStyle())
                        .label(v.getLabel())
                        .active(Boolean.TRUE.equals(v.getIsActive()))
                        .packages(packagesFor(v.getId(), includeInactive))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductPackageResponse> packagesFor(String variantId, boolean includeInactive) {
        return packageRepository.findByVariantIdOrderBySortOrderAsc(variantId).stream()
                .filter(p -> includeInactive || Boolean.TRUE.equals(p.getIsActive()))
                .sorted(Comparator.comparing(ProductPackage::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductPackage::getQuantity))
                .map(p -> ProductPackageResponse.builder()
                        .id(p.getId())
                        .quantity(p.getQuantity())
                        .price(p.getPrice().toPlainString())
                        .currency(p.getCurrency())
                        .active(Boolean.TRUE.equals(p.getIsActive()))
                        .build())
                .collect(Collectors.toList());
    }

    private ProductListingResponse buildProductListing(PrintSize size, ProductMeta meta, List<PrintSizePricingTier> tiers,
                                                       List<ProductVariantResponse> variants, boolean includeInactive) {
        List<String> images = parseJsonArray(meta != null ? meta.getImages() : null);
        List<String> features = parseJsonArray(meta != null ? meta.getFeatures() : null);
        List<TikTokVideoResponse> tiktokVideos = parseTikTokVideos(meta != null ? meta.getTiktokVideos() : null);
        List<PricingTierResponse> pricingTiers = tiers.stream()
                .map(t -> PricingTierResponse.builder()
                        .quantity(t.getQuantity())
                        .regularPrice(t.getRegularPrice())
                        .discountedPrice(t.getDiscountedPrice())
                        .build())
                .collect(Collectors.toList());

        Map<String, String> specs = buildSpecs(size);
        boolean active = Boolean.TRUE.equals(size.getIsActive());

        return ProductListingResponse.builder()
                .id(size.getId())
                .name(size.getName())
                .displayName(size.getDisplayName())
                .width(size.getWidth())
                .height(size.getHeight())
                .dimensions(ProductDimensionsResponse.builder()
                        .width(size.getWidth())
                        .height(size.getHeight())
                        .unit("cm")
                        .build())
                .price(size.getPrice())
                .description(size.getDescription())
                .shortDescription(meta != null ? meta.getShortDescription() : null)
                .fullDescription(meta != null ? meta.getFullDescription() : null)
                .images(images)
                .image(images.isEmpty() ? null : images.get(0))
                .popular(meta != null && Boolean.TRUE.equals(meta.getPopular()))
                .tag(meta != null ? meta.getTag() : size.getTag())
                .features(features)
                .accentColor(meta != null ? meta.getAccentColor() : null)
                .specs(specs)
                .rating(meta != null && meta.getRating() != null ? meta.getRating().doubleValue() : null)
                .reviewCount(meta != null ? meta.getReviewCount() : 0)
                .pricingTiers(pricingTiers)
                .variants(variants)
                .tiktokVideos(tiktokVideos)
                .active(active)
                .available(active)
                .build();
    }

    private Map<String, String> buildSpecs(PrintSize size) {
        Map<String, String> specs = new LinkedHashMap<>();
        if (size.getWidth() != null && size.getHeight() != null) {
            specs.put("dimensions", size.getWidth() + " x " + size.getHeight() + " cm");
        }
        specs.put("paper", "Glossy Photo Paper");
        specs.put("finish", "High Gloss");
        specs.put("printMethod", "Dye Sublimation");
        specs.put("processingTime", "1-2 business days");
        specs.put("minQty", "10");
        return specs;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TikTokVideoResponse> parseTikTokVideos(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
