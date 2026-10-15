package com.polaroid.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** Creates a size product (e.g. IC, 2R, 3R) in the seller catalog. */
@Data
public class CatalogProductCreateRequest {
    @NotBlank(message = "ID is required")
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,48}", message = "ID must be lowercase letters, digits, and dashes")
    private String id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private BigDecimal width;
    private BigDecimal height;
    private String description;
    private String tag;
    private Boolean isActive = true;
}
