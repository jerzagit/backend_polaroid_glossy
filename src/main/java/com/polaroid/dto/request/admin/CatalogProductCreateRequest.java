package com.polaroid.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** Creates a size product (e.g. IC, 2R, 3R) in the seller catalog. The
 *  database generates the product id; {@code code} is the stable slug. */
@Data
public class CatalogProductCreateRequest {
    @NotBlank(message = "Code is required")
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,48}", message = "Code must be lowercase letters, digits, and dashes")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private BigDecimal width;
    private BigDecimal height;

    /** Optional legacy per-print price; packages are the source of truth. */
    private BigDecimal basePrice;

    private String description;
    private String tag;
    private Boolean isActive = true;
}
