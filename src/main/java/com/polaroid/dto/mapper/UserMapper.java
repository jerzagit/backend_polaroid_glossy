package com.polaroid.dto.mapper;

import com.polaroid.dto.response.UserResponse;
import com.polaroid.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper implements EntityMapper<User, UserResponse> {
    
    @Override
    public UserResponse toDto(User entity) {
        if (entity == null) return null;
        
        return UserResponse.builder()
                .id(entity.getId().toString())
                .email(entity.getEmail())
                .name(entity.getName())
                .phone(entity.getPhone())
                .avatarUrl(entity.getAvatarUrl())
                .role(entity.getRole())
                .affiliateCode(entity.getAffiliateCode())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
    
    @Override
    public User toEntity(UserResponse dto) {
        if (dto == null) return null;
        
        return User.builder()
                .id(java.util.UUID.fromString(dto.getId()))
                .email(dto.getEmail())
                .name(dto.getName())
                .phone(dto.getPhone())
                .avatarUrl(dto.getAvatarUrl())
                .role(dto.getRole())
                .affiliateCode(dto.getAffiliateCode())
                .isActive(dto.getIsActive())
                .build();
    }
}
