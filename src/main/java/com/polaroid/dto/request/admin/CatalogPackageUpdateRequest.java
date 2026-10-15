package com.polaroid.dto.request.admin;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/** Partial update of a package. Quantity is immutable (it defines the
 *  package's stable identity); sellers change price, order, and status. */
@Data
public class CatalogPackageUpdateRequest {
    @DecimalMin(value = "0.01", message = "Price must be a positive package total")
    private BigDecimal price;
    private Boolean isActive;
    private Integer sortOrder;
}
