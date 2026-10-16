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
    private List<ProductPackageResponse> packages;
}
