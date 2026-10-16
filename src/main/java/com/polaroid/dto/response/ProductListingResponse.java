package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListingResponse {
    /** Backend-generated product identity (BIGINT). */
    private Long id;
    /** Stable human slug used in URLs, e.g. "ic", "2r", "4r". */
    private String code;
    private String name;
    private String displayName;
    private BigDecimal width;
    private BigDecimal height;
    private ProductDimensionsResponse dimensions;
    private BigDecimal price;
    private String description;
    private String shortDescription;
    private String fullDescription;
    private List<String> images;
    private String image;
    private Boolean popular;
    private String tag;
    private List<String> features;
    private String accentColor;
    private Map<String, String> specs;
    private Double rating;
    private Integer reviewCount;
    private List<PricingTierResponse> pricingTiers;
    private List<ProductVariantResponse> variants;
    private List<TikTokVideoResponse> tiktokVideos;
    private Boolean active;
    private Boolean available;
}
