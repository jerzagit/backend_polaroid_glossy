package com.polaroid.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "print_size_pricing_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintSizePricingTier {

    @Id
    private String id;

    @Column(name = "size_id", nullable = false)
    private String sizeId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "regular_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal regularPrice;

    @Column(name = "discounted_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal discountedPrice;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
