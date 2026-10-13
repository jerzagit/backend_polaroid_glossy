package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.response.PricingTierResponse;
import com.polaroid.dto.response.ProductListingResponse;
import com.polaroid.dto.response.TikTokVideoResponse;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.PrintSize;
import com.polaroid.model.PrintSizePricingTier;
import com.polaroid.model.ProductMeta;
import com.polaroid.repository.PrintSizePricingTierRepository;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ProductMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final PrintSizeRepository printSizeRepository;
    private final ProductMetaRepository productMetaRepository;
    private final PrintSizePricingTierRepository pricingTierRepository;
    private final ObjectMapper objectMapper;

    public List<ProductListingResponse> getAllProducts() {
        List<PrintSize> activeSizes = printSizeRepository.findByIsActiveTrue();
        Map<String, ProductMeta> metaMap = productMetaRepository.findAll().stream()
                .collect(Collectors.toMap(ProductMeta::getId, m -> m));
        Map<String, List<PrintSizePricingTier>> tiersMap = pricingTierRepository.findAll().stream()
                .collect(Collectors.groupingBy(PrintSizePricingTier::getSizeId));

        return activeSizes.stream()
                .map(size -> buildProductListing(size, metaMap.get(size.getId()), tiersMap.getOrDefault(size.getId(), List.of())))
                .collect(Collectors.toList());
    }

    public ProductListingResponse getProductById(String id) {
        PrintSize printSize = printSizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        ProductMeta meta = productMetaRepository.findById(id).orElse(null);
        List<PrintSizePricingTier> tiers = pricingTierRepository.findBySizeIdOrderByQuantityAsc(id);
        return buildProductListing(printSize, meta, tiers);
    }

    private ProductListingResponse buildProductListing(PrintSize size, ProductMeta meta, List<PrintSizePricingTier> tiers) {
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

        return ProductListingResponse.builder()
                .id(size.getId())
                .name(size.getName())
                .displayName(size.getDisplayName())
                .width(size.getWidth())
                .height(size.getHeight())
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
                .rating(meta != null ? meta.getRating() : null)
                .reviewCount(meta != null ? meta.getReviewCount() : 0)
                .pricingTiers(pricingTiers)
                .tiktokVideos(tiktokVideos)
                .available(true)
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
