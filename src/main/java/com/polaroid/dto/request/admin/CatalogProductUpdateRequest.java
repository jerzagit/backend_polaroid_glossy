package com.polaroid.dto.request.admin;

import lombok.Data;

import java.math.BigDecimal;

/** Partial update of a size product; null fields are left unchanged. */
@Data
public class CatalogProductUpdateRequest {
    private String name;
    private String displayName;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal basePrice;
    private String description;
    private String tag;
    private Boolean isActive;
}
