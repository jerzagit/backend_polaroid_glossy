package com.polaroid.model;

import com.polaroid.model.base.Auditable;
import com.polaroid.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    private String name;
    
    private String phone;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CUSTOMER;
    
    @Column(name = "affiliate_code", unique = true)
    private String affiliateCode;
    
    @Column(name = "referred_by")
    private UUID referredBy;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
