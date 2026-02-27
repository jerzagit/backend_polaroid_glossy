package com.polaroid.dto.request;

import com.polaroid.model.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleUpdateRequest {
    @NotNull(message = "Role is required")
    private Role role;
}
