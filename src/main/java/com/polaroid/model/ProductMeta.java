package com.polaroid.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_meta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMeta {

    @Id
    private String id;

    private String tag;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(columnDefinition = "TEXT")
    private String images;

    @Column(columnDefinition = "TEXT")
    private String features;

    @Column(name = "tiktok_videos", columnDefinition = "TEXT")
    private String tiktokVideos;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "full_description", columnDefinition = "TEXT")
    private String fullDescription;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    private Boolean popular;
}
