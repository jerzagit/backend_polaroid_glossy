package com.polaroid.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private String id;
    private String sizeId;
    private String sizeName;
    private Integer quantity;
    private Integer expectedImageCount;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private List<String> images;
    private List<String> customTexts;
}
