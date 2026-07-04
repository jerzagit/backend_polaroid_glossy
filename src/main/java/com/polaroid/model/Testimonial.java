package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "testimonials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Testimonial extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "print_type", nullable = false)
    private String printType;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "text_my", columnDefinition = "TEXT")
    private String textMy;

    @Column(name = "print_type_my")
    private String printTypeMy;

    @Builder.Default
    private Integer rating = 5;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
