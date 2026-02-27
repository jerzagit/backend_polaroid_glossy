package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "print_sizes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintSize extends Auditable {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal width;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal height;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
