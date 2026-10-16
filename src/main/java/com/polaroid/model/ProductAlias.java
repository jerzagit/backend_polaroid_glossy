package com.polaroid.model;

import com.polaroid.model.enums.ProductStyle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps a legacy product id (e.g. ic-border, 2r-no-border) to a style of a
 * canonical product, so existing links, carts, and bookmarks keep working
 * after the duplicate per-style products were merged into one entity.
 */
@Entity
@Table(name = "product_aliases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAlias {

    @Id
    @Column(name = "legacy_code")
    private String legacyCode;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** When set, the alias resolves to just this style (e.g. ic-border -> BORDER). */
    @Enumerated(EnumType.STRING)
    private ProductStyle style;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
