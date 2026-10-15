package com.polaroid.dto.request.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Adds a package (quantity + whole-package price) to one style variant. */
@Data
public class CatalogPackageCreateRequest {
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be a positive number of prints")
    private Integer quantity;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be a positive package total")
    private BigDecimal price;

    private Integer sortOrder;
    private Boolean isActive = true;
}
