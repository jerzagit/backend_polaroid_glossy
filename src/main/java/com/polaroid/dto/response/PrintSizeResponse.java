package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintSizeResponse {
    private String id;
    private String name;
    private String displayName;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal price;
    private String description;
    private Boolean isActive;
}
