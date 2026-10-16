package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A purchasable package for one style variant.
 * {@code price} is the whole-package total, serialized as an exact decimal
 * string (e.g. "6.00") to avoid floating-point rounding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPackageResponse {
    private Long id;
    private Integer quantity;
    private String price;
    private String currency;
    private Boolean active;
}
