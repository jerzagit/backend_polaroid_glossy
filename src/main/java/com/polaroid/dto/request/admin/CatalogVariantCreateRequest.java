package com.polaroid.dto.request.admin;

import com.polaroid.model.enums.ProductStyle;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Adds a FULL or BORDER style variant to a size product. */
@Data
public class CatalogVariantCreateRequest {
    @NotNull(message = "Style is required")
    private ProductStyle style;

    /** Defaults to "Full" / "Border (Polaroid)" when omitted. */
    private String label;
    private Integer sortOrder;
    private Boolean isActive = true;
}
