package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "product_packages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPackage extends Auditable {

    /** Stable identity ({variantId}-{quantity}); never rewritten on price edits. */
    @Id
    private String id;

    @Column(name = "variant_id", nullable = false)
    private String variantId;

    @Column(nullable = false)
    private Integer quantity;

    /** Total price of the whole package in MYR (not per print). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "MYR";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
