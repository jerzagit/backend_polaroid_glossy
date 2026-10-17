package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @Column(name = "size_id")
    private String sizeId;
    
    @Column(name = "size_name")
    private String sizeName;

    /* Purchase-time snapshot of size -> style -> package selection.
       Intentionally not linked to catalog rows: later price edits or
       disabled packages must never alter historical orders. */
    @Column(name = "variant_id")
    private String variantId;

    @Column(name = "variant_label")
    private String variantLabel;

    private String style;

    @Column(name = "package_id")
    private String packageId;

    /** Prints per package at purchase time (e.g. 10 for the "10 pcs" package). */
    @Column(name = "package_quantity")
    private Integer packageQuantity;

    /** Whole-package price at purchase time (e.g. 6.00 for IC Full 10 pcs). */
    @Column(name = "package_price", precision = 10, scale = 2)
    private BigDecimal packagePrice;

    /** How many packages were purchased (e.g. 2 x "10 pcs" = 20 prints). */
    @Column(name = "package_count")
    private Integer packageCount;

    /** Total prints across all purchased packages. */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "expected_image_count")
    private Integer expectedImageCount;
    
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;
    
    @Column(columnDefinition = "TEXT")
    private String images;
    
    @Column(name = "custom_texts", columnDefinition = "TEXT")
    private String customTexts;
    
    @Column(name = "s3_keys", columnDefinition = "TEXT")
    private String s3Keys;

    @Column(name = "strip_design", columnDefinition = "TEXT")
    private String stripDesign;
}
