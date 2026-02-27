package com.polaroid.dto.response;

import com.polaroid.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String name;
    private String phone;
    private String avatarUrl;
    private Role role;
    private String affiliateCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
