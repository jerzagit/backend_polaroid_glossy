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
import com.polaroid.model.PrintSizePricingTier;
import com.polaroid.model.Product;
import com.polaroid.model.ProductAlias;
import com.polaroid.model.ProductMeta;
import com.polaroid.model.ProductPackage;
import com.polaroid.model.ProductVariant;
import com.polaroid.model.enums.ProductStyle;
import com.polaroid.repository.PrintSizePricingTierRepository;
import com.polaroid.repository.ProductAliasRepository;
import com.polaroid.repository.ProductMetaRepository;
import com.polaroid.repository.ProductPackageRepository;
import com.polaroid.repository.ProductRepository;
import com.polaroid.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductAliasRepository productAliasRepository;
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

    /**
     * Storefront lookup by numeric id, by product code (ic, 2r, ...), or by a
     * legacy alias (ic-border, 2r-no-border, ...). Alias links resolve to the
     * canonical product filtered to the aliased style.
     */
    @Transactional(readOnly = true)
    public ProductListingResponse getProductById(String idOrCode) {
        String key = idOrCode == null ? "" : idOrCode.trim().toLowerCase();
        if (key.isEmpty()) {
            throw new ResourceNotFoundException("Product not found: " + idOrCode);
        }

        Product product;
        ProductStyle styleFilter = null;
        if (key.matches("\\d+")) {
            product = productRepository.findById(Long.valueOf(key))
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + idOrCode));
        } else {
            Optional<Product> byCode = productRepository.findByCode(key);
            if (byCode.isPresent()) {
                product = byCode.get();
            } else {
                ProductAlias alias = productAliasRepository.findById(key)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + idOrCode));
                styleFilter = alias.getStyle();
                product = productRepository.findById(alias.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + idOrCode));
            }
        }
        return buildListing(product, styleFilter, false);
    }

    @Transactional(readOnly = true)
    public ProductListingResponse getProductForAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return buildListing(product, null, true);
    }

    private List<ProductListingResponse> buildAllProducts(boolean includeInactive) {
        List<Product> products = includeInactive
                ? productRepository.findAllByOrderBySortOrderAscIdAsc()
                : productRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc();
        return products.stream()
                .map(product -> buildListing(product, null, includeInactive))
                .collect(Collectors.toList());
    }

    private ProductListingResponse buildListing(Product product, ProductStyle styleFilter, boolean includeInactive) {
        ProductMeta meta = productMetaRepository.findById(product.getCode()).orElse(null);
        List<PrintSizePricingTier> tiers = pricingTierRepository
                .findBySizeIdOrderByQuantityAsc(product.getCode());
        List<ProductVariantResponse> variants = variantsFor(product.getId(), styleFilter, includeInactive);
        return buildProductListing(product, meta, tiers, variants);
    }

    /** Variants of a product; when {@code styleFilter} is set only that style is
     *  returned (used by legacy aliases such as ic-border -> IC BORDER). */
    private List<ProductVariantResponse> variantsFor(Long productId, ProductStyle styleFilter, boolean includeInactive) {
        return variantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .filter(v -> styleFilter == null || v.getStyle() == styleFilter)
                .filter(v -> includeInactive || Boolean.TRUE.equals(v.getIsActive()))
                .map(v -> ProductVariantResponse.builder()
                        .id(v.getId())
                        .style(v.getStyle())
                        .label(v.getLabel())
                        .active(Boolean.TRUE.equals(v.getIsActive()))
                        .packages(packagesFor(v.getId(), includeInactive))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductPackageResponse> packagesFor(Long variantId, boolean includeInactive) {
        return packageRepository.findByVariantIdOrderBySortOrderAscQuantityAsc(variantId).stream()
                .filter(p -> includeInactive || Boolean.TRUE.equals(p.getIsActive()))
                .map(p -> ProductPackageResponse.builder()
                        .id(p.getId())
                        .quantity(p.getQuantity())
                        .price(p.getPrice().toPlainString())
                        .currency(p.getCurrency())
                        .active(Boolean.TRUE.equals(p.getIsActive()))
                        .build())
                .collect(Collectors.toList());
    }

    private ProductListingResponse buildProductListing(Product product, ProductMeta meta, List<PrintSizePricingTier> tiers,
                                                       List<ProductVariantResponse> variants) {
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

        boolean active = Boolean.TRUE.equals(product.getIsActive());

        return ProductListingResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .displayName(product.getDisplayName())
                .width(product.getWidth())
                .height(product.getHeight())
                .dimensions(ProductDimensionsResponse.builder()
                        .width(product.getWidth())
                        .height(product.getHeight())
                        .unit("cm")
                        .build())
                .price(product.getBasePrice())
                .description(product.getDescription())
                .shortDescription(meta != null ? meta.getShortDescription() : null)
                .fullDescription(meta != null ? meta.getFullDescription() : null)
                .images(images)
                .image(images.isEmpty() ? null : images.get(0))
                .popular(meta != null && Boolean.TRUE.equals(meta.getPopular()))
                .tag(meta != null ? meta.getTag() : product.getTag())
                .features(features)
                .accentColor(meta != null ? meta.getAccentColor() : null)
                .specs(buildSpecs(product))
                .rating(meta != null && meta.getRating() != null ? meta.getRating().doubleValue() : null)
                .reviewCount(meta != null && meta.getReviewCount() != null ? meta.getReviewCount() : 0)
                .pricingTiers(pricingTiers)
                .variants(variants)
                .tiktokVideos(tiktokVideos)
                .active(active)
                .available(active)
                .build();
    }

    private Map<String, String> buildSpecs(Product product) {
        Map<String, String> specs = new LinkedHashMap<>();
        if (product.getWidth() != null && product.getHeight() != null) {
            specs.put("dimensions", product.getWidth() + " x " + product.getHeight() + " cm");
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
