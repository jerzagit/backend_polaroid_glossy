package com.polaroid.dto.response;

import com.polaroid.model.enums.ProductStyle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
    private Long id;
    private ProductStyle style;
    private String label;
    private Boolean active;
    /** Absolute HTTPS cover for this style, or null when it has none of its own. */
    private String coverImageUrl;
    private List<ProductPackageResponse> packages;
}
