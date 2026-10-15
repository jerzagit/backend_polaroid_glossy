package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import com.polaroid.model.enums.ProductStyle;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends Auditable {

    /** Stable identity ({productId}-{style}); never rewritten on price edits. */
    @Id
    private String id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStyle style;

    /** Customer-facing style name, e.g. "Full" or "Border (Polaroid)". */
    @Column(nullable = false)
    private String label;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
