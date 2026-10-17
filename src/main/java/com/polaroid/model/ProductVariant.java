package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import com.polaroid.model.enums.ProductStyle;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A style (Full / Border) of one product. At most one variant per style per
 * product; each variant owns its own package quantities and prices.
 */
@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

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

    /**
     * Optional cover for this style, pointing at one of the product's gallery images.
     * Null means the storefront falls back to the product-level cover.
     */
    @Column(name = "cover_image_url")
    private String coverImageUrl;
}
