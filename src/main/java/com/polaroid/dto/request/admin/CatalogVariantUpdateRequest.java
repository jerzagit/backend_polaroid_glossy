package com.polaroid.dto.request.admin;

import lombok.Data;

/** Partial update of a style variant; null fields are left unchanged. */
@Data
public class CatalogVariantUpdateRequest {
    private String label;
    private Boolean isActive;
    private Integer sortOrder;
}
