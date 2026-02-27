package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrintSizeRequest {
    @NotBlank(message = "ID is required")
    private String id;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Display name is required")
    private String displayName;
    
    @NotNull(message = "Width is required")
    @Positive(message = "Width must be positive")
    private BigDecimal width;
    
    @NotNull(message = "Height is required")
    @Positive(message = "Height must be positive")
    private BigDecimal height;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private String description;
    private Boolean isActive = true;
}
