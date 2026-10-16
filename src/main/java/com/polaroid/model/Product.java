package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * A single print size (product). One product per size: styles (Full / Border)
 * and their packages hang beneath it, so a customer never sees a size listed
 * twice. {@code code} is the stable human slug (ic, 2r, 3r, ...) used in URLs
 * and legacy aliases; {@code id} is the backend-generated identity.
 */
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(precision = 5, scale = 2)
    private BigDecimal width;

    @Column(precision = 5, scale = 2)
    private BigDecimal height;

    /** Legacy per-print price kept for old carts; packages are authoritative. */
    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String tag;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
