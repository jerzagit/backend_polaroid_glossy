package com.polaroid.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderRequest {
    @NotBlank(message = "Customer name is required")
    private String customerName;
    
    @NotBlank(message = "Customer email is required")
    private String customerEmail;
    
    private String customerPhone;
    private String customerState;
    private String affiliateCode;
    
    @NotNull(message = "Items are required")
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        @NotBlank(message = "Size ID is required")
        private String sizeId;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        private List<String> imageUrls;
        private List<String> customTexts;
    }
}
